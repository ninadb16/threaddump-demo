# -------- Build Stage --------
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# Copy project files
COPY . .

# Make Maven wrapper executable
RUN chmod +x mvnw

# Build the application
RUN ./mvnw clean package -DskipTests

# -------- Runtime Stage --------
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
