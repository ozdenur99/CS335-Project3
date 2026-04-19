# Stage 1 — build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY --chmod=0755 mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src ./src
RUN ./mvnw package -Dmaven.test.skip=true -q

# Stage 2 — run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENV TZ=Europe/Dublin
ENTRYPOINT ["java", "-jar", "app.jar"]