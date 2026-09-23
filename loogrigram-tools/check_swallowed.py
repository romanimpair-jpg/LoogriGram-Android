#!/usr/bin/env python3
"""LoogriGram: find methods a commit range deleted that the tree still calls.

Removing a feature usually means cutting a contiguous range of lines, and the
obvious check - that the range's braces balance - is not enough. An unrelated
method sitting inside the range balances too, so it goes with the cut and
nothing local complains. javac is the only thing that notices, and only if its
conclusion is actually read.

That happened on 2026-09-23: the gift-transfer cut took presentFragment and
both getInputStarGift forms with it, leaving thirteen call sites pointing at
nothing, and four compiles failed before it was spotted.

This reports a removed name only when NO declaration of it is left anywhere in
the source tree but call sites remain - exactly the swallowed-neighbour case,
and quiet about overrides and common names like add() or apply().

    python loogrigram-tools/check_swallowed.py [<range>]

<range> defaults to origin/dev..HEAD. Run it before dispatching a compile; it
takes a few seconds and costs nothing.
"""
import os
import re
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = 'TMessagesProj/src/main/java'
RANGE = sys.argv[1] if len(sys.argv) > 1 else 'origin/dev..HEAD'
REV = RANGE.split('..')[-1] or 'HEAD'      # grep that revision, not the worktree

DECL = re.compile(
    r'^\s*(?:@\w+\s+)*(?:public|private|protected)\s+'
    r'(?:static\s+|final\s+|abstract\s+|synchronized\s+|native\s+|default\s+)*'
    r'(?:<[^>]+>\s*)?'
    r'[\w.$<>,\[\]?\s]+?\s+'
    r'(\w+)\s*\('
)


def git(*args):
    return subprocess.run(['git', '-C', REPO, *args],
                          capture_output=True).stdout.decode('utf-8', 'replace')


def body(grep_line):
    # git grep with a rev prints  rev:path:lineno:text
    parts = grep_line.split(':', 3)
    return parts[-1] if len(parts) == 4 else grep_line


def main():
    removed, added = set(), set()
    for line in git('diff', RANGE, '--', SRC).splitlines():
        if line.startswith('-') and not line.startswith('---'):
            m = DECL.match(line[1:])
            if m:
                removed.add(m.group(1))
        elif line.startswith('+') and not line.startswith('+++'):
            m = DECL.match(line[1:])
            if m:
                added.add(m.group(1))

    candidates = sorted(removed - added)
    orphans = []
    for name in candidates:
        pat = r'\b%s\s*\(' % re.escape(name)
        decls = git('grep', '-nE', r'(public|private|protected|default).*' + pat,
                    REV, '--', SRC).splitlines()
        if any(DECL.match(body(l)) for l in decls):
            continue                           # still declared somewhere
        calls = [l for l in git('grep', '-nE', pat, REV, '--', SRC).splitlines()
                 if l.strip() and not body(l).lstrip().startswith('//')]
        if calls:
            orphans.append((name, calls))

    print('%d declarations removed in %s; %d have no declaration left but are '
          'still called\n' % (len(candidates), RANGE, len(orphans)))
    for name, calls in orphans:
        print('ORPHANED: %s  (%d call sites)' % (name, len(calls)))
        for c in calls[:5]:
            print('    ' + c.split(SRC + '/')[-1][:140])
        print()
    if not orphans:
        print('OK - every removed declaration is either gone entirely or still declared.')
    return 1 if orphans else 0


if __name__ == '__main__':
    sys.exit(main())
