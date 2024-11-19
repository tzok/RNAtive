# RNAtive

A Spring Boot application for reference-free ranking of RNA 3D models.

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

## Configuration

The application uses Spring Boot configuration with main settings in `application.properties`.
Key configurations include:

- Database connection (PostgreSQL)
- External analysis service connection
- Logging levels

For development, the application connects to a PostgreSQL database and analysis service containers
defined in the Docker Compose configuration.

## Testing API

The repository includes a Python script `scripts/test-api.py` for testing the API endpoints. The script supports three main commands:

1. Submit files for analysis:

```bash
python scripts/test-api.py submit path/to/file1.pdb path/to/file2.pdb --wait
```

Options:

- `--wait`: Wait for analysis completion and show results
- `--consensus-mode`: Analysis mode (ALL, CANONICAL, NON_CANONICAL, STACKING)
- `--confidence`: Confidence level (0.0-1.0)
- `--analyzer`: Analysis tool (BARNABA, BPNET, FR3D, MCANNOTATE, RNAPOLIS, RNAVIEW)
- `--visualization`: Visualization tool (PSEUDOVIEWER, VARNA, RCHIE, RNAPUZZLER)

2. Check task status:

```bash
python scripts/test-api.py status <task-id>
```

3. Get analysis results:

```bash
python scripts/test-api.py results <task-id>
```

The script will generate visualization output in SVG format and display detailed analysis results in tabular format.
