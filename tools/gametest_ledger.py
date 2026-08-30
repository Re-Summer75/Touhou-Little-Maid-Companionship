"""Tracks which GameTest scenarios stay red across rounds, and what changed.

Round-over-round we only ever looked at "how many failed" plus a snapshot of
the names. That hides the two things worth knowing: which scenarios have been
red for dozens of rounds untouched, and which ones a given change just broke.
Both were being rediscovered by hand every time, several rounds late.

A round only counts as evidence if it reached the summary line
("N required tests failed" / "All required tests passed"). Rounds cut short by
a stall or the hard cap report zero failures while having run almost nothing --
reading those as green is how a truncated round once got reported as progress.

Usage:
  py -3 gametest_ledger.py table <logdir> [--last N]
  py -3 gametest_ledger.py diff  <logdir> <round>
"""

import glob
import os
import re
import sys
from collections import Counter

FAILURE = re.compile(r"LogTestReporter\]: (\S+) failed!")
# The authoritative completeness mark. "N required tests failed" also prints on
# a run that ended early, and a passing scenario writes nothing to the log at
# all -- so "the name is absent" reads identically for green and for never-ran.
# Only the completion banner carries the count, which is what makes a truncated
# round detectable at all.
COMPLETE = re.compile(r"=+ (\d+) GAME TESTS COMPLETE")


def read_round(path):
    """(name, closed, {scenario}, {flaky}) for one log.

    Flaky is decided from evidence inside the round itself: a scenario that
    runs as several copies (_run1.._runN) and fails only some of them passed
    on the others, in the same build, in the same world. That is an execution
    margin problem, not a regression -- and mixing the two cost a full round
    of diagnosis once already. Cross-round flipping cannot tell the two apart,
    because "just fixed" and "just broken" flip exactly the same way.
    """
    with open(path, encoding="utf-8", errors="ignore") as handle:
        text = handle.read()
    copies = {}
    reds = set()
    counts = {}
    for match in FAILURE.finditer(text):
        raw = match.group(1)
        base = re.sub(r"_run\d+$", "", raw)
        reds.add(base)
        counts[base] = counts.get(base, 0) + 1
        run = re.search(r"_run(\d+)$", raw)
        if run:
            copies.setdefault(base, set()).add(int(run.group(1)))
    flaky = {base for base, runs in copies.items() if max(runs) > len(runs)}
    banner = COMPLETE.search(text)
    total = int(banner.group(1)) if banner else 0
    return os.path.basename(path)[:-4], total > 0, reds, flaky, counts, total


def load(logdir):
    """Every round in the directory, ordered by the number in its name."""
    rounds = []
    for path in glob.glob(os.path.join(logdir, "*.log")):
        digits = re.search(r"(\d+)", os.path.basename(path))
        if digits:
            rounds.append((int(digits.group(1)),) + read_round(path))
    rounds.sort()
    return rounds


def table(rounds, last):
    """Scenario x round matrix over closed rounds only."""
    closed = [r for r in rounds if r[2]][-last:]
    if not closed:
        print("no closed round in this directory")
        return
    seen = Counter()
    for row in closed:
        seen.update(row[3])
    width = max(len(name) for name in seen) + 2
    header = "".join("%5s" % row[1][2:] for row in closed)
    print("%-*s%s" % (width, "scenario", header))
    for name, count in seen.most_common():
        marks = "".join("    R" if name in row[3] else "    ."
                        for row in closed)
        print("%-*s%s   %d/%d" % (width, name, marks, count, len(closed)))
    print()
    print("R = failed, . = passed; %d closed rounds, %s..%s"
          % (len(closed), closed[0][1], closed[-1][1]))


def diff(rounds, target):
    """What this round changed against the previous closed round."""
    here = [r for r in rounds if r[1] == target]
    if not here:
        print("no such round: %s" % target)
        return 2
    number, name, closed, reds, flaky_here, counts, total = here[0]
    if not closed:
        print("%s was CUT SHORT -- no verdict, do not read its red count" % name)
        return 1
    earlier = [r for r in rounds if r[0] < number and r[2]]
    if not earlier:
        print("%s: %d red (no earlier closed round to compare)" % (name, len(reds)))
        return 0
    prev_name, prev_reds = earlier[-1][1], earlier[-1][3]
    prev_counts, prev_total = earlier[-1][5], earlier[-1][6]
    # Flaky comes from this round's own copies, plus any round that recently
    # caught the same scenario failing unevenly.
    recent = [r for r in rounds if r[2]][-8:]
    flaky = set(flaky_here).union(*[r[4] for r in recent]) if recent else flaky_here
    fresh = sorted(reds - prev_reds)
    fixed = sorted(prev_reds - reds)
    stuck = sorted(reds & prev_reds)
    print("%s vs %s: %d red -> %d red  (%d -> %d tests ran)"
          % (prev_name, name, len(prev_reds), len(reds), prev_total, total))
    # A round that ran fewer scenarios can look like progress purely by not
    # reaching the failures. Say so rather than letting the red count speak.
    if total < prev_total:
        print("  !! %d fewer tests ran than last round -- treat FIXED with "
              "suspicion" % (prev_total - total))
    for label, names in (("NEW RED", fresh), ("FIXED", fixed), ("STILL RED", stuck)):
        for scenario in names:
            tag = "  (flaky)" if scenario in flaky else ""
            # Copy counts matter: a scenario stuck at STILL RED whose failing
            # copies went 1 -> 3 got worse, and the red-kind count alone hides
            # that completely.
            was, now = prev_counts.get(scenario, 0), counts.get(scenario, 0)
            move = ""
            if label == "STILL RED" and was != now:
                move = "  %d -> %d copies %s" % (
                    was, now, "WORSE" if now > was else "better")
            print("  %-10s %s%s%s" % (label, scenario, tag, move))
    return 0


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    mode, logdir = argv[1], argv[2]
    rounds = load(logdir)
    if not rounds:
        print("no logs in %s" % logdir)
        return 2
    if mode == "table":
        last = int(argv[argv.index("--last") + 1]) if "--last" in argv else 12
        table(rounds, last)
        return 0
    if mode == "diff":
        if len(argv) < 4:
            print(__doc__)
            return 2
        return diff(rounds, argv[3])
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
