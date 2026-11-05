"""Prints each failing test of JUnit XML reports as a GitHub Actions error annotation.

Annotations show up in the job summary and on the pull request, so a failure is readable without
downloading the test report artifact.

Usage: python3 scripts/test_failures.py 'glob/of/reports/*.xml' [...]
"""

import glob
import sys
import xml.etree.ElementTree as ET

MAX_MESSAGE = 1500


def escape(text: str) -> str:
    # Workflow commands need %, CR and LF escaped to keep a multi-line message in one annotation.
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main(patterns: list[str]) -> int:
    failures = 0
    for pattern in patterns:
        for path in glob.glob(pattern, recursive=True):
            for case in ET.parse(path).getroot().iter("testcase"):
                problem = case.find("failure")
                if problem is None:
                    problem = case.find("error")
                if problem is None:
                    continue
                failures += 1
                title = f"{case.get('classname', '')}.{case.get('name', '')}"
                message = (problem.text or problem.get("message") or "").strip()[:MAX_MESSAGE]
                print(f"::error title={escape(title)}::{escape(message)}")
    print(f"{failures} failing test(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
