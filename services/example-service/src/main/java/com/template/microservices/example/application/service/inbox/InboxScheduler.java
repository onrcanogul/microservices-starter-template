package com.template.microservices.example.application.service.inbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class InboxScheduler {
    private final InboxProcessor inboxProcessor;

    public InboxScheduler(InboxProcessor inboxProcessor) {
        this.inboxProcessor = inboxProcessor;
    }

    @Scheduled(fixedRate = 5000)
    public void process() {
        inboxProcessor.process();
    }
}
