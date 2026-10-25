"""Prints a Markdown summary of the JaCoCo reports (used by the CI job summary).

core-scoring: parsed from the JaCoCo XML report (line and branch counters).
app: the Android Gradle plugin only produces HTML reports, whose index page holds the totals
(unit tests and instrumented tests are reported separately).
"""

import re
import sys
from pathlib import Path

CORE_XML = Path("core-scoring/build/reports/jacoco/test/jacocoTestReport.xml")
APP_UNIT_INDEX = Path("app/build/reports/coverage/test/debug/index.html")
APP_ANDROID_INDEX = Path("app/build/reports/coverage/androidTest/debug/connected/index.html")


def percent(covered: int, missed: int) -> str:
    total = covered + missed
    return "n/a" if total == 0 else f"{100 * covered / total:.1f}%"


def core_summary() -> list[str]:
    if not CORE_XML.exists():
        return ["| core-scoring | report not found | |"]
    xml = CORE_XML.read_text(encoding="utf-8")
    rows = []
    for kind, label in (("LINE", "lines"), ("BRANCH", "branches")):
        counters = re.findall(rf'<counter type="{kind}" missed="(\d+)" covered="(\d+)"/>', xml)
        missed, covered = (int(counters[-1][0]), int(counters[-1][1])) if counters else (0, 0)
        rows.append(f"| core-scoring ({label}) | {percent(covered, missed)} | {covered} / {covered + missed} |")
    return rows


def app_summary(index: Path, label: str) -> list[str]:
    if not index.exists():
        return []
    html = index.read_text(encoding="utf-8")
    text = re.sub(r"<[^>]+>", " ", html[html.find("Total"):])
    match = re.search(r"([\d,.\s]+) of ([\d,.\s]+)\s+(\d+)%", text)
    if not match:
        return [f"| {label} | unreadable report | |"]
    missed, total = match.group(1).strip(), match.group(2).strip()
    return [f"| {label} (instructions) | {match.group(3)}% | {missed} missed of {total} |"]


def main() -> int:
    print("### Code coverage")
    print()
    print("| Scope | Coverage | Detail |")
    print("| --- | --- | --- |")
    rows = core_summary()
    rows += app_summary(APP_UNIT_INDEX, "app, unit tests")
    rows += app_summary(APP_ANDROID_INDEX, "app, instrumented tests")
    for row in rows:
        print(row)
    print()
    print("The app unit tests do not exercise the Compose screens; the instrumented tests cover the data layer.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
