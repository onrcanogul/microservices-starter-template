# -------- BUILD STAGE --------
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Parent pom ve modülleri kopyala
COPY pom.xml .
COPY commons ./commons
COPY starters ./starters
COPY services ./services
COPY infra ./infra

# Sadece discovery-service build et
RUN mvn clean package -pl infra/discovery-service -am -DskipTests

# -------- RUN STAGE --------
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /app/infra/discovery-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
