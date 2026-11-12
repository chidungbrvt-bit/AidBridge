package com.aidbridge.blockchain.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidbridge.blockchain.adapter.ChainReceipt;
import com.aidbridge.blockchain.envelope.AidRegistryAbi;
import com.aidbridge.blockchain.envelope.CaseVerifiedEnvelope;
import com.aidbridge.blockchain.worker.CaseVerifiedReceiptVerifier.Confirmed;
import com.aidbridge.blockchain.worker.CaseVerifiedReceiptVerifier.Expected;
import com.aidbridge.blockchain.worker.CaseVerifiedReceiptVerifier.Mismatch;
import com.aidbridge.blockchain.worker.CaseVerifiedReceiptVerifier.Reverted;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.utils.Numeric;

/** C01-T14 (IT13, BR10-N): every wrong context field keeps the job unconfirmed; only the exact log confirms. */
class CaseVerifiedReceiptVerifierTest {

    private static final String REGISTRY = "0x015d941c679b0c402c8262be237adad36cef6726";
    private static final String SIGNER = "0x559994839f19f40ac704918c3ff1a913ac02dc01";
    private static final String OTHER = "0x" + "99".repeat(20);
    private static final String TX = "0x" + "aa".repeat(32);
    private static final String BLOCK_HASH = "0x" + "bb".repeat(32);

    private static byte[] b32(String byteHex) {
        return Numeric.hexStringToByteArray("0x" + byteHex.repeat(32));
    }

    private static final CaseVerifiedEnvelope ENVELOPE =
            new CaseVerifiedEnvelope(b32("10"), b32("20"), b32("30"), 1, b32("40"), 1);
    private static final Expected EXPECTED = new Expected(TX, REGISTRY, SIGNER, ENVELOPE);

    private static String data(long sequence, byte[] commitment, int schema, long timestamp) {
        return "0x" + TypeEncoder.encode(new Uint64(BigInteger.valueOf(sequence)))
                + TypeEncoder.encode(new Bytes32(commitment))
                + TypeEncoder.encode(new Uint16(BigInteger.valueOf(schema)))
                + TypeEncoder.encode(new Uint64(BigInteger.valueOf(timestamp)));
    }

    private static ChainReceipt.ChainLog goodLog() {
        return new ChainReceipt.ChainLog(REGISTRY,
                List.of(AidRegistryAbi.CASE_VERIFIED_TOPIC0, Numeric.toHexString(b32("10")),
                        Numeric.toHexString(b32("20")), Numeric.toHexString(b32("30"))),
                data(1, b32("40"), 1, 1_762_650_000L), BigInteger.ZERO, false);
    }

    private static ChainReceipt receipt(int status, String to, String from, List<ChainReceipt.ChainLog> logs) {
        return new ChainReceipt(TX, status, BigInteger.valueOf(60_893_112L), BLOCK_HASH, from, to, logs);
    }

    private static ChainReceipt good() {
        return receipt(1, REGISTRY, SIGNER, List.of(goodLog()));
    }

    private static CaseVerifiedReceiptVerifier.Verdict verify(ChainReceipt receipt) {
        return CaseVerifiedReceiptVerifier.verify(EXPECTED, receipt, Optional.of(BLOCK_HASH));
    }

    private static ChainReceipt withLog(UnaryOperator<ChainReceipt.ChainLog> change) {
        return receipt(1, REGISTRY, SIGNER, List.of(change.apply(goodLog())));
    }

    private static ChainReceipt.ChainLog topics(int index, String value) {
        List<String> topics = new ArrayList<>(goodLog().topics());
        topics.set(index, value);
        return new ChainReceipt.ChainLog(REGISTRY, topics, goodLog().data(), BigInteger.ZERO, false);
    }

    @Test
    void exactReceiptConfirmsWithBlockAndTimestamp() {
        assertThat(verify(good())).isEqualTo(new Confirmed(60_893_112L, BLOCK_HASH, 0, 1_762_650_000L));
    }

    @Test
    void nullOrRevertedReceiptIsNeverConfirmed() {
        assertThat(CaseVerifiedReceiptVerifier.verify(EXPECTED, null, Optional.of(BLOCK_HASH)))
                .isEqualTo(new Mismatch("RECEIPT_MISSING"));
        assertThat(verify(receipt(0, REGISTRY, SIGNER, List.of()))).isInstanceOf(Reverted.class);
    }

    @Test
    void wrongTransactionBlockContractOrSignerIsRejected() {
        assertThat(CaseVerifiedReceiptVerifier.verify(new Expected("0x" + "ab".repeat(32), REGISTRY, SIGNER, ENVELOPE),
                good(), Optional.of(BLOCK_HASH))).isEqualTo(new Mismatch("RECEIPT_TX_HASH_MISMATCH"));
        Mismatch reorg = (Mismatch) CaseVerifiedReceiptVerifier.verify(EXPECTED, good(), Optional.of("0x" + "cc".repeat(32)));
        assertThat(reorg.reasonCode()).isEqualTo("BLOCK_NOT_CANONICAL");
        assertThat(reorg.transientCondition()).isTrue();
        assertThat(CaseVerifiedReceiptVerifier.verify(EXPECTED, good(), Optional.empty()))
                .isEqualTo(new Mismatch("BLOCK_NOT_CANONICAL"));
        assertThat(verify(receipt(1, OTHER, SIGNER, List.of(goodLog())))).isEqualTo(new Mismatch("RECEIPT_WRONG_CONTRACT"));
        assertThat(verify(receipt(1, REGISTRY, OTHER, List.of(goodLog())))).isEqualTo(new Mismatch("RECEIPT_WRONG_SIGNER"));
    }

    @Test
    void missingRemovedForeignOrDuplicatedEventIsRejected() {
        assertThat(verify(receipt(1, REGISTRY, SIGNER, List.of()))).isEqualTo(new Mismatch("RECEIPT_EVENT_MISSING"));
        ChainReceipt.ChainLog removed = new ChainReceipt.ChainLog(REGISTRY, goodLog().topics(), goodLog().data(), BigInteger.ZERO, true);
        assertThat(verify(receipt(1, REGISTRY, SIGNER, List.of(removed)))).isEqualTo(new Mismatch("RECEIPT_EVENT_MISSING"));
        ChainReceipt.ChainLog foreign = new ChainReceipt.ChainLog(OTHER, goodLog().topics(), goodLog().data(), BigInteger.ZERO, false);
        assertThat(verify(receipt(1, REGISTRY, SIGNER, List.of(foreign)))).isEqualTo(new Mismatch("RECEIPT_EVENT_MISSING"));
        assertThat(verify(withLog(log -> topics(0, "0x" + "ee".repeat(32))))).isEqualTo(new Mismatch("RECEIPT_EVENT_MISSING"));
        assertThat(verify(receipt(1, REGISTRY, SIGNER, List.of(goodLog(), goodLog())))).isEqualTo(new Mismatch("RECEIPT_EVENT_DUPLICATED"));
        // A log from another contract next to the real one is ignored.
        assertThat(verify(receipt(1, REGISTRY, SIGNER, List.of(foreign, goodLog())))).isInstanceOf(Confirmed.class);
    }

    @Test
    void wrongIndexedRefsAreRejected() {
        assertThat(verify(withLog(log -> topics(1, Numeric.toHexString(b32("11")))))).isEqualTo(new Mismatch("RECEIPT_ORG_REF_MISMATCH"));
        assertThat(verify(withLog(log -> topics(2, Numeric.toHexString(b32("21")))))).isEqualTo(new Mismatch("RECEIPT_CASE_REF_MISMATCH"));
        assertThat(verify(withLog(log -> topics(3, Numeric.toHexString(b32("31")))))).isEqualTo(new Mismatch("RECEIPT_EVENT_ID_MISMATCH"));
        ChainReceipt.ChainLog threeTopics = new ChainReceipt.ChainLog(REGISTRY, goodLog().topics().subList(0, 3), goodLog().data(), BigInteger.ZERO, false);
        assertThat(verify(receipt(1, REGISTRY, SIGNER, List.of(threeTopics)))).isEqualTo(new Mismatch("RECEIPT_TOPIC_COUNT"));
    }

    @Test
    void wrongDataWordsAreRejected() {
        assertThat(verify(withLog(log -> new ChainReceipt.ChainLog(REGISTRY, log.topics(), data(2, b32("40"), 1, 1), BigInteger.ZERO, false))))
                .isEqualTo(new Mismatch("RECEIPT_SEQUENCE_MISMATCH"));
        assertThat(verify(withLog(log -> new ChainReceipt.ChainLog(REGISTRY, log.topics(), data(1, b32("41"), 1, 1), BigInteger.ZERO, false))))
                .isEqualTo(new Mismatch("RECEIPT_COMMITMENT_MISMATCH"));
        assertThat(verify(withLog(log -> new ChainReceipt.ChainLog(REGISTRY, log.topics(), data(1, b32("40"), 2, 1), BigInteger.ZERO, false))))
                .isEqualTo(new Mismatch("RECEIPT_SCHEMA_MISMATCH"));
        assertThat(verify(withLog(log -> new ChainReceipt.ChainLog(REGISTRY, log.topics(), log.data() + "00", BigInteger.ZERO, false))))
                .isEqualTo(new Mismatch("RECEIPT_DATA_LENGTH"));
        String dirtyHighBytes = "0x01" + goodLog().data().substring(4);
        assertThat(verify(withLog(log -> new ChainReceipt.ChainLog(REGISTRY, log.topics(), dirtyHighBytes, BigInteger.ZERO, false))))
                .isEqualTo(new Mismatch("RECEIPT_DATA_MALFORMED"));
    }
}
