// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title AidRegistry
 * @notice Non-upgradeable audit registry for AidBridge on Avalanche Fuji (C03 verification slice).
 *         Stores only opaque bytes32 references, sequence numbers, and cryptographic commitments.
 *         Enforces organization-scoped relayer authorization and deterministic duplicate/conflict detection.
 */
contract AidRegistry is AccessControl {
    bytes32 public constant RELAYER_ROLE = keccak256("RELAYER_ROLE");
    uint16 public constant SCHEMA_VERSION_V1 = 1;

    struct OrganizationRecord {
        bool registered;
        bool active;
        address relayer;
    }

    struct EventRecord {
        bool exists;
        bytes4 operation;
        bytes32 orgRef;
        bytes32 caseRef;
        uint64 chainSequence;
        bytes32 evidenceCommitment;
        uint16 schemaVersion;
        bytes32 payloadDigest;
    }

    struct CaseRecord {
        bytes32 orgRef;
        bool verified;
        uint256 openDisputeCount;
        uint64 lastChainSequence;
    }

    struct CommitmentRecord {
        bytes32 orgRef;
        bytes32 caseRef;
    }

    struct ReceiptRecord {
        bytes32 orgRef;
        bytes32 caseRef;
        bytes32 commitmentRef;
    }

    enum DeliveryStatus {
        None,
        Recorded
    }

    struct DeliveryRecord {
        bytes32 orgRef;
        bytes32 caseRef;
        DeliveryStatus status;
    }

    mapping(bytes32 => OrganizationRecord) private _organizations;
    mapping(address => uint256) private _activeOrgCountByRelayer;
    // Explicit global withdrawal survives all organization lifecycle operations.
    mapping(address => bool) private _relayerPermissionWithdrawn;
    mapping(bytes32 => EventRecord) private _events;
    mapping(bytes32 => CaseRecord) private _cases;
    mapping(bytes32 => CommitmentRecord) private _commitments;
    mapping(bytes32 => ReceiptRecord) private _receipts;
    mapping(bytes32 => DeliveryRecord) private _deliveries;

    error InvalidZeroInput();
    error OrganizationAlreadyRegistered(bytes32 orgRef);
    error OrganizationNotRegistered(bytes32 orgRef);
    error OrganizationNotActive(bytes32 orgRef);
    error UnauthorizedRelayer(bytes32 orgRef, address caller);
    error UnsupportedSchemaVersion(uint16 schemaVersion);
    error EventAlreadyRecorded(bytes32 eventId);
    error EventIdConflict(bytes32 eventId);
    error CaseOrganizationMismatch(bytes32 caseRef, bytes32 expectedOrgRef, bytes32 actualOrgRef);
    error CaseAlreadyVerified(bytes32 caseRef);
    error InvalidChainSequence(bytes32 caseRef, uint64 expectedSequence, uint64 actualSequence);
    error CaseNotVerified(bytes32 caseRef);
    error CaseHasOpenDispute(bytes32 caseRef);
    error RefAlreadyUsed(bytes32 ref);
    error RefNotFound(bytes32 ref);
    error RefOrganizationMismatch(bytes32 ref, bytes32 expectedOrgRef, bytes32 actualOrgRef);
    error RefCaseMismatch(bytes32 ref, bytes32 expectedCaseRef, bytes32 actualCaseRef);

    event OrganizationRegistered(
        bytes32 indexed orgRef,
        address indexed relayer,
        uint64 timestamp
    );

    event OrganizationRelayerUpdated(
        bytes32 indexed orgRef,
        address indexed previousRelayer,
        address indexed newRelayer,
        bool active,
        uint64 timestamp
    );

    event CaseVerified(
        bytes32 indexed orgRef,
        bytes32 indexed caseRef,
        bytes32 indexed eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion,
        uint64 timestamp
    );

    event AidCommitted(
        bytes32 indexed orgRef,
        bytes32 indexed caseRef,
        bytes32 indexed eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion,
        uint64 timestamp,
        bytes32 commitmentRef
    );

    event CommitmentReduced(
        bytes32 indexed orgRef,
        bytes32 indexed caseRef,
        bytes32 indexed eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion,
        uint64 timestamp,
        bytes32 commitmentRef
    );

    event GoodsReceived(
        bytes32 indexed orgRef,
        bytes32 indexed caseRef,
        bytes32 indexed eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion,
        uint64 timestamp,
        bytes32 receiptRef,
        bytes32 commitmentRef
    );

    event DeliveryRecorded(
        bytes32 indexed orgRef,
        bytes32 indexed caseRef,
        bytes32 indexed eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion,
        uint64 timestamp,
        bytes32 deliveryRef
    );

    constructor(address initialAdmin) {
        if (initialAdmin == address(0)) {
            revert InvalidZeroInput();
        }
        _grantRole(DEFAULT_ADMIN_ROLE, initialAdmin);
    }

    function grantRole(bytes32 role, address account) public override {
        // AccessControl checks the role admin even when the role already exists.
        super.grantRole(role, account);
        if (role == RELAYER_ROLE) {
            _relayerPermissionWithdrawn[account] = false;
        }
    }

    function revokeRole(bytes32 role, address account) public override {
        super.revokeRole(role, account);
        if (role == RELAYER_ROLE) {
            // Record intent even if lifecycle cleanup already removed the role.
            _relayerPermissionWithdrawn[account] = true;
        }
    }

    function renounceRole(bytes32 role, address callerConfirmation) public override {
        super.renounceRole(role, callerConfirmation);
        if (role == RELAYER_ROLE) {
            _relayerPermissionWithdrawn[callerConfirmation] = true;
        }
    }

    function registerOrganization(
        bytes32 orgRef,
        address relayer
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (orgRef == bytes32(0) || relayer == address(0)) {
            revert InvalidZeroInput();
        }
        if (_organizations[orgRef].registered) {
            revert OrganizationAlreadyRegistered(orgRef);
        }

        _organizations[orgRef] = OrganizationRecord({
            registered: true,
            active: true,
            relayer: relayer
        });

        _incrementActiveRelayer(relayer);

        emit OrganizationRegistered(orgRef, relayer, uint64(block.timestamp));
    }

    function updateOrganizationRelayer(
        bytes32 orgRef,
        address newRelayer
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (orgRef == bytes32(0) || newRelayer == address(0)) {
            revert InvalidZeroInput();
        }
        OrganizationRecord storage org = _organizations[orgRef];
        if (!org.registered) {
            revert OrganizationNotRegistered(orgRef);
        }

        address previousRelayer = org.relayer;
        if (org.active && previousRelayer != address(0)) {
            _decrementActiveRelayer(previousRelayer);
        }

        org.relayer = newRelayer;
        if (org.active) {
            _incrementActiveRelayer(newRelayer);
        }

        emit OrganizationRelayerUpdated(
            orgRef,
            previousRelayer,
            newRelayer,
            org.active,
            uint64(block.timestamp)
        );
    }

    function revokeOrganizationRelayer(
        bytes32 orgRef
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (orgRef == bytes32(0)) {
            revert InvalidZeroInput();
        }
        OrganizationRecord storage org = _organizations[orgRef];
        if (!org.registered) {
            revert OrganizationNotRegistered(orgRef);
        }

        address previousRelayer = org.relayer;
        if (org.active && previousRelayer != address(0)) {
            _decrementActiveRelayer(previousRelayer);
        }

        org.relayer = address(0);

        emit OrganizationRelayerUpdated(
            orgRef,
            previousRelayer,
            address(0),
            org.active,
            uint64(block.timestamp)
        );
    }

    function setOrganizationStatus(
        bytes32 orgRef,
        bool active
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (orgRef == bytes32(0)) {
            revert InvalidZeroInput();
        }
        OrganizationRecord storage org = _organizations[orgRef];
        if (!org.registered) {
            revert OrganizationNotRegistered(orgRef);
        }

        if (org.active != active) {
            org.active = active;
            if (org.relayer != address(0)) {
                if (active) {
                    _incrementActiveRelayer(org.relayer);
                } else {
                    _decrementActiveRelayer(org.relayer);
                }
            }
        }

        emit OrganizationRelayerUpdated(
            orgRef,
            org.relayer,
            org.relayer,
            org.active,
            uint64(block.timestamp)
        );
    }

    function recordCaseVerification(
        bytes32 orgRef,
        bytes32 caseRef,
        bytes32 eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion
    ) external {
        // 1. Organization & caller authorization first (fail-closed before event/case lookup)
        OrganizationRecord storage org = _organizations[orgRef];
        if (!org.registered || !org.active) {
            revert OrganizationNotActive(orgRef);
        }
        if (!hasRole(RELAYER_ROLE, msg.sender) || org.relayer != msg.sender) {
            revert UnauthorizedRelayer(orgRef, msg.sender);
        }

        // 2. Structural & input validation
        if (
            orgRef == bytes32(0) ||
            caseRef == bytes32(0) ||
            eventId == bytes32(0) ||
            evidenceCommitment == bytes32(0) ||
            chainSequence == 0
        ) {
            revert InvalidZeroInput();
        }
        if (schemaVersion != SCHEMA_VERSION_V1) {
            revert UnsupportedSchemaVersion(schemaVersion);
        }

        // 3. Duplicate eventId check (evaluated before CaseAlreadyVerified / sequence transition checks)
        bytes4 operation = this.recordCaseVerification.selector;
        bytes32 payloadDigest = keccak256(
            abi.encode(
                operation,
                orgRef,
                caseRef,
                chainSequence,
                evidenceCommitment,
                schemaVersion
            )
        );

        EventRecord storage existingEvent = _events[eventId];
        if (existingEvent.exists) {
            if (existingEvent.payloadDigest == payloadDigest) {
                revert EventAlreadyRecorded(eventId);
            }
            revert EventIdConflict(eventId);
        }

        // 4. Case ownership, verification state, and chainSequence progression
        CaseRecord storage caseRecord = _cases[caseRef];
        if (caseRecord.orgRef != bytes32(0) && caseRecord.orgRef != orgRef) {
            revert CaseOrganizationMismatch(caseRef, caseRecord.orgRef, orgRef);
        }
        if (caseRecord.verified) {
            revert CaseAlreadyVerified(caseRef);
        }
        uint64 expectedSequence = caseRecord.lastChainSequence + 1;
        if (chainSequence != expectedSequence) {
            revert InvalidChainSequence(caseRef, expectedSequence, chainSequence);
        }

        // 5. State write & event emission
        _events[eventId] = EventRecord({
            exists: true,
            operation: operation,
            orgRef: orgRef,
            caseRef: caseRef,
            chainSequence: chainSequence,
            evidenceCommitment: evidenceCommitment,
            schemaVersion: schemaVersion,
            payloadDigest: payloadDigest
        });

        caseRecord.orgRef = orgRef;
        caseRecord.verified = true;
        caseRecord.openDisputeCount = 0;
        caseRecord.lastChainSequence = chainSequence;

        emit CaseVerified(
            orgRef,
            caseRef,
            eventId,
            chainSequence,
            evidenceCommitment,
            schemaVersion,
            uint64(block.timestamp)
        );
    }

    function recordAidCommitment(
        bytes32 orgRef,
        bytes32 caseRef,
        bytes32 eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion,
        bytes32 commitmentRef
    ) external {
        // 1. Organization & caller authorization first (fail-closed before event/case/ref lookup)
        _requireAuthorizedRelayer(orgRef);

        // 2. Structural & input validation (envelope + operation-specific ref)
        _requireValidEnvelope(orgRef, caseRef, eventId, chainSequence, evidenceCommitment, schemaVersion);
        if (commitmentRef == bytes32(0)) {
            revert InvalidZeroInput();
        }

        // 3. Duplicate eventId check; the digest binds the operation selector and commitmentRef
        bytes32 payloadDigest = keccak256(
            abi.encode(
                this.recordAidCommitment.selector,
                orgRef,
                caseRef,
                chainSequence,
                evidenceCommitment,
                schemaVersion,
                commitmentRef
            )
        );
        _requireNewEvent(eventId, payloadDigest);

        // 4. Case must be verified, owned by orgRef, without open dispute; commitmentRef must be new
        CaseRecord storage caseRecord = _requireVerifiedCase(orgRef, caseRef, true);
        if (_commitments[commitmentRef].orgRef != bytes32(0)) {
            revert RefAlreadyUsed(commitmentRef);
        }

        // 5. chainSequence progression
        _requireNextSequence(caseRecord, caseRef, chainSequence);

        // 6. State write & event emission
        _events[eventId] = EventRecord({
            exists: true,
            operation: this.recordAidCommitment.selector,
            orgRef: orgRef,
            caseRef: caseRef,
            chainSequence: chainSequence,
            evidenceCommitment: evidenceCommitment,
            schemaVersion: schemaVersion,
            payloadDigest: payloadDigest
        });
        _commitments[commitmentRef] = CommitmentRecord({orgRef: orgRef, caseRef: caseRef});
        caseRecord.lastChainSequence = chainSequence;

        emit AidCommitted(
            orgRef,
            caseRef,
            eventId,
            chainSequence,
            evidenceCommitment,
            schemaVersion,
            uint64(block.timestamp),
            commitmentRef
        );
    }

    function recordCommitmentReduction(
        bytes32 orgRef,
        bytes32 caseRef,
        bytes32 eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion,
        bytes32 commitmentRef
    ) external {
        // 1. Organization & caller authorization first (fail-closed before event/case/ref lookup)
        _requireAuthorizedRelayer(orgRef);

        // 2. Structural & input validation (envelope + operation-specific ref)
        _requireValidEnvelope(orgRef, caseRef, eventId, chainSequence, evidenceCommitment, schemaVersion);
        if (commitmentRef == bytes32(0)) {
            revert InvalidZeroInput();
        }

        // 3. Duplicate eventId check; the digest binds the operation selector and commitmentRef
        bytes32 payloadDigest = keccak256(
            abi.encode(
                this.recordCommitmentReduction.selector,
                orgRef,
                caseRef,
                chainSequence,
                evidenceCommitment,
                schemaVersion,
                commitmentRef
            )
        );
        _requireNewEvent(eventId, payloadDigest);

        // 4. Case verified and owned by orgRef (open dispute allowed); commitment must belong to this org/case.
        //    No quantity math on-chain: outstanding/cancelled quantities are enforced in SQL.
        CaseRecord storage caseRecord = _requireVerifiedCase(orgRef, caseRef, false);
        _requireCommitmentOfCase(commitmentRef, orgRef, caseRef);

        // 5. chainSequence progression
        _requireNextSequence(caseRecord, caseRef, chainSequence);

        // 6. State write & event emission (the original CommitmentRecord is never modified)
        _events[eventId] = EventRecord({
            exists: true,
            operation: this.recordCommitmentReduction.selector,
            orgRef: orgRef,
            caseRef: caseRef,
            chainSequence: chainSequence,
            evidenceCommitment: evidenceCommitment,
            schemaVersion: schemaVersion,
            payloadDigest: payloadDigest
        });
        caseRecord.lastChainSequence = chainSequence;

        emit CommitmentReduced(
            orgRef,
            caseRef,
            eventId,
            chainSequence,
            evidenceCommitment,
            schemaVersion,
            uint64(block.timestamp),
            commitmentRef
        );
    }

    function recordGoodsReceipt(
        bytes32 orgRef,
        bytes32 caseRef,
        bytes32 eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion,
        bytes32 receiptRef,
        bytes32 commitmentRef
    ) external {
        // 1. Organization & caller authorization first (fail-closed before event/case/ref lookup)
        _requireAuthorizedRelayer(orgRef);

        // 2. Structural & input validation (envelope + both operation-specific refs)
        _requireValidEnvelope(orgRef, caseRef, eventId, chainSequence, evidenceCommitment, schemaVersion);
        if (receiptRef == bytes32(0) || commitmentRef == bytes32(0)) {
            revert InvalidZeroInput();
        }

        // 3. Duplicate eventId check; the digest binds the operation selector, receiptRef and commitmentRef
        bytes32 payloadDigest = keccak256(
            abi.encode(
                this.recordGoodsReceipt.selector,
                orgRef,
                caseRef,
                chainSequence,
                evidenceCommitment,
                schemaVersion,
                receiptRef,
                commitmentRef
            )
        );
        _requireNewEvent(eventId, payloadDigest);

        // 4. Case verified and owned by orgRef (open dispute allowed); receiptRef must be new;
        //    referenced commitment must belong to this org/case. Quantities are enforced in SQL.
        CaseRecord storage caseRecord = _requireVerifiedCase(orgRef, caseRef, false);
        if (_receipts[receiptRef].orgRef != bytes32(0)) {
            revert RefAlreadyUsed(receiptRef);
        }
        _requireCommitmentOfCase(commitmentRef, orgRef, caseRef);

        // 5. chainSequence progression
        _requireNextSequence(caseRecord, caseRef, chainSequence);

        // 6. State write & event emission
        _events[eventId] = EventRecord({
            exists: true,
            operation: this.recordGoodsReceipt.selector,
            orgRef: orgRef,
            caseRef: caseRef,
            chainSequence: chainSequence,
            evidenceCommitment: evidenceCommitment,
            schemaVersion: schemaVersion,
            payloadDigest: payloadDigest
        });
        _receipts[receiptRef] = ReceiptRecord({
            orgRef: orgRef,
            caseRef: caseRef,
            commitmentRef: commitmentRef
        });
        caseRecord.lastChainSequence = chainSequence;

        emit GoodsReceived(
            orgRef,
            caseRef,
            eventId,
            chainSequence,
            evidenceCommitment,
            schemaVersion,
            uint64(block.timestamp),
            receiptRef,
            commitmentRef
        );
    }

    function recordDelivery(
        bytes32 orgRef,
        bytes32 caseRef,
        bytes32 eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion,
        bytes32 deliveryRef
    ) external {
        // 1. Organization & caller authorization first (fail-closed before event/case/ref lookup)
        _requireAuthorizedRelayer(orgRef);

        // 2. Structural & input validation (envelope + operation-specific ref)
        _requireValidEnvelope(orgRef, caseRef, eventId, chainSequence, evidenceCommitment, schemaVersion);
        if (deliveryRef == bytes32(0)) {
            revert InvalidZeroInput();
        }

        // 3. Duplicate eventId check; the digest binds the operation selector and deliveryRef
        bytes32 payloadDigest = keccak256(
            abi.encode(
                this.recordDelivery.selector,
                orgRef,
                caseRef,
                chainSequence,
                evidenceCommitment,
                schemaVersion,
                deliveryRef
            )
        );
        _requireNewEvent(eventId, payloadDigest);

        // 4. Case must be verified, owned by orgRef, without open dispute; deliveryRef must be new.
        //    Delivered quantities and stock are enforced in SQL, not on-chain.
        CaseRecord storage caseRecord = _requireVerifiedCase(orgRef, caseRef, true);
        if (_deliveries[deliveryRef].orgRef != bytes32(0)) {
            revert RefAlreadyUsed(deliveryRef);
        }

        // 5. chainSequence progression
        _requireNextSequence(caseRecord, caseRef, chainSequence);

        // 6. State write & event emission
        _events[eventId] = EventRecord({
            exists: true,
            operation: this.recordDelivery.selector,
            orgRef: orgRef,
            caseRef: caseRef,
            chainSequence: chainSequence,
            evidenceCommitment: evidenceCommitment,
            schemaVersion: schemaVersion,
            payloadDigest: payloadDigest
        });
        _deliveries[deliveryRef] = DeliveryRecord({
            orgRef: orgRef,
            caseRef: caseRef,
            status: DeliveryStatus.Recorded
        });
        caseRecord.lastChainSequence = chainSequence;

        emit DeliveryRecorded(
            orgRef,
            caseRef,
            eventId,
            chainSequence,
            evidenceCommitment,
            schemaVersion,
            uint64(block.timestamp),
            deliveryRef
        );
    }

    function getOrganization(
        bytes32 orgRef
    ) external view returns (bool registered, bool active, address relayer) {
        OrganizationRecord storage org = _organizations[orgRef];
        return (org.registered, org.active, org.relayer);
    }

    function getEventRecord(
        bytes32 eventId
    )
        external
        view
        returns (
            bool exists,
            bytes4 operation,
            bytes32 orgRef,
            bytes32 caseRef,
            uint64 chainSequence,
            bytes32 evidenceCommitment,
            uint16 schemaVersion,
            bytes32 payloadDigest
        )
    {
        EventRecord storage record = _events[eventId];
        return (
            record.exists,
            record.operation,
            record.orgRef,
            record.caseRef,
            record.chainSequence,
            record.evidenceCommitment,
            record.schemaVersion,
            record.payloadDigest
        );
    }

    function getCaseSummary(
        bytes32 caseRef
    )
        external
        view
        returns (
            bool verified,
            uint256 openDisputeCount,
            uint64 lastChainSequence
        )
    {
        CaseRecord storage record = _cases[caseRef];
        return (
            record.verified,
            record.openDisputeCount,
            record.lastChainSequence
        );
    }

    function _incrementActiveRelayer(address relayer) private {
        uint256 currentCount = _activeOrgCountByRelayer[relayer];
        _activeOrgCountByRelayer[relayer] = currentCount + 1;
        if (currentCount == 0 && !_relayerPermissionWithdrawn[relayer]) {
            _grantRole(RELAYER_ROLE, relayer);
        }
    }

    function _decrementActiveRelayer(address relayer) private {
        uint256 currentCount = _activeOrgCountByRelayer[relayer];
        if (currentCount <= 1) {
            _activeOrgCountByRelayer[relayer] = 0;
            _revokeRole(RELAYER_ROLE, relayer);
        } else {
            _activeOrgCountByRelayer[relayer] = currentCount - 1;
        }
    }

    // ---- C06 milestone guard helpers (recordCaseVerification keeps its own C03 code) ----

    /// @dev Guard step 1: organization registered + active, caller is its relayer.
    function _requireAuthorizedRelayer(bytes32 orgRef) private view {
        OrganizationRecord storage org = _organizations[orgRef];
        if (!org.registered || !org.active) {
            revert OrganizationNotActive(orgRef);
        }
        if (!hasRole(RELAYER_ROLE, msg.sender) || org.relayer != msg.sender) {
            revert UnauthorizedRelayer(orgRef, msg.sender);
        }
    }

    /// @dev Guard step 2: non-zero envelope values and supported schemaVersion.
    function _requireValidEnvelope(
        bytes32 orgRef,
        bytes32 caseRef,
        bytes32 eventId,
        uint64 chainSequence,
        bytes32 evidenceCommitment,
        uint16 schemaVersion
    ) private pure {
        if (
            orgRef == bytes32(0) ||
            caseRef == bytes32(0) ||
            eventId == bytes32(0) ||
            evidenceCommitment == bytes32(0) ||
            chainSequence == 0
        ) {
            revert InvalidZeroInput();
        }
        if (schemaVersion != SCHEMA_VERSION_V1) {
            revert UnsupportedSchemaVersion(schemaVersion);
        }
    }

    /// @dev Guard step 3: same digest = idempotent replay, different digest = conflicting reuse.
    function _requireNewEvent(bytes32 eventId, bytes32 payloadDigest) private view {
        EventRecord storage existingEvent = _events[eventId];
        if (existingEvent.exists) {
            if (existingEvent.payloadDigest == payloadDigest) {
                revert EventAlreadyRecorded(eventId);
            }
            revert EventIdConflict(eventId);
        }
    }

    /// @dev Guard step 4 (case part): verified, owned by orgRef, optionally no open dispute.
    function _requireVerifiedCase(
        bytes32 orgRef,
        bytes32 caseRef,
        bool requireNoOpenDispute
    ) private view returns (CaseRecord storage caseRecord) {
        caseRecord = _cases[caseRef];
        if (!caseRecord.verified) {
            revert CaseNotVerified(caseRef);
        }
        if (caseRecord.orgRef != orgRef) {
            revert CaseOrganizationMismatch(caseRef, caseRecord.orgRef, orgRef);
        }
        if (requireNoOpenDispute && caseRecord.openDisputeCount != 0) {
            revert CaseHasOpenDispute(caseRef);
        }
    }

    /// @dev Guard step 5: chainSequence must be exactly lastChainSequence + 1.
    function _requireNextSequence(
        CaseRecord storage caseRecord,
        bytes32 caseRef,
        uint64 chainSequence
    ) private view {
        uint64 expectedSequence = caseRecord.lastChainSequence + 1;
        if (chainSequence != expectedSequence) {
            revert InvalidChainSequence(caseRef, expectedSequence, chainSequence);
        }
    }

    /// @dev Guard step 4 (ref part): commitment exists, then same org, then same case.
    ///      Org is checked before case so a cross-organization reference reports
    ///      RefOrganizationMismatch instead of being shadowed by RefCaseMismatch.
    function _requireCommitmentOfCase(
        bytes32 commitmentRef,
        bytes32 orgRef,
        bytes32 caseRef
    ) private view {
        CommitmentRecord storage commitment = _commitments[commitmentRef];
        if (commitment.orgRef == bytes32(0)) {
            revert RefNotFound(commitmentRef);
        }
        if (commitment.orgRef != orgRef) {
            revert RefOrganizationMismatch(commitmentRef, commitment.orgRef, orgRef);
        }
        if (commitment.caseRef != caseRef) {
            revert RefCaseMismatch(commitmentRef, commitment.caseRef, caseRef);
        }
    }
}