# logging-starter

MDC propagation + sensitive-data masking for structured JSON logging. Namespace `acme.logging.*`. Feature tier.

## Beans / key types
| Type | Role |
|------|------|
| `LoggingAutoConfiguration` | registers `MdcFilter` (servlet only) |
| `MdcFilter` | `OncePerRequestFilter`, order `HIGHEST_PRECEDENCE+10`; MDC keys `userId`/`userEmail`/`correlationId`/`requestMethod`/`requestUri`; echoes correlation id to response, generates UUID if absent |
| `SensitiveFields` | shared case-insensitive sensitive-name set; replacement `***MASKED***` |
| `SensitiveDataMaskingConverter` | Logback converter, masks plaintext |
| `MaskingJsonGeneratorDecorator` | logstash JSON-encoder decorator, masks JSON values |
| `LoggingProperties` | bound config (nested `Mdc`) |

Masked names: password, passwd, secret, token, authorization, auth_token, access_token, refresh_token, credit_card(+camel/lower), ssn, api_key(+camel/lower), private_key(+camel).

## Config (`acme.logging.*`)
| Property | Default | Meaning |
|----------|---------|---------|
| `enabled` | `true` | activate starter |
| `mdc.enabled` | `true` | register `MdcFilter` |
| `mdc.user-id-header` | `X-User-Id` | source header for `userId` |
| `mdc.user-email-header` | `X-User-Email` | source header for `userEmail` |
| `mdc.correlation-id-header` | `X-Correlation-Id` | source/response header for `correlationId` |

## Depends on
none (logstash encoder optional, classpath-activated)

## See
`docs/patterns/structured-logging.md`
