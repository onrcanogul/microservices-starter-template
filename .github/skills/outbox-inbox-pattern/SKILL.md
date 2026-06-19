---
name: outbox-inbox-pattern
description: "Use when implementing transactional outbox or idempotent inbox patterns, adding event publishing to a service transaction, creating InboxProcessor implementations, or adding outbox/inbox Flyway migrations. Covers the full lifecycle: OutboxService.save() in business TX, OutboxProcessor polling, EventClassResolver, InboxService dedup, and InboxProcessor extension."
---

> **Canonical content:** this skill now lives in [`.claude/skills/outbox-inbox-pattern/SKILL.md`](../../../.claude/skills/outbox-inbox-pattern/SKILL.md) (single source for both Claude Code and Copilot). Read that file for the full guidance.
