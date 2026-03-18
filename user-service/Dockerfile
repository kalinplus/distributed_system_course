# Runtime stage - uses pre-built JAR
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY user-service/target/user-service-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
