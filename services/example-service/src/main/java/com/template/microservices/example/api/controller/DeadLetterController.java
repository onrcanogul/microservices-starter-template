package com.template.microservices.example.api.controller;

import com.template.core.response.ApiResponse;
import com.template.starter.inbox.entity.Inbox;
import com.template.starter.inbox.repository.InboxRepository;
import com.template.starter.inbox.service.InboxService;
import com.template.starter.outbox.entity.Outbox;
import com.template.starter.outbox.repository.OutboxRepository;
import com.template.starter.outbox.service.OutboxService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Ops endpoint for the processing-layer dead-letter queue: list dead (poison) inbox/outbox rows and
 * replay them. Distinct from the Kafka transport DLT (which lives in Kafka topics). Admin-only.
 */
@RestController
@RequestMapping("/api/admin/dead")
@PreAuthorize("hasRole('ADMIN')")
public class DeadLetterController {

    private final InboxRepository inboxRepository;
    private final OutboxRepository outboxRepository;
    private final InboxService inboxService;
    private final OutboxService outboxService;

    public DeadLetterController(InboxRepository inboxRepository,
                                OutboxRepository outboxRepository,
                                InboxService inboxService,
                                OutboxService outboxService) {
        this.inboxRepository = inboxRepository;
        this.outboxRepository = outboxRepository;
        this.inboxService = inboxService;
        this.outboxService = outboxService;
    }

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<Inbox>>> deadInbox() {
        return ResponseEntity.ok(ApiResponse.ok(inboxRepository.findByDeadTrue()));
    }

    @GetMapping("/outbox")
    public ResponseEntity<ApiResponse<List<Outbox>>> deadOutbox() {
        return ResponseEntity.ok(ApiResponse.ok(outboxRepository.findByDeadTrue()));
    }

    @PostMapping("/inbox/{token}/replay")
    public ResponseEntity<ApiResponse<Void>> replayInbox(@PathVariable("token") UUID token) {
        inboxService.replay(token);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null));
    }

    @PostMapping("/outbox/{id}/replay")
    public ResponseEntity<ApiResponse<Void>> replayOutbox(@PathVariable("id") UUID id) {
        outboxService.replay(id);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null));
    }
}
