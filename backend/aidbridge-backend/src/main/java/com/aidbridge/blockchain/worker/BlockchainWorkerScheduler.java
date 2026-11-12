package com.aidbridge.blockchain.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Periodic trigger; registered only when aidbridge.blockchain.enabled=true. Logs reason codes, never secrets. */
public class BlockchainWorkerScheduler {

    private static final Logger log = LoggerFactory.getLogger(BlockchainWorkerScheduler.class);

    private final CaseVerifiedWorker worker;

    public BlockchainWorkerScheduler(CaseVerifiedWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${aidbridge.blockchain.poll-interval-ms:5000}")
    public void tick() {
        try {
            worker.processNextJob().ifPresent(result ->
                    log.info("blockchain job {} -> {} {}", result.jobId(), result.outcome(),
                            result.reasonCode() == null ? "" : result.reasonCode()));
            worker.pollSubmitted().forEach(result ->
                    log.info("blockchain receipt {} -> {} {}", result.jobId(), result.outcome(),
                            result.reasonCode() == null ? "" : result.reasonCode()));
        } catch (RuntimeException e) {
            // Only our own reason codes are logged; library messages may contain RPC URLs with API keys.
            log.warn("blockchain worker run failed: {} {}", e.getClass().getSimpleName(), CaseVerifiedWorker.reasonCode(e, "UNCLASSIFIED"));
        }
    }
}
