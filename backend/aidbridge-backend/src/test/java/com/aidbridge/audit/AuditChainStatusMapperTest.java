package com.aidbridge.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidbridge.audit.dto.response.BlockchainStatusResponse;
import com.aidbridge.audit.service.AuditChainStatusMapper;
import com.aidbridge.audit.service.AuditChainStatusMapper.ChainInput;
import org.junit.jupiter.api.Test;

/** F05 section 4 status table, one case per row. Pure unit test: no Spring, no database. */
class AuditChainStatusMapperTest {
    static final String HASH = "0x" + "ab".repeat(32);
    static final String CONTRACT = "0x015D941c679b0c402c8262BE237adAD36cEf6726";
    static final String LINK = "https://testnet.snowtrace.io/tx/" + HASH;

    final AuditChainStatusMapper mapper = new AuditChainStatusMapper("https://testnet.snowtrace.io/tx/");

    static ChainInput job(String jobStatus, String lastError, String txStatus, Long block, long txChain) {
        return new ChainInput(jobStatus, lastError, 43113L, txStatus == null ? null : HASH, txStatus, block,
                txStatus == null ? null : txChain, txStatus == null ? null : CONTRACT, true);
    }

    @Test
    void noJobIsNotApplicable() {
        var out = mapper.resolve(new ChainInput(null, null, null, null, null, null, null, null), false);
        assertThat(out).isEqualTo(new BlockchainStatusResponse("NOT_APPLICABLE", null, null, null, null, null, null, null));
    }

    @Test
    void pendingWithoutBroadcastShowsNoHash() {
        for (String status : new String[] {"PENDING", "PROCESSING"}) {
            var out = mapper.resolve(job(status, null, null, null, 43113L), false);
            assertThat(out.status()).isEqualTo("PENDING_ON_CHAIN");
            assertThat(out.txHash()).isNull();
            assertThat(out.explorerUrl()).isNull();
            assertThat(out.chainId()).isEqualTo(43113L);
            assertThat(out.network()).isEqualTo("Avalanche Fuji testnet");
        }
    }

    @Test
    void submittedShowsHashAndLinkButStaysPending() {
        var out = mapper.resolve(job("SUBMITTED", null, "SUBMITTED", null, 43113L), true);
        assertThat(out.status()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(out.txHash()).isEqualTo(HASH);
        assertThat(out.explorerUrl()).isEqualTo(LINK);
        assertThat(out.blockNumber()).isNull();
    }

    @Test
    void retryCodeOnSubmittedJobIsNotAFailure() {
        var out = mapper.resolve(job("SUBMITTED", "RECEIPT_PENDING_OPERATOR_ATTENTION", "SUBMITTED", null, 43113L), false);
        assertThat(out.status()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(out.errorCode()).isNull();
    }

    @Test
    void confirmedShowsHashBlockAndLink() {
        var out = mapper.resolve(job("CONFIRMED", null, "CONFIRMED", 1234L, 43113L), true);
        assertThat(out).isEqualTo(new BlockchainStatusResponse("CONFIRMED_ON_CHAIN", 43113L,
                "Avalanche Fuji testnet", CONTRACT, HASH, 1234L, LINK, null));
    }

    @Test
    void confirmedJobWithoutConfirmedAttemptIsNeverReportedConfirmed() {
        var out = mapper.resolve(job("CONFIRMED", null, "SUBMITTED", null, 43113L), false);
        assertThat(out.status()).isEqualTo("PENDING_ON_CHAIN");
        var none = mapper.resolve(job("CONFIRMED", null, null, null, 43113L), false);
        assertThat(none.status()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(none.txHash()).isNull();
    }

    @Test
    void revertedShowsHashAndLinkInBothViewsButCodeOnlyInternally() {
        var internal = mapper.resolve(job("FAILED", "TX_REVERTED", "REVERTED", 1235L, 43113L), false);
        assertThat(internal).isEqualTo(new BlockchainStatusResponse("FAILED_ON_CHAIN", 43113L,
                "Avalanche Fuji testnet", CONTRACT, HASH, 1235L, LINK, "TX_REVERTED"));
        var pub = mapper.resolve(job("FAILED", "TX_REVERTED", "REVERTED", 1235L, 43113L), true);
        assertThat(pub.txHash()).isEqualTo(HASH);
        assertThat(pub.explorerUrl()).isEqualTo(LINK);
        assertThat(pub.errorCode()).isNull();
    }

    @Test
    void receiptMismatchHidesHashPubliclyButNotInternally() {
        var internal = mapper.resolve(job("FAILED", "RECEIPT_WRONG_CONTRACT", "SUBMITTED", null, 43113L), false);
        assertThat(internal.status()).isEqualTo("FAILED_ON_CHAIN");
        assertThat(internal.txHash()).isEqualTo(HASH);
        assertThat(internal.explorerUrl()).isEqualTo(LINK);
        assertThat(internal.errorCode()).isEqualTo("RECEIPT_WRONG_CONTRACT");

        var pub = mapper.resolve(job("FAILED", "RECEIPT_WRONG_CONTRACT", "SUBMITTED", null, 43113L), true);
        assertThat(pub).isEqualTo(new BlockchainStatusResponse("FAILED_ON_CHAIN", 43113L,
                "Avalanche Fuji testnet", null, null, null, null, null));
    }

    @Test
    void failureBeforeBroadcastHasNoHash() {
        var out = mapper.resolve(job("FAILED", "MANIFEST_INVALID", null, null, 43113L), false);
        assertThat(out.status()).isEqualTo("FAILED_ON_CHAIN");
        assertThat(out.txHash()).isNull();
        assertThat(out.errorCode()).isEqualTo("MANIFEST_INVALID");
    }

    @Test
    void linkOnlyForFuji() {
        var out = mapper.resolve(job("CONFIRMED", null, "CONFIRMED", 7L, 31337L), false);
        assertThat(out.status()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(out.txHash()).isNull();
        assertThat(out.explorerUrl()).isNull();
    }

    @Test
    void malformedHashIsNeverShownOrLinked() {
        var in = new ChainInput("CONFIRMED", null, 43113L, "0x1234", "CONFIRMED", 9L, 43113L, CONTRACT);
        var out = mapper.resolve(in, false);
        assertThat(out.status()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(out.txHash()).isNull();
        assertThat(out.explorerUrl()).isNull();
    }

    @Test
    void explorerBaseUrlIsConfigurableButMustBeHttps() {
        var custom = new AuditChainStatusMapper("https://explorer.example.test/tx");
        assertThat(custom.resolve(job("SUBMITTED", null, "SUBMITTED", null, 43113L), false).explorerUrl())
                .isEqualTo("https://explorer.example.test/tx/" + HASH);
        assertThatThrownBy(() -> new AuditChainStatusMapper("http://testnet.snowtrace.io/tx/"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void missingOrWrongProofNeverConfirms() {
        for (ChainInput input : new ChainInput[] {
            new ChainInput("CONFIRMED",null,43113L,HASH,"CONFIRMED",1L,43113L,CONTRACT),
            new ChainInput("CONFIRMED",null,43113L,HASH,"CONFIRMED",null,43113L,CONTRACT,true),
            new ChainInput("CONFIRMED",null,43113L,HASH,"CONFIRMED",1L,31337L,CONTRACT,true),
            new ChainInput("CONFIRMED",null,43113L,HASH,"CONFIRMED",1L,43113L,null,true)
        }) assertThat(mapper.resolve(input,true).status()).isEqualTo("PENDING_ON_CHAIN");
    }
}
