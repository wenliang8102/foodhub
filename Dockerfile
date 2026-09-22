# Build a selected Spring Boot service together with its Maven reactor dependencies.
FROM eclipse-temurin:21-jdk-alpine AS build

ARG MODULE
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY foodhub-common foodhub-common
COPY foodhub-gateway foodhub-gateway
COPY foodhub-auth foodhub-auth
COPY foodhub-merchant foodhub-merchant
COPY foodhub-social foodhub-social
COPY foodhub-coupon foodhub-coupon
COPY foodhub-order foodhub-order

RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S foodhub && adduser -S foodhub -G foodhub
WORKDIR /app

ARG MODULE
COPY --from=build /workspace/${MODULE}/target/${MODULE}-*.jar app.jar

USER foodhub
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
