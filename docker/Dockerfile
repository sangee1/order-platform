# syntax=docker/dockerfile:1
# One Dockerfile for every service: docker build --build-arg SERVICE=order-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${SERVICE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
RUN useradd --system --no-create-home app
USER app
COPY --from=build /src/${SERVICE}/target/${SERVICE}.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
