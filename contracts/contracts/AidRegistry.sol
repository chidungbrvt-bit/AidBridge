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

    mapping(bytes32 => OrganizationRecord) private _organizations;
    mapping(address => uint256) private _activeOrgCountByRelayer;
    // Explicit global withdrawal survives all organization lifecycle operations.
    mapping(address => bool) private _relayerPermissionWithdrawn;
    mapping(bytes32 => EventRecord) private _events;
    mapping(bytes32 => CaseRecord) private _cases;

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
}