#!/usr/bin/env python3

import argparse
import json
import sys
import time
from pathlib import Path
from typing import List, Optional

import requests
from tabulate import tabulate

API_BASE = "http://localhost:8080/api/compute"


def submit_job(files: List[Path], dot_bracket: Optional[str] = None) -> str:
    """Submit files for analysis and return the task ID."""
    payload = {
        "files": [
            {
                "name": f.name,
                "content": f.read_text(),
            }
            for f in files
        ],
        "analyzer": "MCANNOTATE",
        "visualizationTool": "VARNA",
        "consensusMode": args.consensus_mode,
        "confidenceLevel": args.confidence,
        "molprobityFilter": args.molprobity_filter,
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
        print(f"Status: {status['status']}", end="\r")

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

    # Display ranking table
    print("\nModel Rankings:")
    print(
        tabulate(
            results["ranking"]["rows"],
            headers=results["ranking"]["headers"],
            tablefmt="grid",
        )
    )

    # For each model, get and display detailed results
    for filename in results["fileNames"]:
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
        default="ALL",
        help="Consensus mode for analysis (default: ALL)",
    )
    submit_parser.add_argument(
        "--confidence",
        type=float,
        default=0.9,
        help="Confidence level threshold (0.0-1.0, default: 0.9)",
    )
    submit_parser.add_argument(
        "--molprobity-filter",
        action="store_true",
        help="Enable MolProbity filtering",
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
            task_id = submit_job(args.files, args.dot_bracket)
            print(f"Submitted task: {task_id}")

            if args.wait:
                wait_for_completion(task_id)
                get_results(task_id)

        elif args.command == "status":
            status = get_status(args.task_id)
            print(f"Status: {status['status']}")
            if status.get("message"):
                print(f"Message: {status['message']}")

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
