---
name: architecture-fitness
description: "Use when adding or changing an ArchUnit fitness function, enforcing an architectural rule or coding convention, or wiring a new invariant into CI. Covers the guiding-vs-enforcing two-layer model, the architecture-tests module, the foundation-starter allowlist, and how to add a rule while keeping docs in sync."
---

# Architecture fitness functions

Architectural rules live in **two layers** that must change together:

- **Guiding (soft):** prose in `docs/constraints/layering.md` (module layering) and `docs/conventions.md` (coding conventions). Read before writing code.
- **Enforcing (hard):** ArchUnit tests in `architecture-tests/` that fail the build with the offending dependency/class path.

Never add one without the other: a documented-only rule gets forgotten; a tested-only rule is opaque.

## Where things live

| File | Holds |
|------|-------|
| `architecture-tests/.../StarterLayeringFitnessTest.java` | module-layering rules |
| `architecture-tests/.../CodingConventionFitnessTest.java` | coding-convention rules |
| `docs/constraints/layering.md` | the layering invariants (guiding) |
| `docs/conventions.md` | the coding conventions + `Enforced by` column |

## Add a new fitness function

1. Write the rule. Import production classes only: `new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.template")`.
2. **Verify it passes against current code first** (grep / reason). If it fails, the code has a real violation — surface it and decide fix-code vs fix-rule; never commit a red rule silently.
3. Add it as a `@Test` (with `.because(...)`) in the matching test class, or a new `*FitnessTest` for a new concern.
4. Document the invariant: add a row to `docs/constraints/layering.md` or `docs/conventions.md`, and set its `Enforced by` cell to `ArchUnit (architecture-tests)`.
5. It runs automatically — the `Architecture Fitness CI` workflow runs `mvn -B test -pl architecture-tests -am`; Surefire picks up every `*Test` in the module. No workflow change needed.

## Conventions & gotchas

- Package groups: commons `com.template.{core,messaging,web,test}`; foundation starters `com.template.{persistence,kafka}`; feature starters `com.template.starter..` + `com.template.observer..`; services `com.template.microservices..`; infra `com.template.{gateway,config,service.discovery}..`.
- The **foundation-starter allowlist** is an explicit `String[]` in `StarterLayeringFitnessTest` — promoting a starter is a one-line edit there plus the table in `layering.md`.
- Do **not** use `GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION` — it also flags `@Value` fields (legitimate here). Target `@Autowired` specifically.
- `DO_NOT_INCLUDE_TESTS` is required so test fixtures (e.g. `*Event` records under `src/test`) don't trip rules.
- Feature->foundation deps are allowed via `.ignoreDependency(alwaysTrue(), resideInAnyPackage(FOUNDATION_STARTERS))` on the slice rule.

See also: skill `docs-authoring`, `docs/constraints/layering.md`, `architecture-tests/README.md`.
