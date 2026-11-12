package com.aidbridge.blockchain.envelope;

import java.util.Arrays;

/**
 * Public on-chain envelope for CASE_VERIFIED (D48: exactly six fields, no PII).
 * Byte arrays are copied defensively; equality compares contents.
 */
public record CaseVerifiedEnvelope(
        byte[] orgRef,
        byte[] caseRef,
        byte[] eventId,
        long chainSequence,
        byte[] evidenceCommitment,
        int schemaVersion) {

    public static final int SCHEMA_VERSION_V1 = 1;

    public CaseVerifiedEnvelope {
        orgRef = EvidenceCommitments.requireNonZeroBytes32(orgRef, "orgRef").clone();
        caseRef = EvidenceCommitments.requireNonZeroBytes32(caseRef, "caseRef").clone();
        eventId = EvidenceCommitments.requireNonZeroBytes32(eventId, "eventId").clone();
        evidenceCommitment = EvidenceCommitments.requireNonZeroBytes32(evidenceCommitment, "evidenceCommitment").clone();
        EvidenceCommitments.requirePositive(chainSequence, "chainSequence");
        if (schemaVersion != SCHEMA_VERSION_V1) {
            throw new IllegalArgumentException("Unsupported schemaVersion " + schemaVersion);
        }
    }

    @Override
    public byte[] orgRef() {
        return orgRef.clone();
    }

    @Override
    public byte[] caseRef() {
        return caseRef.clone();
    }

    @Override
    public byte[] eventId() {
        return eventId.clone();
    }

    @Override
    public byte[] evidenceCommitment() {
        return evidenceCommitment.clone();
    }

    /** payloadDigest the contract stores for this envelope (operation = recordCaseVerification selector). */
    public byte[] payloadDigest() {
        return EvidenceCommitments.payloadDigest(
                AidRegistryAbi.RECORD_CASE_VERIFICATION_SELECTOR, orgRef, caseRef, chainSequence, evidenceCommitment,
                schemaVersion);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CaseVerifiedEnvelope that
                && chainSequence == that.chainSequence
                && schemaVersion == that.schemaVersion
                && Arrays.equals(orgRef, that.orgRef)
                && Arrays.equals(caseRef, that.caseRef)
                && Arrays.equals(eventId, that.eventId)
                && Arrays.equals(evidenceCommitment, that.evidenceCommitment);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(eventId);
    }

    @Override
    public String toString() {
        return "CaseVerifiedEnvelope[eventId=" + org.web3j.utils.Numeric.toHexString(eventId)
                + ", chainSequence=" + chainSequence + "]";
    }
}
