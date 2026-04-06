# -------- BUILD STAGE --------
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY commons ./commons
COPY starters ./starters
COPY services ./services
COPY infra ./infra

RUN mvn clean package -pl infra/config-server -am -DskipTests

# -------- RUN STAGE --------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

COPY --from=build /app/infra/config-server/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
