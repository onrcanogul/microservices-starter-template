package com.template.starter.outbox.scheduler;

import com.template.starter.outbox.processor.OutboxProcessor;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxScheduler {
    private final OutboxProcessor processor;

    public OutboxScheduler(OutboxProcessor processor) { this.processor = processor; }

    @PostConstruct
    void init() {
        System.out.println("OutboxScheduler bean created");
    }
        @Scheduled(fixedRate = 5000)
    public void run() {
        processor.processAsync();
    }
}

