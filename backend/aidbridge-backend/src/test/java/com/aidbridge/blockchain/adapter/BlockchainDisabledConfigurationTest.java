package com.aidbridge.blockchain.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidbridge.blockchain.worker.BlockchainWorkerScheduler;
import com.aidbridge.blockchain.worker.CaseVerifiedWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BlockchainDisabledConfigurationTest {
    @Test
    void absentOptInCreatesNoAdapterWorkerOrSchedulerWithoutAnyCredentials() {
        new ApplicationContextRunner().withUserConfiguration(BlockchainConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(BlockchainAdapter.class);
            assertThat(context).doesNotHaveBean(CaseVerifiedWorker.class);
            assertThat(context).doesNotHaveBean(BlockchainWorkerScheduler.class);
        });
    }

    @Test
    void explicitDisabledAlsoCreatesNoSigningOrSchedulingPath() {
        new ApplicationContextRunner().withUserConfiguration(BlockchainConfiguration.class)
                .withPropertyValues("aidbridge.blockchain.enabled=false").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(BlockchainAdapter.class);
                    assertThat(context).doesNotHaveBean(CaseVerifiedWorker.class);
                    assertThat(context).doesNotHaveBean(BlockchainWorkerScheduler.class);
                });
    }
}
