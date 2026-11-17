package com.aidbridge.blockchain.envelope;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

/**
 * Frozen D49 ABI facts for the AidRegistry CASE_VERIFIED milestone, computed from canonical signatures
 * (never hard-coded hashes) so a signature typo fails the golden-vector test.
 */
public final class AidRegistryAbi {

    public static final String RECORD_CASE_VERIFICATION_SIGNATURE =
            "recordCaseVerification(bytes32,bytes32,bytes32,uint64,bytes32,uint16)";
    public static final String CASE_VERIFIED_EVENT_SIGNATURE =
            "CaseVerified(bytes32,bytes32,bytes32,uint64,bytes32,uint16,uint64)";

    public static final byte[] RECORD_CASE_VERIFICATION_SELECTOR = selector(RECORD_CASE_VERIFICATION_SIGNATURE);
    public static final String CASE_VERIFIED_TOPIC0 = Hash.sha3String(CASE_VERIFIED_EVENT_SIGNATURE);

    /** Non-indexed CaseVerified data: uint64 chainSequence, bytes32 commitment, uint16 schemaVersion, uint64 timestamp. */
    public static final int CASE_VERIFIED_DATA_BYTES = 4 * 32;

    private AidRegistryAbi() {
    }

    public static byte[] selector(String canonicalSignature) {
        return Arrays.copyOf(Hash.sha3(canonicalSignature.getBytes(StandardCharsets.UTF_8)), 4);
    }

    /** Calldata (0x-prefixed hex) for recordCaseVerification with the six-field D48 common envelope. */
    @SuppressWarnings("rawtypes")
    public static String encodeRecordCaseVerification(CaseVerifiedEnvelope envelope) {
        List<Type> inputs = Arrays.<Type>asList(
                new Bytes32(envelope.orgRef()),
                new Bytes32(envelope.caseRef()),
                new Bytes32(envelope.eventId()),
                new Uint64(BigInteger.valueOf(envelope.chainSequence())),
                new Bytes32(envelope.evidenceCommitment()),
                new Uint16(BigInteger.valueOf(envelope.schemaVersion())));
        String calldata = FunctionEncoder.encode(new Function("recordCaseVerification", inputs, Collections.emptyList()));
        String expectedPrefix = Numeric.toHexString(RECORD_CASE_VERIFICATION_SELECTOR);
        if (!calldata.startsWith(expectedPrefix)) {
            throw new IllegalStateException("Encoded selector does not match the frozen D49 signature");
        }
        return calldata;
    }

    public enum Operation {
        CASE_VERIFIED("recordCaseVerification", "CaseVerified", 0),
        AID_PLEDGED("recordAidCommitment", "AidCommitted", 1),
        COMMITMENT_REDUCED("recordCommitmentReduction", "CommitmentReduced", 1),
        GOODS_RECEIVED("recordGoodsReceipt", "GoodsReceived", 2),
        DELIVERY_RECORDED("recordDelivery", "DeliveryRecorded", 1);

        private final String functionName;
        private final String eventName;
        private final int refCount;

        Operation(String functionName, String eventName, int refCount) {
            this.functionName = functionName;
            this.eventName = eventName;
            this.refCount = refCount;
        }

        public String functionSignature() {
            return functionName + "(bytes32,bytes32,bytes32,uint64,bytes32,uint16"
                    + ",bytes32".repeat(refCount) + ")";
        }

        public String topic0() {
            return Hash.sha3String(eventName + "(bytes32,bytes32,bytes32,uint64,bytes32,uint16,uint64"
                    + ",bytes32".repeat(refCount) + ")");
        }

        public int dataBytes() {
            return CASE_VERIFIED_DATA_BYTES + 32 * refCount;
        }

        public void validateRefs(List<Bytes32> refs) {
            if (refs.size() != refCount) {
                throw new IllegalArgumentException("INVALID_OPERATION_REF_COUNT");
            }
            for (Bytes32 ref : refs) {
                EvidenceCommitments.requireNonZeroBytes32(ref.getValue(), "operationRef");
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public static String encode(Operation operation, CaseVerifiedEnvelope envelope, List<Bytes32> refs) {
        operation.validateRefs(refs);
        if (operation == Operation.CASE_VERIFIED) {
            return encodeRecordCaseVerification(envelope);
        }

        List<Type> inputs = new java.util.ArrayList<>();
        inputs.add(new Bytes32(envelope.orgRef()));
        inputs.add(new Bytes32(envelope.caseRef()));
        inputs.add(new Bytes32(envelope.eventId()));
        inputs.add(new Uint64(BigInteger.valueOf(envelope.chainSequence())));
        inputs.add(new Bytes32(envelope.evidenceCommitment()));
        inputs.add(new Uint16(BigInteger.valueOf(envelope.schemaVersion())));
        inputs.addAll(refs);

        String calldata = FunctionEncoder.encode(
                new Function(operation.functionName, inputs, Collections.emptyList()));
        if (!calldata.startsWith(Numeric.toHexString(selector(operation.functionSignature())))) {
            throw new IllegalStateException("ABI_SELECTOR_MISMATCH");
        }
        return calldata;
    }
}
