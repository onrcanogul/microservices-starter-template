package com.template.starter.outbox.scheduler;

import com.template.starter.outbox.processor.OutboxProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OutboxScheduler {
    private final OutboxProcessor processor;

    public OutboxScheduler(OutboxProcessor processor) { this.processor = processor; }

    @Scheduled(fixedRateString = "${acme.outbox.scheduler.rate:1500}")
    public void run() {
        processor.process();
    }
}

