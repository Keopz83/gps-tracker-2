"""Seeds dummy runs into the run history (REQ-9) of the debug app on a connected device or emulator.

Writes 22 jogging and cycling runs from today back to more than a year ago (two of them on the same day, two of them
today, so every span of the statistics, REQ-29, has data), in the app's own storage format: a line per run in
`files/history.tsv` and its track, with elevation and pauses, as a GPX file (REQ-8) in `files/runs`.

    python3 test-support/seed_runs.py              # the only connected device
    python3 test-support/seed_runs.py -s emulator-5554
    python3 test-support/seed_runs.py --replace    # overwrite a history that already has runs

Needs the debug build installed (`run-as` only works for debuggable apps). adb is taken from `$ADB`, the PATH or the
`sdk.dir` in `local.properties`.
"""
import argparse
import math
import os
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

PACKAGE = "com.example.gpstracker"
REPO = Path(__file__).resolve().parent.parent

# (activity, days ago, start, centre lat, lon, place, loop radius m, speed m/s, base elevation m, climb m, pauses)
RUNS = [
    ("JOGGING", 0, "06:40", 47.3769, 8.5417, "Zürich", 1100, 3.0, 410, 35, 0),
    ("CYCLING", 0, "12:15", 47.3500, 8.5600, "Zollikon", 3500, 7.2, 420, 90, 0),
    ("JOGGING", 1, "07:15", 47.3769, 8.5417, "Zürich", 900, 2.9, 410, 25, 0),
    ("CYCLING", 2, "17:40", 47.3500, 8.5600, "Zollikon", 4000, 7.5, 420, 120, 1),
    ("JOGGING", 4, "06:50", 47.3667, 8.5500, "Zürich", 1200, 3.1, 420, 40, 0),
    ("CYCLING", 4, "18:30", 47.3667, 8.5500, "Zürich", 4500, 7.8, 420, 150, 0),
    ("JOGGING", 6, "18:05", 47.4245, 8.5185, "Regensdorf", 800, 2.7, 440, 15, 1),
    ("CYCLING", 9, "09:30", 47.2100, 8.6000, "Wädenswil", 7000, 8.2, 410, 350, 0),
    ("JOGGING", 12, "07:00", 47.3769, 8.5417, "Zürich", 1500, 3.0, 410, 60, 0),
    ("CYCLING", 15, "14:20", 47.0502, 8.3093, "Luzern", 5000, 6.9, 435, 200, 2),
    ("JOGGING", 20, "08:10", 46.9480, 7.4474, "Bern", 1000, 2.8, 540, 30, 0),
    ("JOGGING", 27, "07:30", 47.3769, 8.5417, "Zürich", 1300, 3.0, 410, 45, 0),
    ("CYCLING", 34, "10:00", 47.1662, 8.5155, "Zug", 6000, 7.9, 425, 260, 1),
    ("JOGGING", 41, "18:20", 47.4245, 8.5185, "Regensdorf", 900, 2.8, 440, 20, 0),
    ("CYCLING", 55, "09:15", 46.8508, 9.5320, "Chur", 8000, 6.5, 590, 600, 1),
    ("JOGGING", 70, "07:05", 47.3769, 8.5417, "Zürich", 1600, 3.1, 410, 70, 0),
    ("CYCLING", 95, "15:40", 47.5596, 7.5886, "Basel", 5500, 8.0, 260, 80, 0),
    ("JOGGING", 130, "06:45", 46.2044, 6.1432, "Genève", 1200, 2.9, 375, 25, 0),
    ("CYCLING", 175, "08:30", 46.0037, 8.9511, "Lugano", 7000, 6.8, 275, 450, 2),
    ("JOGGING", 220, "17:50", 47.3769, 8.5417, "Zürich", 1000, 2.8, 410, 30, 0),
    ("CYCLING", 300, "11:00", 46.6863, 7.8632, "Interlaken", 6000, 7.0, 570, 300, 1),
    ("JOGGING", 380, "07:20", 47.0502, 8.3093, "Luzern", 1100, 2.9, 435, 35, 0),
]

FIX_INTERVAL_S = 5
PAUSE_MILLIS = 120_000


def haversine_m(a, b):
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp, dl = p2 - p1, math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * 6371000.0 * math.asin(math.sqrt(h))


def iso(millis):
    return datetime.fromtimestamp(millis / 1000, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def write_runs(out: Path, zone: ZoneInfo):
    """Writes `history.tsv` and `runs/*.gpx` to [out], with file names and dates in the device's time [zone]."""
    (out / "runs").mkdir(parents=True)
    today = datetime.now(zone).date()
    lines = []
    for i, (activity, days_ago, hhmm, lat, lon, place, radius, speed, ele0, climb, pauses) in enumerate(RUNS):
        hour, minute = map(int, hhmm.split(":"))
        start = datetime.combine(today - timedelta(days=days_ago), datetime.min.time(), zone).replace(hour=hour, minute=minute)
        # A run of today that would start in the future (or still be under way) starts earlier instead, still today.
        latest = datetime.now(zone) - timedelta(hours=3)
        if start > latest:
            start = max(latest, datetime.combine(today, datetime.min.time(), zone) + timedelta(minutes=5 * i))
        start_millis = t = int(start.timestamp() * 1000)

        # A wobbly loop around the centre, climbing to its far side and back.
        n = max(60, int(2 * math.pi * radius / speed / FIX_INTERVAL_S))
        points = []
        for k in range(n + 1):
            a = 2 * math.pi * k / n
            r = radius * (1 + 0.15 * math.sin(3 * a + i))
            points.append((
                lat + r * math.sin(a) / 111320,
                lon + r * (math.cos(a) - 1) / (111320 * math.cos(math.radians(lat))),
                ele0 + climb * (0.5 - 0.5 * math.cos(a)) + 3 * math.sin(7 * a),
            ))

        # Each pause starts a new segment after a gap, as the app records it.
        cuts = {round(n * (j + 1) / (pauses + 1)) for j in range(pauses)}
        segments, segment, distance, moving = [], [], 0.0, 0
        for k, p in enumerate(points):
            if k in cuts:
                segments.append(segment)
                segment = []
                t += PAUSE_MILLIS
            if segment:
                d = haversine_m(segment[-1], p)
                dt = int(d / (speed * (0.9 + 0.2 * math.sin(k / 9))) * 1000)
                distance += d
                moving += dt
                t += dt
            segment.append((*p, t))
        segments.append(segment)

        label = activity.capitalize()
        gpx = [
            '<?xml version="1.0" encoding="UTF-8"?>',
            '<gpx version="1.1" creator="Summit" xmlns="http://www.topografix.com/GPX/1/1" '
            'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
            'xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">',
            f"  <metadata><time>{iso(start_millis)}</time></metadata>",
            "  <trk>",
            f"    <name>{label} {iso(start_millis)}</name>",
            f"    <type>{'running' if activity == 'JOGGING' else 'cycling'}</type>",
        ]
        for s in segments:
            gpx.append("    <trkseg>")
            gpx += [f'      <trkpt lat="{la:.7f}" lon="{lo:.7f}"><ele>{el:.1f}</ele><time>{iso(tm)}</time></trkpt>' for la, lo, el, tm in s]
            gpx.append("    </trkseg>")
        gpx += ["  </trk>", "</gpx>"]
        name = f"{start:%Y-%m-%d_%H%M}_{activity.lower()}.gpx"
        (out / "runs" / name).write_text("\n".join(gpx) + "\n")

        first = segments[0][0]
        lines.append((start_millis, "\t".join(map(str, [activity, start_millis, moving, distance, first[0], first[1], place]))))
        print(f"{name}  {distance / 1000:6.2f} km  {moving / 60000:4.0f} min  {place}")

    (out / "history.tsv").write_text("".join(line + "\n" for _, line in sorted(lines)))


def find_adb():
    if os.environ.get("ADB"):
        return os.environ["ADB"]
    if shutil.which("adb"):
        return "adb"
    props = REPO / "local.properties"
    if props.exists():
        for line in props.read_text().splitlines():
            if line.startswith("sdk.dir="):
                adb = Path(line.split("=", 1)[1].replace("\\:", ":")) / "platform-tools" / "adb"
                if adb.exists():
                    return str(adb)
    sys.exit("adb not found: set $ADB, put it on the PATH or set sdk.dir in local.properties")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", help="device serial, as listed by `adb devices` (default: the only one)")
    parser.add_argument("--replace", action="store_true", help="overwrite the device's existing run history")
    args = parser.parse_args()

    adb = [find_adb()] + (["-s", args.serial] if args.serial else [])

    def shell(command, check=True):
        return subprocess.run(adb + ["shell", command], capture_output=True, text=True, check=check).stdout.strip()

    if shell(f"run-as {PACKAGE} ls files/history.tsv", check=False) == "files/history.tsv" and not args.replace:
        sys.exit("The device already has a run history; pass --replace to overwrite it (its runs are lost).")

    zone = ZoneInfo(shell("getprop persist.sys.timezone") or "UTC")
    remote = "/data/local/tmp/summit-seed"
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "seed"
        write_runs(out, zone)
        # The app reads its history once per process, so it is stopped first and started again afterwards.
        shell(f"am force-stop {PACKAGE}")
        shell(f"rm -rf {remote}")
        subprocess.run(adb + ["push", f"{out}/.", remote], capture_output=True, check=True)
    shell(
        f"run-as {PACKAGE} sh -c 'rm -rf files/runs && mkdir -p files/runs "
        f"&& cp {remote}/history.tsv files/ && cp {remote}/runs/*.gpx files/runs/'"
    )
    shell(f"rm -rf {remote}")
    shell(f"am start -n {PACKAGE}/.MainActivity")
    print(f"Seeded {len(RUNS)} runs and started the app.")


if __name__ == "__main__":
    main()
