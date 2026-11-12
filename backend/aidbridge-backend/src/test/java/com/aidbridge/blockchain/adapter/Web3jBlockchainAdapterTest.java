package com.aidbridge.blockchain.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/** Offline checks: local signing, EIP-155 chainId binding, hash-before-broadcast and secret redaction. */
class Web3jBlockchainAdapterTest {

    // Public Hardhat development account #0. Documented test key, never funded on any real network.
    private static final String HARDHAT_KEY_0 = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String HARDHAT_ADDRESS_0 = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
    private static final String REGISTRY = "0x5fbdb2315678afecb367f032d93f642f64180aa3";

    private static BlockchainProperties localProperties(String key) {
        return new BlockchainProperties(true, "http://127.0.0.1:8545", 31337, REGISTRY, key, null, null);
    }

    // No RPC call is made by these tests; the client points at an unused local port.
    private static Web3j offlineClient() {
        return Web3j.build(new HttpService("http://127.0.0.1:1"));
    }

    @Test
    void signsLocallyWithChainIdAndComputesHashBeforeBroadcast() throws Exception {
        Web3jBlockchainAdapter adapter = new Web3jBlockchainAdapter(offlineClient(), localProperties(HARDHAT_KEY_0));
        String calldata = "0x1bd1fadf" + "11".repeat(32 * 6);

        SignedTransaction signed = adapter.signTransaction(REGISTRY, calldata, BigInteger.valueOf(7),
                BigInteger.valueOf(1_000_000_000L), BigInteger.valueOf(300_000));

        assertThat(adapter.relayerAddress()).isEqualTo(HARDHAT_ADDRESS_0);
        assertThat(signed.transactionHash())
                .isEqualTo(Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(signed.rawTransactionHex()))));
        SignedRawTransaction decoded = (SignedRawTransaction) TransactionDecoder.decode(signed.rawTransactionHex());
        assertThat(decoded.getFrom().toLowerCase()).isEqualTo(HARDHAT_ADDRESS_0);
        assertThat(decoded.getChainId()).isEqualTo(31337L);
        assertThat(decoded.getNonce()).isEqualTo(BigInteger.valueOf(7));
        assertThat(decoded.getTo().toLowerCase()).isEqualTo(REGISTRY);
        assertThat(decoded.getValue()).isEqualTo(BigInteger.ZERO);
        assertThat(Numeric.prependHexPrefix(decoded.getData())).isEqualTo(calldata);
    }

    @Test
    void signingIsDeterministicSoRetriesCanReuseTheSameHash() {
        Web3jBlockchainAdapter adapter = new Web3jBlockchainAdapter(offlineClient(), localProperties(HARDHAT_KEY_0));
        SignedTransaction first = adapter.signTransaction(REGISTRY, "0x1bd1fadf", BigInteger.ONE, BigInteger.TEN, BigInteger.valueOf(21_000));
        SignedTransaction second = adapter.signTransaction(REGISTRY, "0x1bd1fadf", BigInteger.ONE, BigInteger.TEN, BigInteger.valueOf(21_000));
        assertThat(second.rawTransactionHex()).isEqualTo(first.rawTransactionHex());
        assertThat(second.transactionHash()).isEqualTo(first.transactionHash());
    }

    @Test
    void propertiesNeverPrintTheRelayerKeyAndRejectInvalidSettings() {
        assertThat(localProperties(HARDHAT_KEY_0).toString()).doesNotContain(HARDHAT_KEY_0.substring(2, 20)).contains("<redacted>");

        assertThatThrownBy(() -> localProperties("not-a-key").validate()).hasMessageStartingWith("MISSING_OR_INVALID_SECRET");
        assertThatThrownBy(() -> new BlockchainProperties(true, "http://127.0.0.1:8545", 1, REGISTRY, HARDHAT_KEY_0, null, null).validate())
                .hasMessageStartingWith("INVALID_CHAIN_ID");
        assertThatThrownBy(() -> new BlockchainProperties(true, "http://fuji.example", 43113, REGISTRY, HARDHAT_KEY_0, null, null).validate())
                .hasMessageStartingWith("MISSING_RPC_URL");
        assertThatThrownBy(() -> new BlockchainProperties(true, "http://127.0.0.1:8545", 31337, "0x1234", HARDHAT_KEY_0, null, null).validate())
                .hasMessageStartingWith("INVALID_REGISTRY_ADDRESS");
    }
}
