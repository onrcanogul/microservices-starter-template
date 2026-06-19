# architecture-tests

ArchUnit fitness functions enforcing the layering invariants in `docs/constraints/layering.md` and
the coding conventions in `docs/conventions.md`.

## Layering (`StarterLayeringFitnessTest`)
| Test | Invariant |
|------|-----------|
| `commonsStaysPure` | `commons` must not depend on starters, services or infra |
| `startersDoNotDependOnServicesOrInfra` | starters must not depend on services or infra |
| `featureStartersDoNotDependOnEachOther` | feature starters depend only on `commons` + foundation starters (`persistence`, `kafka`) |
| `startersAreFreeOfCycles` | no dependency cycles between starters |

Package groups (test-scope): commons `com.template.{core,messaging,web,test}`; foundation `com.template.{persistence,kafka}`; all starters add `com.template.starter..` + `com.template.observer..`; services `com.template.microservices..`; infra `com.template.{gateway,config,service.discovery}..`. Foundation allowlist is an explicit array in the test.

## Conventions (`CodingConventionFitnessTest`)
| Test | Convention |
|------|-----------|
| `noFieldInjection` | no field is annotated `@Autowired` (constructor injection only) |
| `noStandardStreams` | no access to `System.out` / `System.err` |
| `noJavaUtilLogging` | no use of `java.util.logging` (SLF4J only) |
| `eventsImplementEventContract` | non-interface `*Event` types implement `Event` |

## Run
`mvn -B test -pl architecture-tests -am`  (JDK 21)
