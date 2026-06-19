---
name: docs-authoring
description: "Use when adding or updating documentation — a module README, an ADR, the glossary, the conventions, or any docs/ page. Covers the hybrid single-source model (co-located READMEs vs centralised docs/), the terse AI-optimized format, the ADR template, and the rule that each fact has exactly one home."
---

# Documentation authoring

Docs follow a **hybrid, single-source** model: each fact lives in exactly one place; everything else links to it.

## Where docs live

| Kind | Home | Holds |
|------|------|-------|
| Module doc | co-located `README.md` | what it does, key types, config |
| Invariants | `docs/constraints/` | enforced architectural rules |
| Pattern ADRs | `docs/patterns/` | design decisions (why this approach) |
| Concepts | `docs/concepts/` | distributed-systems theory mapped to the code |
| Operations | `docs/operations/` | running, ports, troubleshooting |
| Conventions | `docs/conventions.md` | coding conventions (single source) |
| Glossary | `docs/glossary.md` | term definitions |
| Index/map | `CLAUDE.md` | repo map, golden rules, knowledge-base index |

Tech stack + module map live in `CLAUDE.md` (and the README landing); never duplicate them elsewhere.

## Format: terse, AI-optimized

Tables and short declarative lines; exact type names and file paths; no narrative paragraphs. Module README <= ~25 lines; ADR <= ~30 lines. Prefer linking to the single source over restating it.

## Module README template (omit N/A sections)
```
# <artifactId>
<one line: capability + namespace + tier>
## Beans / key types      (table: Type | Role)
## Config (`acme.<ns>.*`)  (table: Property | Default | Meaning)
## Depends on             (internal modules only)
## See                    (docs/patterns/<x>.md, skill <name> — if any)
```

## ADR template (`docs/patterns/<name>.md`)
```
# <Pattern>
**Decision:** ...
**Why:** ...
**Alternatives rejected:** Option — why not
**Trade-offs:** ...
**Implementation:** modules + key types
```

## Rules

- **Single source:** changing a fact means editing one file. New doc file -> add it to the `CLAUDE.md` knowledge-base index and `docs/README.md`.
- **Enforced constraints change in two places:** the doc *and* its fitness test — see skill `architecture-fitness`.
- **Record findings, don't silently fix:** if docs disagree with code, document the discrepancy accurately and flag it.
- After moving/renaming docs, grep for stale links and fix them.
