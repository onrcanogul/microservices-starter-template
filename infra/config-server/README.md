# config-server

Spring Cloud Config Server, `native` profile (classpath, no Git). Per-service config resolved by application name. Package `com.template.config`, `@EnableConfigServer`.

## Config
- Profile `native`; `search-locations: classpath:/configs/{application}` (`{application}` = requester's `spring.application.name`).
- Per-service config under `src/main/resources/configs/<service>/application.yml`; env overrides as `<service>-<profile>.yml`. Present: `configs/api-gateway/`, `configs/example-service/`.
- Services opt in via `spring.config.import: optional:configserver:`.
- Registers with Eureka (`EUREKA_URI:http://localhost:8761/eureka`).
- Actuator: `health`, `info`.

## Run
Port `8888`. Start after discovery-service, before application services (they import config from `8888` on startup).
```bash
mvn -pl infra/config-server -am spring-boot:run
```
