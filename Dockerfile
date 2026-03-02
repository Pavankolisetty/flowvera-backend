FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY . .

# Give permission to mvnw
RUN chmod +x mvnw

# Build the application
RUN ./mvnw clean package -DskipTests

EXPOSE 8080

CMD ["sh", "-c", "java -jar target/*.jar"]