FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
