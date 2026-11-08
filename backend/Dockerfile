FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu AS builder
WORKDIR /backend

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:resolve-plugins dependency:resolve

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests

FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu
WORKDIR /backend

COPY --from=builder /backend/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]