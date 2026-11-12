package com.aidbridge.blockchain.envelope;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Bytes4;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

/**
 * Off-chain hashing used before a milestone is sent on-chain (D49 C01-O03 and C01-O04).
 *
 * <ul>
 *   <li>{@code manifestDigest = SHA-256(canonical manifest UTF-8)}</li>
 *   <li>{@code evidenceCommitment = keccak256(abi.encode(domainV1, chainId, registryAddress, caseRef,
 *       eventId, manifestDigest, salt))}</li>
 *   <li>{@code payloadDigest = keccak256(abi.encode(operation, orgRef, caseRef, chainSequence,
 *       evidenceCommitment, schemaVersion))} &mdash; must equal {@code getEventRecord(eventId).payloadDigest}</li>
 * </ul>
 * Salt and manifest stay private (DB only); only the commitment goes on-chain.
 */
public final class EvidenceCommitments {

    public static final byte[] DOMAIN_V1 = Hash.sha3("AIDBRIDGE_EVIDENCE_V1".getBytes(StandardCharsets.UTF_8));

    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private EvidenceCommitments() {
    }

    public static byte[] manifestDigest(byte[] canonicalManifestUtf8) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonicalManifestUtf8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static byte[] evidenceCommitment(
            long chainId,
            String registryAddress,
            byte[] caseRef,
            byte[] eventId,
            byte[] manifestDigest,
            byte[] salt) {
        if (chainId <= 0) {
            throw new IllegalArgumentException("chainId must be positive");
        }
        if (registryAddress == null || !ADDRESS.matcher(registryAddress).matches()) {
            throw new IllegalArgumentException("registryAddress must be a 20-byte hex address");
        }
        String encoded = TypeEncoder.encode(new Bytes32(DOMAIN_V1))
                + TypeEncoder.encode(new Uint256(BigInteger.valueOf(chainId)))
                + TypeEncoder.encode(new Address(registryAddress))
                + TypeEncoder.encode(new Bytes32(requireNonZeroBytes32(caseRef, "caseRef")))
                + TypeEncoder.encode(new Bytes32(requireNonZeroBytes32(eventId, "eventId")))
                + TypeEncoder.encode(new Bytes32(requireNonZeroBytes32(manifestDigest, "manifestDigest")))
                + TypeEncoder.encode(new Bytes32(requireNonZeroBytes32(salt, "salt")));
        return Hash.sha3(Numeric.hexStringToByteArray(encoded));
    }

    public static byte[] payloadDigest(
            byte[] operationSelector,
            byte[] orgRef,
            byte[] caseRef,
            long chainSequence,
            byte[] evidenceCommitment,
            int schemaVersion) {
        if (operationSelector == null || operationSelector.length != 4) {
            throw new IllegalArgumentException("operation selector must be 4 bytes");
        }
        String encoded = TypeEncoder.encode(new Bytes4(operationSelector))
                + TypeEncoder.encode(new Bytes32(requireNonZeroBytes32(orgRef, "orgRef")))
                + TypeEncoder.encode(new Bytes32(requireNonZeroBytes32(caseRef, "caseRef")))
                + TypeEncoder.encode(new Uint64(BigInteger.valueOf(requirePositive(chainSequence, "chainSequence"))))
                + TypeEncoder.encode(new Bytes32(requireNonZeroBytes32(evidenceCommitment, "evidenceCommitment")))
                + TypeEncoder.encode(new Uint16(BigInteger.valueOf(requirePositive(schemaVersion, "schemaVersion"))));
        return Hash.sha3(Numeric.hexStringToByteArray(encoded));
    }

    static byte[] requireNonZeroBytes32(byte[] value, String name) {
        if (value == null || value.length != 32) {
            throw new IllegalArgumentException(name + " must be exactly 32 bytes");
        }
        int acc = 0;
        for (byte b : value) {
            acc |= b;
        }
        if (acc == 0) {
            throw new IllegalArgumentException(name + " must not be zero");
        }
        return value;
    }

    static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
