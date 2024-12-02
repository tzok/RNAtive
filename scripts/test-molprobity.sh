#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BASE_URL="https://domgen.cs.put.poznan.pl/PUTWSs/services/rnalyzer"

usage() {
	echo "Usage: $0 [-f filter_level] [-v] file1.pdb [file2.pdb ...]"
	echo "Filter levels:"
	echo "  good        - Accept only 'good' ratings"
	echo "  caution     - Accept 'good' and 'caution' ratings"
	echo "  all         - Accept all structures"
	echo "Options:"
	echo "  -v          - Verbose output"
	echo "  -h          - Show this help"
	exit 1
}

# Default values
FILTER_LEVEL="good"
VERBOSE=0

# Parse command line options
while getopts "f:vh" opt; do
	case $opt in
	f)
		FILTER_LEVEL="$OPTARG"
		;;
	v)
		VERBOSE=1
		;;
	h)
		usage
		;;
	\?)
		usage
		;;
	esac
done

shift $((OPTIND - 1))

# Check if at least one file is provided
if [ $# -eq 0 ]; then
	echo -e "${RED}Error: No PDB files provided${NC}"
	usage
fi

# Function to check if a category passes the filter
check_category() {
	local category="$1"
	local filter="$2"

	case $filter in
	"good")
		[ "$category" = "good" ]
		;;
	"caution")
		[ "$category" = "good" ] || [ "$category" = "caution" ]
		;;
	"all")
		true
		;;
	*)
		echo -e "${RED}Error: Invalid filter level: $filter${NC}"
		exit 1
		;;
	esac
}

# Process each PDB file
for pdb_file in "$@"; do
	echo -e "\n${GREEN}Processing: $pdb_file${NC}"

	# Initialize session
	if [ $VERBOSE -eq 1 ]; then
		echo "Initializing session..."
	fi

	response=$(curl -s -D - -X POST "$BASE_URL")
	resource_id=$(echo "$response" | grep -i "Location:" | grep -o '[^/]*$' | tr -d '\r')

	if [ -z "$resource_id" ]; then
		echo -e "${RED}Failed to initialize session${NC}"
		continue
	fi

	if [ $VERBOSE -eq 1 ]; then
		echo "Session ID: $resource_id"
	fi

	# Prepare XML content
	xml_content="<structures><structure><atoms>$(cat "$pdb_file")</atoms></structure></structures>"

	# Send analysis request
	if [ $VERBOSE -eq 1 ]; then
		echo "Sending analysis request..."
	fi

	response=$(curl -v -X PUT \
		-H "Content-Type: application/xml" \
		-H "Accept: application/json" \
		-d "$xml_content" \
		"$BASE_URL/$resource_id/molprobity")

	# Extract categories from response
	rank_category=$(echo "$response" | grep -o '"rankCategory":"[^"]*"' | cut -d'"' -f4)
	sugar_category=$(echo "$response" | grep -o '"probablyWrongSugarPuckersCategory":"[^"]*"' | cut -d'"' -f4)
	backbone_category=$(echo "$response" | grep -o '"badBackboneConformationsCategory":"[^"]*"' | cut -d'"' -f4)
	bonds_category=$(echo "$response" | grep -o '"badBondsCategory":"[^"]*"' | cut -d'"' -f4)
	angles_category=$(echo "$response" | grep -o '"badAnglesCategory":"[^"]*"' | cut -d'"' -f4)

	# Print detailed results if verbose
	if [ $VERBOSE -eq 1 ]; then
		echo "MolProbity Results:"
		echo "  Overall Rank: $rank_category"
		echo "  Sugar Puckers: $sugar_category"
		echo "  Backbone Conformations: $backbone_category"
		echo "  Bonds: $bonds_category"
		echo "  Angles: $angles_category"
	fi

	# Check if structure passes the filter
	passes_filter=1

	if ! check_category "$rank_category" "$FILTER_LEVEL"; then
		echo -e "${YELLOW}Failed: Overall rank category is $rank_category${NC}"
		passes_filter=0
	fi

	if ! check_category "$sugar_category" "$FILTER_LEVEL"; then
		echo -e "${YELLOW}Failed: Sugar pucker category is $sugar_category${NC}"
		passes_filter=0
	fi

	if ! check_category "$backbone_category" "$FILTER_LEVEL"; then
		echo -e "${YELLOW}Failed: Backbone conformations category is $backbone_category${NC}"
		passes_filter=0
	fi

	if ! check_category "$bonds_category" "$FILTER_LEVEL"; then
		echo -e "${YELLOW}Failed: Bonds category is $bonds_category${NC}"
		passes_filter=0
	fi

	if ! check_category "$angles_category" "$FILTER_LEVEL"; then
		echo -e "${YELLOW}Failed: Angles category is $angles_category${NC}"
		passes_filter=0
	fi

	# Print final result
	if [ $passes_filter -eq 1 ]; then
		echo -e "${GREEN}Structure PASSED all criteria${NC}"
	else
		echo -e "${RED}Structure FAILED one or more criteria${NC}"
	fi

	# Cleanup session
	if [ $VERBOSE -eq 1 ]; then
		echo "Cleaning up session..."
	fi

	curl -s -X DELETE "$BASE_URL/$resource_id" >/dev/null
done
