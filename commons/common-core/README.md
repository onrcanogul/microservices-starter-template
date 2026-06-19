# common-core

Framework-neutral primitives: API responses, error codes, business exception, auditing markers, tracing, id/precondition utils. No Spring auto-config.

## Key types
| Type | Role |
|------|------|
| `ApiResponse<T>` | Response envelope; `ok(data)`, `error(...)`; nested `ApiResponse.ApiError(code,message,details,traceId)` |
| `PageResponse<T>` | Paged DTO; `of(content,page,size,totalElements)` |
| `ErrorCode` | Interface: `code()`, `httpStatus()` |
| `StandardErrorCodes` | Enum impl: VALIDATION_FAILED(400), NOT_FOUND(404), CONFLICT(409), UNAUTHORIZED(401), FORBIDDEN(403), RATE_LIMITED(429), INTERNAL_ERROR(500), SERVICE_UNAVAILABLE(503), TIMEOUT(504) |
| `BusinessException` | RuntimeException carrying `ErrorCode` + metadata; `of(...)` factories, `httpStatus()`, `code()` |
| `IInsertAuditing` / `IUpdateAuditing` / `ISoftDelete` | Entity auditing/soft-delete markers |
| `SoftDelete` | Helper: `markDeleted(e,by)`, `restore(e)`, `isActive(e)` |
| `TraceContext` | `traceId()`, `spanId()` → `Optional<String>` from MDC |
| `Ids` | `uuid()`, `uuidNoDash()` |
| `Preconditions` | `checkNotNull(ref,msg)`, `checkArgument(expr,msg)` |

## Depends on
none (pure base)
