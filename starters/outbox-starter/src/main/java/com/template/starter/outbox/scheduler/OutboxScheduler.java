package com.template.starter.outbox.scheduler;

import com.template.starter.outbox.processor.OutboxProcessor;
import com.template.starter.outbox.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class OutboxScheduler {
    private final OutboxProcessor processor;
    private final OutboxRepository repository;
    private final Duration retentionPeriod;

    public OutboxScheduler(OutboxProcessor processor,
                           OutboxRepository repository,
                           @Value("${acme.outbox.cleanup.retention-days:7}") int retentionDays) {
        this.processor = processor;
        this.repository = repository;
        this.retentionPeriod = Duration.ofDays(retentionDays);
    }

    @Scheduled(fixedRateString = "${acme.outbox.scheduler.rate:1500}")
    @SchedulerLock(name = "outbox_run", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    public void run() {
        processor.process();
    }

    @Scheduled(cron = "${acme.outbox.cleanup.cron:0 0 3 * * *}")
    @SchedulerLock(name = "outbox_cleanup", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retentionPeriod);
        int deleted = repository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            log.info("Outbox cleanup: deleted {} published records older than {} days", deleted, retentionPeriod.toDays());
        }
    }
}

