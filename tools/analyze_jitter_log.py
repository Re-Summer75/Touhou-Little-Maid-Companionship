"""Aggregates the per-frame jitter report across every window in a log.

The in-game report prints the worst twelve segments every half second. What
matters across a session is which segments appear again and again, and which of
the two faults each one has: a reversal every frame or two is a segment trapped
between constraints, while an occasional large step is a single ejection on an
otherwise settled segment. They need different fixes, so they are separated here.

Usage: python analyze_jitter_log.py <log>
"""

import re
import sys
from collections import defaultdict

ROW = re.compile(
    r"^\s+(?P<bone>\S+)\s+buzz=\s*(?P<buzz>[-\d.]+)px/f"
    r"\s+peak=\s*(?P<peak>[-\d.]+)"
    r"\s+revs=\s*(?P<revs>\d+)"
    r"\s+per=\s*(?P<per>[-\d.]+)"
    r"\s+off=\s*(?P<off>[-\d.]+)px"
    r"\s+sup=\s*(?P<sup>[-\d.]+)"
    r"(?:\s+damp=\s*(?P<damp>[-\d.]+)/(?P<damp_peak>[-\d.]+))?"
    r"\s+hitF=\s*(?P<hit>\d+)"
    r"\s+colF=\s*(?P<col>\d+)"
    r"\s+swgF=\s*(?P<swg>\d+)"
)
HEADER = re.compile(r"physics jitter \[.*?\] frames=(?P<frames>\d+)")
# A reversal every frame or two is a constraint fight; anything slower is a
# segment that is settled apart from the odd lurch.
BUZZ_PERIOD = 3.0


class Bone:
    def __init__(self):
        self.windows = 0
        self.buzz = []
        self.peak = []
        self.per = []
        self.support = []
        self.damping = []
        self.damping_peak = []
        self.hit = 0
        self.col = 0
        self.swing = 0
        self.frames = 0

    def add(self, row, frames):
        self.windows += 1
        self.buzz.append(row["buzz"])
        self.peak.append(row["peak"])
        self.per.append(row["per"])
        self.support.append(row["sup"])
        self.damping.append(row["damp"])
        self.damping_peak.append(row["damp_peak"])
        self.hit += row["hit"]
        self.col += row["col"]
        self.swing += row["swg"]
        self.frames += frames

    def mean(self, series):
        return sum(series) / len(series) if series else 0.0

    def fast(self):
        """Windows where the reversal period says it is fighting every frame."""
        return [p for p in self.per if 0.0 < p <= BUZZ_PERIOD]

    def kind(self):
        share = len(self.fast()) / len(self.per) if self.per else 0.0
        if share >= 0.5:
            return "buzz"
        if share > 0.0:
            return "mixed"
        return "lurch"

    def share(self, count):
        return count / self.frames if self.frames else 0.0


def main():
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    bones = defaultdict(Bone)
    windows = 0
    frames = 0
    with open(sys.argv[1], encoding="utf-8", errors="replace") as handle:
        for line in handle:
            header = HEADER.search(line)
            if header:
                windows += 1
                frames = int(header.group("frames"))
                continue
            match = ROW.match(line.rstrip("\n"))
            if not match:
                continue
            row = {"bone": match.group("bone")}
            for key in ("buzz", "peak", "per", "off", "sup"):
                row[key] = float(match.group(key))
            row["damp"] = float(match.group("damp") or 0.0)
            row["damp_peak"] = float(match.group("damp_peak") or 0.0)
            for key in ("revs", "hit", "col", "swg"):
                row[key] = int(match.group(key))
            bones[row["bone"]].add(row, frames)

    print(f"windows={windows}  bones appearing={len(bones)}")
    print(
        f"{'bone':<20}{'kind':>6}{'seen':>5}{'buzz':>7}{'peak':>7}{'per':>6}"
        f"{'sup':>6}{'dmp':>6}{'dpk':>6}{'hit%':>6}{'col%':>6}{'swg%':>6}"
    )
    ranked = sorted(bones.items(), key=lambda item: -item[1].mean(item[1].buzz))
    for name, bone in ranked[:20]:
        print(
            f"{name:<20}{bone.kind():>6}{bone.windows:>5}"
            f"{bone.mean(bone.buzz):>7.3f}{max(bone.peak):>7.2f}"
            f"{bone.mean(bone.per):>6.1f}{bone.mean(bone.support):>6.2f}"
            f"{bone.mean(bone.damping):>6.2f}{max(bone.damping_peak):>6.2f}"
            f"{bone.share(bone.hit) * 100:>6.0f}"
            f"{bone.share(bone.col) * 100:>6.0f}"
            f"{bone.share(bone.swing) * 100:>6.0f}"
        )

    print()
    for kind in ("buzz", "mixed", "lurch"):
        group = [name for name, bone in bones.items() if bone.kind() == kind]
        print(f"{kind:<6} {len(group):>3}: {' '.join(sorted(group)[:14])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
