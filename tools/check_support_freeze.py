"""Reports segments whose contact support sits frozen at a partial value.

Support is meant to be either building toward full while a segment rests on a
surface or fading once the surface is gone. A value that never changes for
seconds at a time is neither, and it means the inward half of the spring is
permanently cancelled by that fraction. Written to check the gating that holds
support while a collider is still measurably in contact.

Usage: python check_support_freeze.py <log>
"""

import re
import sys
from collections import defaultdict

SAMPLE = re.compile(
    r"^\s+(?P<bone>\S+)\s+off=\s*(?P<off>[-\d.]+)px"
    r".*?sup=(?P<sup>[-\d.]+)\s+damp=(?P<damp>[-\d.]+)"
)
HEADER = re.compile(r"physics displacement")


def longest_run(values):
    """Longest stretch of identical consecutive values, and that value."""
    best_length = 0
    best_value = None
    length = 0
    previous = object()
    for value in values:
        length = length + 1 if value == previous else 1
        previous = value
        if length > best_length:
            best_length = length
            best_value = value
    return best_length, best_value


def main():
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    support = defaultdict(list)
    offsets = defaultdict(list)
    with open(sys.argv[1], encoding="utf-8", errors="replace") as handle:
        for line in handle:
            if HEADER.search(line):
                continue
            match = SAMPLE.match(line.rstrip("\n"))
            if not match:
                continue
            support[match.group("bone")].append(float(match.group("sup")))
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
