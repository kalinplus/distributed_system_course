# Runtime stage - uses pre-built JAR
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY inventory-service/target/inventory-service-1.0.0.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
