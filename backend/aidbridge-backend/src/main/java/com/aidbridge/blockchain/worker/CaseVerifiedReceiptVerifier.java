package com.aidbridge.blockchain.worker;

import com.aidbridge.blockchain.adapter.ChainReceipt;
import com.aidbridge.blockchain.envelope.AidRegistryAbi;
import com.aidbridge.blockchain.envelope.CaseVerifiedEnvelope;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.web3j.utils.Numeric;

/**
 * BR-10 / C01-T14: decides whether a receipt proves the expected CASE_VERIFIED write. status=1 alone is not
 * enough; the transaction, block, contract, signer, event signature, indexed refs and every data word must
 * match what the database prepared. Pure function: no I/O, no clock.
 */
public final class CaseVerifiedReceiptVerifier {

    private CaseVerifiedReceiptVerifier() {
    }

    public record Expected(String transactionHash, String registryAddress, String signerAddress,
            CaseVerifiedEnvelope envelope) {
    }

    public sealed interface Verdict permits Confirmed, Reverted, Mismatch {
    }

    public record Confirmed(long blockNumber, String blockHash, long logIndex, long blockTimestamp) implements Verdict {
    }

    public record Reverted(long blockNumber, String blockHash) implements Verdict {
    }

    /** {@code BLOCK_NOT_CANONICAL} is transient (wait and re-check); every other code is an integrity failure. */
    public record Mismatch(String reasonCode) implements Verdict {
        public boolean transientCondition() {
            return "BLOCK_NOT_CANONICAL".equals(reasonCode);
        }
    }

    public static Verdict verify(Expected expected, ChainReceipt receipt, Optional<String> canonicalBlockHash) {
        if (receipt == null) {
            return new Mismatch("RECEIPT_MISSING");
        }
        if (!same(receipt.transactionHash(), expected.transactionHash())) {
            return new Mismatch("RECEIPT_TX_HASH_MISMATCH");
        }
        if (receipt.blockNumber() == null || canonicalBlockHash.isEmpty()
                || !same(canonicalBlockHash.get(), receipt.blockHash())) {
            return new Mismatch("BLOCK_NOT_CANONICAL");
        }
        long blockNumber = receipt.blockNumber().longValueExact();
        if (!same(receipt.to(), expected.registryAddress())) {
            return new Mismatch("RECEIPT_WRONG_CONTRACT");
        }
        if (!same(receipt.from(), expected.signerAddress())) {
            return new Mismatch("RECEIPT_WRONG_SIGNER");
        }
        if (receipt.status() != 1) {
            return new Reverted(blockNumber, receipt.blockHash());
        }

        List<ChainReceipt.ChainLog> events = receipt.logs().stream()
                .filter(log -> !log.removed())
                .filter(log -> same(log.address(), expected.registryAddress()))
                .filter(log -> !log.topics().isEmpty() && same(log.topics().get(0), AidRegistryAbi.CASE_VERIFIED_TOPIC0))
                .toList();
        if (events.isEmpty()) {
            return new Mismatch("RECEIPT_EVENT_MISSING");
        }
        if (events.size() > 1) {
            return new Mismatch("RECEIPT_EVENT_DUPLICATED");
        }
        ChainReceipt.ChainLog log = events.get(0);
        CaseVerifiedEnvelope envelope = expected.envelope();
        if (log.topics().size() != 4) {
            return new Mismatch("RECEIPT_TOPIC_COUNT");
        }
        if (!sameBytes(log.topics().get(1), envelope.orgRef())) {
            return new Mismatch("RECEIPT_ORG_REF_MISMATCH");
        }
        if (!sameBytes(log.topics().get(2), envelope.caseRef())) {
            return new Mismatch("RECEIPT_CASE_REF_MISMATCH");
        }
        if (!sameBytes(log.topics().get(3), envelope.eventId())) {
            return new Mismatch("RECEIPT_EVENT_ID_MISMATCH");
        }

        byte[] data = Numeric.hexStringToByteArray(log.data() == null ? "0x" : log.data());
        if (data.length != AidRegistryAbi.CASE_VERIFIED_DATA_BYTES) {
            return new Mismatch("RECEIPT_DATA_LENGTH");
        }
        Optional<BigInteger> sequence = uintWord(data, 0, 8);
        Optional<BigInteger> schema = uintWord(data, 2, 2);
        Optional<BigInteger> timestamp = uintWord(data, 3, 8);
        if (sequence.isEmpty() || schema.isEmpty() || timestamp.isEmpty()) {
            return new Mismatch("RECEIPT_DATA_MALFORMED");
        }
        if (sequence.get().longValueExact() != envelope.chainSequence()) {
            return new Mismatch("RECEIPT_SEQUENCE_MISMATCH");
        }
        if (!Arrays.equals(Arrays.copyOfRange(data, 32, 64), envelope.evidenceCommitment())) {
            return new Mismatch("RECEIPT_COMMITMENT_MISMATCH");
        }
        if (schema.get().intValueExact() != envelope.schemaVersion()) {
            return new Mismatch("RECEIPT_SCHEMA_MISMATCH");
        }
        long logIndex = log.logIndex() == null ? -1 : log.logIndex().longValueExact();
        return new Confirmed(blockNumber, receipt.blockHash(), logIndex, timestamp.get().longValueExact());
    }

    /** ABI uintN word: the high (32 - byteWidth) bytes must be zero. */
    private static Optional<BigInteger> uintWord(byte[] data, int wordIndex, int byteWidth) {
        int start = wordIndex * 32;
        for (int i = start; i < start + 32 - byteWidth; i++) {
            if (data[i] != 0) {
                return Optional.empty();
            }
        }
        return Optional.of(new BigInteger(1, Arrays.copyOfRange(data, start + 32 - byteWidth, start + 32)));
    }

    private static boolean sameBytes(String hex, byte[] expected) {
        return hex != null && Arrays.equals(Numeric.hexStringToByteArray(hex), expected);
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
