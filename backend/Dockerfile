FROM eclipse-temurin:21-jdk-alpine AS build
LABEL authors="ibrahimadiallo"
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw
COPY pom.xml .
RUN ./mvnw -q -e -B dependency:go-offline
COPY src src
RUN ./mvnw -q -e -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
