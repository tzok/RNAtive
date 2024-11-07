#!/bin/bash

# Default values
API_URL="http://localhost:8080/api/compute"
CONFIDENCE_LEVEL=0.95
ANALYZER="BPNET"
CONSENSUS_MODE="CANONICAL"
MOL_PROBITY_FILTER=true
VISUALIZATION_TOOL="RNAPUZZLER"

# Help function
print_usage() {
    echo "Usage: $0 [options] file1 [file2 ...]"
    echo "Options:"
    echo "  -c, --confidence LEVEL    Confidence level (0-1), default: 0.95"
    echo "  -a, --analyzer TOOL       Analyzer tool (BARNABA|BPNET|FR3D|MCANNOTATE|RNAPOLIS|RNAVIEW), default: BPNET"
    echo "  -m, --consensus MODE      Consensus mode (CANONICAL|NON_CANONICAL|STACKING|ALL), default: CANONICAL"
    echo "  --no-molprobity          Disable MolProbity filter"
    echo "  -v, --visualization TOOL  Visualization tool (RNAPUZZLER|VARNA|PSEUDOVIEWER|RCHIE), default: RNAPUZZLER"
    echo "  -h, --help               Show this help message"
    exit 1
}

# Parse command line arguments
FILES=()
while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--confidence)
            CONFIDENCE_LEVEL="$2"
            shift 2
            ;;
        -a|--analyzer)
            ANALYZER="$2"
            shift 2
            ;;
        -m|--consensus)
            CONSENSUS_MODE="$2"
            shift 2
            ;;
        --no-molprobity)
            MOL_PROBITY_FILTER=false
            shift
            ;;
        -v|--visualization)
            VISUALIZATION_TOOL="$2"
            shift 2
            ;;
        -h|--help)
            print_usage
            ;;
        *)
            if [[ -f "$1" ]]; then
                FILES+=("$1")
            else
                echo "Error: File not found: $1"
                exit 1
            fi
            shift
            ;;
    esac
done

# Check if at least one file was provided
if [ ${#FILES[@]} -eq 0 ]; then
    echo "Error: No input files provided"
    print_usage
fi

# Prepare files array for JSON using jq
FILES_JSON=$(
    for file in "${FILES[@]}"; do
        jq -n \
            --arg name "$(basename "$file")" \
            --arg content "$(cat "$file")" \
            '{name: $name, content: $content}'
    done | jq -s '.'
)

# Create JSON request using jq
REQUEST_DATA=$(jq -n \
    --argjson files "$FILES_JSON" \
    --arg confidenceLevel "$CONFIDENCE_LEVEL" \
    --arg analyzer "$ANALYZER" \
    --arg consensusMode "$CONSENSUS_MODE" \
    --argjson molProbityFilter "$MOL_PROBITY_FILTER" \
    --arg visualizationTool "$VISUALIZATION_TOOL" \
    '{
        files: $files,
        confidenceLevel: ($confidenceLevel | tonumber),
        analyzer: $analyzer,
        consensusMode: $consensusMode,
        applyMolProbityFilter: $molProbityFilter,
        visualizationTool: $visualizationTool
    }'
)

# Submit computation request
echo "Submitting computation request..."
RESPONSE=$(curl -s -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -d "$REQUEST_DATA")

# Extract task ID from response
TASK_ID=$(echo $RESPONSE | jq -r '.taskId')
echo "Task ID: $TASK_ID"

# Poll for task status
echo "Polling for task status..."
while true; do
  STATUS_RESPONSE=$(curl -s -X GET "$API_URL/$TASK_ID/status")
  STATUS=$(echo $STATUS_RESPONSE | jq -r '.status')
  echo "Current status: $STATUS"
  
  if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
    break
  fi
  
  sleep 2
done

# If task completed, get results
if [ "$STATUS" = "COMPLETED" ]; then
  echo "Getting results..."
  curl -s -X GET "$API_URL/$TASK_ID/result" | json_pp
  
  echo "Getting SVG visualization..."
  curl -s -X GET "$API_URL/$TASK_ID/svg" > result.svg
  
  echo "Getting original file..."
  curl -s -X GET "$API_URL/$TASK_ID/file?filename=sample.pdb" | jq .
  
  echo "Getting CSV tables..."
  curl -s -X GET "$API_URL/$TASK_ID/csv-tables" | jq .
fi
