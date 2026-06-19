# discovery-service

Netflix Eureka server; service registry enabling `lb://` routing. Package `com.template.service.discovery`, `@EnableEurekaServer`.

## Config
- Standalone: `register-with-eureka: false`, `fetch-registry: false`.
- `wait-time-in-ms-when-sync-empty: 0` — serves registry immediately.
- Actuator: `health`, `info`.

## Run
Port `8761` (dashboard/registry at `http://localhost:8761`). Start FIRST — all other services register against it on boot.
```bash
mvn -pl infra/discovery-service -am spring-boot:run
```
