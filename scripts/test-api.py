#!/usr/bin/env python3

import argparse
import json
import sys
import time
from pathlib import Path
from typing import List, Optional

import requests
from tabulate import tabulate

API_BASE = "http://localhost/api/compute"


def submit_job(files: List[Path], args, dot_bracket: Optional[str] = None) -> str:
    """Submit files for analysis and return the task ID."""
    payload = {
        "files": [
            {
                "name": f.name,
                "content": f.read_text(),
            }
            for f in files
        ],
        "analyzer": args.analyzer,
        "visualizationTool": args.visualization,
        "consensusMode": args.consensus_mode,
        "confidenceLevel": args.confidence,
        "molProbityFilter": args.molprobity_filter,
    }

    if dot_bracket:
        payload["dotBracket"] = dot_bracket

    response = requests.post(f"{API_BASE}", json=payload)
    response.raise_for_status()
    return response.json()["taskId"]


def get_status(task_id: str) -> dict:
    """Get the current status of a task."""
    response = requests.get(f"{API_BASE}/{task_id}/status")
    response.raise_for_status()
    return response.json()


def wait_for_completion(task_id: str, interval: int = 5) -> None:
    """Wait for a task to complete, showing status updates."""
    while True:
        status = get_status(task_id)
        print(f"Status: {status['status']}")

        if status["status"] == "COMPLETED":
            print("\nTask completed successfully!")
            break
        elif status["status"] == "FAILED":
            print(f"\nTask failed: {status.get('message', 'Unknown error')}")
            sys.exit(1)

        time.sleep(interval)


def get_results(task_id: str) -> None:
    """Fetch and display results for a completed task."""
    # Get overall results
    response = requests.get(f"{API_BASE}/{task_id}/result")
    response.raise_for_status()
    results = response.json()

    # Display Model Rankings and Removal Reasons
    print("\nModel Rankings:")
    print(
        tabulate(
            results["ranking"]["rows"],
            headers=results["ranking"]["headers"],
            tablefmt="grid",
        )
    )

    print("\nCanonical Base Pairs:")
    print(
        tabulate(
            results["canonicalPairs"]["rows"],
            headers=results["canonicalPairs"]["headers"],
            tablefmt="grid",
        )
    )

    print("\nNon-canonical Base Pairs:")
    print(
        tabulate(
            results["nonCanonicalPairs"]["rows"],
            headers=results["nonCanonicalPairs"]["headers"],
            tablefmt="grid",
        )
    )

    print("\nStackings:")
    print(
        tabulate(
            results["stackings"]["rows"],
            headers=results["stackings"]["headers"],
            tablefmt="grid",
        )
    )

    # Display dotBracket
    print(f"\nDotBracket:\n{results['dotBracket']}")

    # Display results for the first file only
    if results["fileNames"]:
        filename = results["fileNames"][0]
        print(f"\nDetailed results for {filename}:")

        response = requests.get(f"{API_BASE}/{task_id}/result/{filename}")
        response.raise_for_status()
        model_results = response.json()

        print("\nCanonical Base Pairs:")
        print(
            tabulate(
                model_results["canonicalPairs"]["rows"],
                headers=model_results["canonicalPairs"]["headers"],
                tablefmt="grid",
            )
        )

        print("\nNon-canonical Base Pairs:")
        print(
            tabulate(
                model_results["nonCanonicalPairs"]["rows"],
                headers=model_results["nonCanonicalPairs"]["headers"],
                tablefmt="grid",
            )
        )

        print("\nStackings:")
        print(
            tabulate(
                model_results["stackings"]["rows"],
                headers=model_results["stackings"]["headers"],
                tablefmt="grid",
            )
        )

        # Print the dotBracket of the model
        print(f"\nDotBracket:\n{model_results['dotBracket']}")

    # Get and save SVG visualization
    response = requests.get(f"{API_BASE}/{task_id}/svg")
    response.raise_for_status()
    svg_filename = f"visualization.svg"
    with open(svg_filename, "w") as f:
        f.write(response.text)
    print(f"\nSaved visualization to {svg_filename}")


def main():
    parser = argparse.ArgumentParser(description="Test RNA structure analysis API")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # Submit command
    submit_parser = subparsers.add_parser("submit", help="Submit files for analysis")
    submit_parser.add_argument(
        "files", nargs="+", type=Path, help="PDB files to analyze"
    )
    submit_parser.add_argument("--dot-bracket", help="Optional dot-bracket notation")
    submit_parser.add_argument(
        "--wait", action="store_true", help="Wait for completion"
    )
    submit_parser.add_argument(
        "--consensus-mode",
        choices=["ALL", "CANONICAL", "NON_CANONICAL", "STACKING"],
        default="CANONICAL",
        help="Consensus mode for analysis (default: CANONICAL)",
    )
    submit_parser.add_argument(
        "--confidence",
        type=float,
        default=None,
        help="Confidence level threshold (0.0-1.0, default: fuzzy)",
    )
    submit_parser.add_argument(
        "--molprobity-filter",
        choices=["ALL", "CLASHSCORE", "CLASHSCORE_BONDS_ANGLES"],
        default="ALL",  # Default to no filtering
        help="MolProbity filtering level (default: ALL)",
    )
    submit_parser.add_argument(
        "--analyzer",
        choices=["BARNABA", "BPNET", "FR3D", "MCANNOTATE", "RNAPOLIS", "RNAVIEW"],
        default="BPNET",
        help="Analysis tool to use (default: BPNET)",
    )
    submit_parser.add_argument(
        "--visualization",
        choices=["PSEUDOVIEWER", "VARNA", "RCHIE", "RNAPUZZLER"],
        default="VARNA",
        help="Visualization tool to use (default: VARNA)",
    )

    # Status command
    status_parser = subparsers.add_parser("status", help="Check task status")
    status_parser.add_argument("task_id", help="Task ID to check")

    # Results command
    results_parser = subparsers.add_parser("results", help="Get task results")
    results_parser.add_argument("task_id", help="Task ID to get results for")

    args = parser.parse_args()

    try:
        if args.command == "submit":
            task_id = submit_job(args.files, args, args.dot_bracket)
            print(f"Submitted task: {task_id}")

            if args.wait:
                wait_for_completion(task_id)
                get_results(task_id)

        elif args.command == "status":
            status = get_status(args.task_id)
            print(f"Status: {status['status']}")
            if status.get("message"):
                print(f"Message: {status['message']}")
            if status.get("removalReasons"):
                print("\nRemoved models:")
                for model, reasons in status["removalReasons"].items():
                    print(f"\n  {model}:")
                    for reason in reasons:
                        print(f"    - {reason}")

        elif args.command == "results":
            get_results(args.task_id)

    except requests.exceptions.RequestException as e:
        print(f"API Error: {e}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
