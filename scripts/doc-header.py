#!/usr/bin/env python3
"""Check that every tracked markdown file opens with its three-line head and links no plan.

Line 1 is the heading, line 2 one unbroken line on what the file holds,
line 3 either `Neighbors: ...` (one line) followed by a blank, or blank.
A standing doc never links a plan file: plans are deleted once shipped (docs/rules.md).
Plans and the archive are working state and exempt; a backlog may point at a plan.

  scripts/doc-header.py           report every file off the shape (same as --check)
  scripts/doc-header.py --check   exit 1 if any file is off the shape
"""
import re
import subprocess
import sys

EXEMPT = ("docs/plans/", "docs/archive/", ".claude/")
PLAN_LINK = re.compile(r"plans/[\w.-]+\.md")


def problems(path):
    text = open(path, encoding="utf-8").read()
    lines = text.split("\n") + ["", "", "", ""]
    if not lines[0].startswith("# "):
        return "line 1 is not a '# ' heading"
    if not lines[1].strip() or lines[1][0] in "#-*|>" or lines[1].startswith("```"):
        return "line 2 is not the one-line scope"
    if lines[2] and not (lines[2].startswith("Neighbors: ") and lines[3] == ""):
        return "line 3 is neither empty nor one 'Neighbors: ' line followed by a blank"
    if not path.endswith("backlog.md") and (link := PLAN_LINK.search(text)):
        return f"links the plan {link.group()}, which goes once it ships"
    return None


def main():
    files = subprocess.run(["git", "ls-files", "*.md"], capture_output=True, text=True, check=True).stdout.split()
    bad = [(f, p) for f in files if not f.startswith(EXEMPT) for p in [problems(f)] if p]
    for f, p in bad:
        print(f"{f}: {p}", file=sys.stderr)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
