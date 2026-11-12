FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu AS builder
WORKDIR /backend

COPY mvnw .
COPY backend/.mvn .mvn
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:resolve-plugins dependency:resolve

COPY backend/src backend/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests

FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu
WORKDIR /backend

COPY --from=builder /backend/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]