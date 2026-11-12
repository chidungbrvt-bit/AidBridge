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
}
