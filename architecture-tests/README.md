# architecture-tests

ArchUnit fitness functions enforcing the layering invariants in `docs/constraints/layering.md`.

## Rules (`StarterLayeringFitnessTest`)
| Test | Invariant |
|------|-----------|
| `commonsStaysPure` | `commons` must not depend on starters, services or infra |
| `startersDoNotDependOnServicesOrInfra` | starters must not depend on services or infra |
| `featureStartersDoNotDependOnEachOther` | feature starters depend only on `commons` + foundation starters (`persistence`, `kafka`) |
| `startersAreFreeOfCycles` | no dependency cycles between starters |

Package groups (test-scope): commons `com.template.{core,messaging,web,test}`; foundation `com.template.{persistence,kafka}`; all starters add `com.template.starter..` + `com.template.observer..`; services `com.template.microservices..`; infra `com.template.{gateway,config,service.discovery}..`. Foundation allowlist is an explicit array in the test.

## Run
`mvn -B test -pl architecture-tests -am`  (JDK 21)
