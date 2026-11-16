FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu AS builder
WORKDIR /backend

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY engine/pom.xml engine/pom.xml
COPY api/pom.xml api/pom.xml
COPY engine/src engine/src
COPY api/src api/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode package -DskipTests

FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu
WORKDIR /backend

COPY --from=builder /backend/api/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
