# common-web

Shared Spring MVC error handling: maps exceptions to a uniform body (`ApiResponse` envelope or RFC 7807 `ProblemDetail`).

NOTE: The one commons module that ships Spring auto-config. `CommonWebAutoConfiguration` (`@AutoConfiguration`, registered in `META-INF/.../AutoConfiguration.imports`) wires `GlobalExceptionHandler` as `@ConditionalOnMissingBean`.

## Key types
| Type | Role |
|------|------|
| `GlobalExceptionHandler` | `@RestControllerAdvice` (HIGHEST_PRECEDENCE); maps `BusinessException`→code/status, JSR-303 validation→400, MVC errors, fallback→500; injects `traceId` from `TraceContext` |
| `WebErrorProperties` | `@ConfigurationProperties("acme.web.error")`: `format` (API_RESPONSE \| PROBLEM_DETAIL), `includeMessage`, `includeStackTrace` |
| `CommonWebAutoConfiguration` | `@AutoConfiguration` enabling props + registering the handler |

## Depends on
`common-core`
