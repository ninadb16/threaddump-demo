# -------- Build Stage --------
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# Install git
RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

# Clone repository
RUN git clone https://github.com/ninadb16/threaddump-demo.git .

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
