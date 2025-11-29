FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy everything (including .mvn and mvnw)
COPY . .

# Make mvnw executable and build
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests

# Port setup for Render
ENV PORT=8080
EXPOSE 8080

# Run the JAR (note: check the exact jar name in target/)
CMD ["sh", "-c", "java -jar target/backend-0.0.1-SNAPSHOT.jar --server.port=${PORT}"]
