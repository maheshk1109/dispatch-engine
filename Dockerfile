FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/dispatch-engine-1.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
