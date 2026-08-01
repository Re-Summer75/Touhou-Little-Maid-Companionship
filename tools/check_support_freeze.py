"""Reports segments whose contact support sits frozen at a partial value.

Support is meant to be either building toward full while a segment rests on a
surface or fading once the surface is gone. A value that never changes for
seconds at a time is neither, and it means the inward half of the spring is
permanently cancelled by that fraction. Written to check the gating that holds
support while a collider is still measurably in contact.

Usage: py -3 check_support_freeze.py <log>
"""

import re
import sys
from collections import defaultdict

SAMPLE = re.compile(
    r"^\s+(?P<bone>\S+)\s+.*?\boff=\s*(?P<off>[-\d.]+)px"
    r".*?\bsup=\s*(?P<sup>[-\d.]+)"
)
HEADER = re.compile(r"physics (?:displacement|jitter)")


def longest_run(values):
    """Longest stretch of identical values in adjacent report windows."""
    best_length = 0
    best_value = None
    length = 0
    previous_value = object()
    previous_window = -2
    for window, value in values:
        adjacent = window == previous_window + 1
        length = length + 1 if adjacent and value == previous_value else 1
        previous_window = window
        previous_value = value
        if length > best_length:
            best_length = length
            best_value = value
    return best_length, best_value


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(errors="replace")
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    support = defaultdict(list)
    offsets = defaultdict(list)
    window = -1
    with open(sys.argv[1], encoding="utf-8", errors="replace") as handle:
        for line in handle:
            if HEADER.search(line):
                window += 1
                continue
            match = SAMPLE.match(line.rstrip("\n"))
            if not match or window < 0:
                continue
            support[match.group("bone")].append(
                (window, float(match.group("sup")))
            )
            offsets[match.group("bone")].append(float(match.group("off")))

    rows = []
    for bone, series in support.items():
        length, value = longest_run(series)
        if value is None or length < 10:
            continue
        # Zero and full are legitimate resting states; anything between is a
        # support level that stopped moving in either direction.
        partial = 0.02 < value < 0.98
        rows.append((length, bone, value, partial, len(series)))
    rows.sort(reverse=True)

    frozen = [row for row in rows if row[3]]
    print(f"bones sampled={len(support)}  frozen at a partial level={len(frozen)}")
    print(f"{'bone':<20}{'sup':>6}{'heldFor':>9}{'ofSamples':>11}{'offRange':>10}")
    for length, bone, value, _partial, total in frozen[:25]:
        span = max(offsets[bone]) - min(offsets[bone])
        print(f"{bone:<20}{value:>6.2f}{length:>9}{total:>11}{span:>10.2f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
