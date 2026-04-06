package com.template.starter.inbox.service;

import com.template.starter.inbox.repository.InboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class InboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(InboxScheduler.class);
    private final InboxProcessor processor;
    private final InboxRepository repository;
    private final Duration retentionPeriod;

    public InboxScheduler(InboxProcessor processor,
                          InboxRepository repository,
                          @Value("${acme.inbox.cleanup.retention-days:7}") int retentionDays) {
        this.processor = processor;
        this.repository = repository;
        this.retentionPeriod = Duration.ofDays(retentionDays);
    }

    @Scheduled(fixedRateString = "${acme.inbox.scheduler.rate:1500}")
    public void process() {
        processor.process();
    }

    @Scheduled(cron = "${acme.inbox.cleanup.cron:0 0 3 * * *}")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retentionPeriod);
        int deleted = repository.deleteProcessedBefore(cutoff);
        if (deleted > 0) {
            log.info("Inbox cleanup: deleted {} processed records older than {} days", deleted, retentionPeriod.toDays());
        }
    }
}
