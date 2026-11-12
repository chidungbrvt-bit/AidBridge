package com.aidbridge.blockchain.adapter;

import com.aidbridge.blockchain.crypto.JobSecretCipher;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.blockchain.repository.BlockchainTransactionRepository;
import com.aidbridge.blockchain.worker.BlockchainWorkerScheduler;
import com.aidbridge.blockchain.worker.CaseVerifiedManifestBuilder;
import com.aidbridge.blockchain.worker.CaseVerifiedWorker;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import tools.jackson.databind.ObjectMapper;

/**
 * Creates the adapter only when {@code aidbridge.blockchain.enabled=true}. Startup fails fast if the
 * configuration is invalid or the RPC node reports a different chainId than configured.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aidbridge.blockchain", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BlockchainProperties.class)
@EnableScheduling
public class BlockchainConfiguration {

    @Bean(destroyMethod = "shutdown")
    Web3j web3j(BlockchainProperties properties) {
        properties.validate();
        return Web3j.build(new HttpService(properties.rpcUrl()));
    }

    @Bean
    BlockchainAdapter blockchainAdapter(Web3j web3j, BlockchainProperties properties) {
        Web3jBlockchainAdapter adapter = new Web3jBlockchainAdapter(web3j, properties);
        long remoteChainId = adapter.readChainId();
        if (remoteChainId != properties.chainId()) {
            throw new IllegalStateException("INVALID_CHAIN_ID: RPC reports " + remoteChainId
                    + " but configuration expects " + properties.chainId());
        }
        return adapter;
    }

    @Bean
    CaseVerifiedManifestBuilder caseVerifiedManifestBuilder(ObjectMapper json) {
        return new CaseVerifiedManifestBuilder(json);
    }

    @Bean
    CaseVerifiedWorker caseVerifiedWorker(PlatformTransactionManager transactionManager, BlockchainJobRepository jobs,
            BlockchainTransactionRepository attempts, CaseVerifiedManifestBuilder manifests, BlockchainAdapter adapter,
            JobSecretCipher cipher, BlockchainProperties properties) {
        return new CaseVerifiedWorker(new TransactionTemplate(transactionManager), jobs, attempts, manifests, adapter,
                cipher, properties, Clock.systemUTC());
    }

    @Bean
    BlockchainWorkerScheduler blockchainWorkerScheduler(CaseVerifiedWorker worker) {
        return new BlockchainWorkerScheduler(worker);
    }

    @Bean
    JobSecretCipher jobSecretCipher(@Value("${aidbridge.blockchain.secret-key:}") String base64Key) {
        return JobSecretCipher.fromBase64(base64Key);
    }
}
