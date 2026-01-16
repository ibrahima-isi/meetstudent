# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS build
LABEL authors="ibrahimadiallo"
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw
COPY pom.xml .
# Use cache mount to persist dependencies between builds
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -e -B dependency:go-offline
COPY src src
# Use cache mount to speed up the package phase
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -e -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]