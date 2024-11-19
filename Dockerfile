# Use OpenJDK 17 as the base image
FROM eclipse-temurin:17-jdk-jammy

# Install PostgreSQL client
RUN apt-get update && apt-get install -y postgresql-client && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the JAR file and wait-for-it script
COPY target/*.jar app.jar
COPY wait-for-it.sh /wait-for-it.sh
RUN chmod +x /wait-for-it.sh

# Expose port 8080
EXPOSE 8080

# Run the application with wait-for-it
CMD ["/wait-for-it.sh", "db", "java", "-jar", "app.jar"]
