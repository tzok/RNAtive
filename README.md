# RNAtive

RNAtive is a consensus-based RNA structure analysis system designed to process multiple structural models sharing the same sequence and to identify reliable base pairs and stacking interactions. It supports model validation, improves structural predictions, and facilitates studies of RNA structure evolution. The tool accepts a minimum of two RNA 3D structure models in PDB or mmCIF format (with a total file size limit of 100 MB), and analyzes them using state-of-the-art base-pair annotation tools. Comparing annotations across all input models, it generates a consensus structure that highlights recurrent interactions, which are more likely to reflect stable, native-like folds. It then evaluates and ranks the input models based on their consistency with the derived consensus.

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

By default, Docker Compose reads both `docker-compose.yml` and `docker-compose.override.yml` files. The override file configures the application for local development without HTTPS, making it available at `http://localhost`.

### Running with HTTPS (Production Mode)

To run the application in production mode with HTTPS support:

```bash
docker-compose -f docker-compose.yml up --build
```

This command explicitly uses only the base configuration file, ignoring the override file, which enables the HTTPS configuration and certbot service for SSL certificate management.

The production deployment will be available at `https://rnative.cs.put.poznan.pl` (or your configured domain).

## Configuration

The application uses Spring Boot configuration with main settings in `application.properties`.
Key configurations include:

- Database connection (PostgreSQL)
- External analysis service connection
- Logging levels

For development, the application connects to a PostgreSQL database and analysis service containers
defined in the Docker Compose configuration.

### Docker Compose Configuration

The project uses two Docker Compose configuration files:

1. `docker-compose.yml` - Base configuration for all environments, including production settings with HTTPS
2. `docker-compose.override.yml` - Local development overrides that:
   - Configure services for local access without HTTPS
   - Map local ports appropriately
   - Mount additional volumes for development
   - Disable production-only services like certbot

When you run `docker-compose up` without specifying a file, Docker Compose automatically merges both files, with settings in the override file taking precedence.

## Adapters Service

The application uses the `rnapdbee-adapters-image` which is built from the [RNApdbee-adapters](https://github.com/rnapdbee/rnapdbee-adapters) repository. This service provides RNA structure analysis tools that are essential for the application's functionality.

## Running in CLI Mode

The application can be run in CLI mode for command-line processing. To use the CLI:

1. Start the required services:
```bash
docker-compose up --build
```

2. Access the backend container:
```bash
docker exec -it rnative-backend-1 bash
```

3. Run the application in CLI mode:
```bash
APP_MODE=cli java -jar app.jar [options]
```

Available options:
- `--help`: Show help message
- `--mol-probity <filter>`: Set MolProbity filter (ALL, CLASHSCORE, CLASHSCORE_BONDS_ANGLES)
- `--analyzer <analyzer>`: Set analyzer (BARNABA, BPNET, FR3D, MCANNOTATE, RNAPOLIS, RNAVIEW)
- `--consensus <mode>`: Set consensus mode (CANONICAL, NON_CANONICAL, STACKING, ALL)
- `--confidence <level>`: Set confidence level (0.0-1.0)
- `--dot-bracket <structure>`: Set expected 2D structure

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
- `--molprobity-filter`: MolProbity filter (ALL, CLASHSCORE, CLASHSCORE_BONDS_ANGLES)
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
