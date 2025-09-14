FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY commons ./commons
COPY starters ./starters
COPY services ./services
COPY infra ./infra

RUN mvn clean package -pl services/example-service -am -DskipTests
RUN ls -lh /app/services/example-service/target

FROM eclipse-temurin:21-jdk
WORKDIR /app

COPY --from=build /app/services/example-service/target/*-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
