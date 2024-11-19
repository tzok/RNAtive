# RNA Structure Analysis Service

A Spring Boot application for analyzing RNA structures, providing visualization and analysis capabilities through a REST API.

## Prerequisites

- Java 17 or higher
- Maven
- Docker and Docker Compose

## Building and Running

1. Build the application:
```bash
mvn clean package
```

2. Start the application with Docker Compose:
```bash
docker-compose up --build
```

The application will be available at `http://localhost:8080`.

## Services

The application provides several endpoints for:
- RNA structure analysis
- Structure visualization using different tools (PseudoViewer, RChie, RNApuzzler)
- Format conversion between different RNA structure notations

## Configuration

The application uses Spring Boot configuration with main settings in `application.properties`. 
Key configurations include:
- Database connection (PostgreSQL)
- External analysis service connection
- Logging levels

For development, the application connects to a PostgreSQL database and analysis service containers 
defined in the Docker Compose configuration.
