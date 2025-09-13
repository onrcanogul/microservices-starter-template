package com.template.starter.inbox.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class InboxScheduler {
    private final InboxProcessor processor;

    public InboxScheduler(InboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedRate = 1500)
    public void process() {
        processor.process();
    }
}
