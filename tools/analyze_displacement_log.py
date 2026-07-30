"""Summarises the in-game physics displacement log into per-bone statistics.

The log prints every driven segment every 0.1 s, which is far too much to read
directly. What matters is not any single offset but how much each segment's
offset moves over time, so this reports swing, reversal rate and who is pushing.

Usage: python analyze_displacement_log.py <log> [--top N] [--bone PREFIX]
"""

import argparse
import re
import sys
from collections import defaultdict

SAMPLE = re.compile(
    r"^\s+(?P<bone>\S+)\s+off=\s*(?P<off>[-\d.]+)px"
    r"\s+integ=(?P<integ>[-\d.]+)"
    r"\s+proj=(?P<proj>[-\d.]+)"
    r"\s+by=(?P<by>\S+)"
    r"\s+sup=(?P<sup>[-\d.]+)"
    r"\s+damp=(?P<damp>[-\d.]+)"
)
HEADER = re.compile(r"physics displacement \[(?P<maid>[^\]]*)\]")


class Track:
    """Per-bone series of offsets and the solver state that produced them."""

    def __init__(self):
        self.offsets = []
        self.integ = []
        self.proj = []
        self.sup = []
        self.damp = []
        self.by = defaultdict(int)
        # Sample indices where the series is broken by a pose change, so steps
        # are never taken across the discontinuity.
        self.cuts = set()

    def cut(self):
        self.cuts.add(len(self.offsets))

    def add(self, row):
        self.offsets.append(row["off"])
        self.integ.append(row["integ"])
        self.proj.append(row["proj"])
        self.sup.append(row["sup"])
        self.damp.append(row["damp"])
        self.by[row["by"]] += 1

    def swing(self):
        """Widest peak-to-peak travel inside any single run."""
        return max(
            (max(run) - min(run) for run in self.runs() if run),
            default=0.0,
        )

    def runs(self):
        """Contiguous stretches of samples, split at every pose change."""
        result = []
        current = []
        for index, offset in enumerate(self.offsets):
            if index in self.cuts and current:
                result.append(current)
                current = []
            current.append(offset)
        if current:
            result.append(current)
        return result

    def steps(self):
        """Offset change between consecutive samples within a run."""
        return [
            after - before
            for run in self.runs()
            for before, after in zip(run, run[1:])
        ]

    def shake(self):
        """
        Mean per-sample travel that reverses direction.

        Peak-to-peak range cannot separate shake from pose, and neither can raw
        travel: a segment following an animation covers ground steadily. Travel
        that changes direction is what shake is made of, so only reversing steps
        are counted, averaged over the whole window so a brief wobble does not
        rank alongside a sustained one.
        """
        total = 0.0
        count = 0
        for run in self.runs():
            steps = [after - before for before, after in zip(run, run[1:])]
            count += len(steps)
            for before, after in zip(steps, steps[1:]):
                if before * after < 0.0:
                    total += abs(after)
        return total / count if count else 0.0

    def reversals(self):
        """Direction changes within each run, ignoring flat noise."""
        count = 0
        for run in self.runs():
            sign = 0
            for before, after in zip(run, run[1:]):
                delta = after - before
                if abs(delta) < 0.02:
                    continue
                current = 1 if delta > 0 else -1
                if sign and current != sign:
                    count += 1
                sign = current
        return count

    def period(self):
        """Mean samples between reversals, so slow swings read apart from buzz."""
        reversals = self.reversals()
        return len(self.offsets) / reversals if reversals else 0.0

    def samples(self):
        return len(self.offsets)

    def spike(self):
        """
        Ratio of the largest step to the typical one.

        Sampling at 0.1 s aliases physics running at 60 fps, so a big step may be
        either an oscillation folded down to the sample rate or a single genuine
        lurch as the maid moves. A steady oscillation has every step about the
        same size, giving a ratio near one; a lurch stands far above its
        neighbours. Anything past about four is pose motion, not shake.
        """
        steps = [abs(step) for step in self.steps()]
        typical = sum(steps) / len(steps) if steps else 0.0
        if typical <= 1.0e-6:
            return 0.0
        return max(steps) / typical

    def alternation(self):
        """Share of consecutive step pairs that reverse, ignoring flat noise."""
        steps = [step for step in self.steps() if abs(step) >= 0.02]
        if len(steps) < 2:
            return 0.0
        flips = sum(
            1 for before, after in zip(steps, steps[1:]) if before * after < 0.0
        )
        return flips / (len(steps) - 1)

    def mean(self, series):
        return sum(series) / len(series) if series else 0.0

    def pushers(self):
        ranked = sorted(self.by.items(), key=lambda item: -item[1])
        return ",".join(f"{name}:{count}" for name, count in ranked[:3])


POSE_STEP_PIXELS = 3.0
POSE_SHARE = 0.15


def read_blocks(path):
    """Reads the log into one dict of bone -> row per printed sample."""
    blocks = []
    maids = set()
    current = None
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            header = HEADER.search(line)
            if header:
                maids.add(header.group("maid"))
                current = {}
                blocks.append(current)
                continue
            match = SAMPLE.match(line.rstrip("\n"))
            if not match or current is None:
                continue
            row = {key: match.group(key) for key in ("bone", "by")}
            for key in ("off", "integ", "proj", "sup", "damp"):
                row[key] = float(match.group(key))
            current[row["bone"]] = row
    return blocks, maids


def posed(blocks):
    """
    Flags sample transitions where the whole model moved at once.

    The log samples ten times a second while the animation plays, so a maid
    standing up or turning shifts every segment tens of pixels at once. That is
    the pose doing its job, and counting it as shake buries the segments that are
    actually oscillating. Physics jitter is local to a few segments; a change
    that hits most of them at the same instant is the animation.
    """
    flags = []
    for before, after in zip(blocks, blocks[1:]):
        shared = set(before) & set(after)
        if not shared:
            flags.append(True)
            continue
        moved = sum(
            1
            for bone in shared
            if abs(after[bone]["off"] - before[bone]["off"]) > POSE_STEP_PIXELS
        )
        flags.append(moved / len(shared) >= POSE_SHARE)
    return flags


def parse(path):
    """Collects one Track per bone, skipping transitions driven by the pose."""
    blocks, maids = read_blocks(path)
    skip = posed(blocks)
    tracks = defaultdict(Track)
    for index, block in enumerate(blocks):
        if index and skip[index - 1]:
            # The step into this sample was the pose moving, so break the series
            # rather than let that step read as a reversal.
            for bone in block:
                tracks[bone].cut()
        for bone, row in block.items():
            tracks[bone].add(row)
    return tracks, maids, len(blocks), sum(skip)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("log")
    parser.add_argument("--top", type=int, default=20)
    parser.add_argument("--bone", default=None)
    args = parser.parse_args()

    tracks, maids, blocks, posed_count = parse(args.log)
    if not tracks:
        print("no displacement samples found", file=sys.stderr)
        return 1

    print(
        f"samples={blocks}  bones={len(tracks)}  "
        f"pose-driven transitions skipped={posed_count}"
    )
    print(
        f"{'bone':<20}{'shake':>8}{'swing':>8}{'maxStep':>8}{'spike':>7}"
        f"{'alt':>6}{'revs':>6}{'integ':>9}{'proj':>9}{'sup':>6}{'damp':>6}"
        "  pushedBy"
    )

    items = tracks.items()
    if args.bone:
        items = [(name, t) for name, t in items if name.startswith(args.bone)]
    ranked = sorted(items, key=lambda item: -item[1].shake())[: args.top]
    for name, track in ranked:
        steps = track.steps()
        peak = max((abs(step) for step in steps), default=0.0)
        print(
            f"{name:<20}{track.shake():>8.2f}{track.swing():>8.2f}"
            f"{peak:>8.2f}{track.spike():>7.1f}{track.alternation():>6.2f}"
            f"{track.reversals():>6}{track.mean(track.integ):>9.5f}"
            f"{track.mean(track.proj):>9.5f}{track.mean(track.sup):>6.2f}"
            f"{track.mean(track.damp):>6.2f}  {track.pushers()}"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
