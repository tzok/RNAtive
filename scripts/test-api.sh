#!/bin/bash

# Configuration
API_URL="http://localhost:8080/api/compute"

# Sample PDB file content (truncated for example)
PDB_CONTENT="ATOM      1  P     A A   1      -3.201   5.069   3.617  1.00 17.85           P"

# Create a JSON request with a sample file
REQUEST_DATA=$(cat <<EOF
{
  "files": [
    {
      "name": "sample.pdb",
      "content": "$PDB_CONTENT"
    }
  ],
  "confidenceLevel": 0.95,
  "analyzer": "BPNET",
  "consensusMode": "CANONICAL",
  "applyMolProbityFilter": true
}
EOF
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
fi
