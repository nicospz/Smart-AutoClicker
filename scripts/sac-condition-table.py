#!/usr/bin/env python3
"""Rolling-average SAC condition table with scenario catalog.

Usage:
  ./scripts/sac-condition-table.py                 # live from device
  adb logcat -d -s SacConditionProcessing | ./scripts/sac-condition-table.py --stdin
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field

TAG = "SacConditionProcessing"
TABLE_MARKER = "TABLE\t"

EVENT_HEADERS = ("#", "Type", "Event", "On", "Op", "#C")
COND_HEADERS = (
    "#",
    "Condition",
    "Event",
    "Ty",
    "N",
    "OK%",
    "Det%",
    "Cf",
    "ms",
    "Mt",
    "Ld",
)

# Max visible width per column (truncates long names).
COL_WIDTHS: dict[str, int] = {
    "#": 3,
    "Type": 4,
    "Event": 22,
    "On": 3,
    "Op": 4,
    "#C": 4,
    "Condition": 22,
    "Ty": 4,
    "N": 5,
    "OK%": 4,
    "Det%": 4,
    "Cf": 4,
    "ms": 5,
    "Mt": 5,
    "Ld": 5,
}


@dataclass
class ConditionStats:
    cond_id: str
    name: str
    event: str
    ctype: str
    samples: int = 0
    ok_sum: int = 0
    det_sum: int = 0
    det_samples: int = 0
    conf_sum: float = 0.0
    conf_samples: int = 0
    ms_sum: int = 0
    match_sum: int = 0
    match_samples: int = 0
    load_sum: int = 0
    load_samples: int = 0

    def record(self, ok: str, det: str, conf: str, ms: str, match: str, load: str) -> None:
        self.samples += 1
        if ok == "Y":
            self.ok_sum += 1
        if det in ("Y", "N"):
            self.det_samples += 1
            if det == "Y":
                self.det_sum += 1
        conf_value = parse_number(conf)
        if conf_value is not None:
            self.conf_sum += conf_value
            self.conf_samples += 1
        ms_value = parse_number(ms)
        if ms_value is not None:
            self.ms_sum += ms_value
        match_value = parse_number(match)
        if match_value is not None:
            self.match_sum += match_value
            self.match_samples += 1
        load_value = parse_number(load)
        if load_value is not None:
            self.load_sum += load_value
            self.load_samples += 1

    def avg(self, total: int | float, count: int) -> str:
        if count <= 0:
            return "0"
        return f"{total / count:.0f}"

    def row(self, order: str) -> list[str]:
        if self.samples == 0:
            return [order, self.name, self.event, self.ctype, "0", "0", "0", "0", "0", "0", "0"]
        return [
            order,
            self.name,
            self.event,
            self.ctype,
            str(self.samples),
            self.avg(self.ok_sum * 100, self.samples),
            self.avg(self.det_sum * 100, self.det_samples),
            self.avg(self.conf_sum, self.conf_samples),
            self.avg(self.ms_sum, self.samples),
            self.avg(self.match_sum, self.match_samples),
            self.avg(self.load_sum, self.load_samples),
        ]


@dataclass
class EventInfo:
    order: str
    etype: str
    name: str
    enabled: str
    operator: str
    condition_count: str

    def row(self) -> list[str]:
        return [self.order, self.etype, self.name, self.enabled, self.operator, self.condition_count]


@dataclass
class ConditionSlot:
    order: str
    cond_id: str
    event: str
    name: str
    ctype: str


@dataclass
class Dashboard:
    scenario_name: str = "?"
    events: list[EventInfo] = field(default_factory=list)
    condition_slots: list[ConditionSlot] = field(default_factory=list)
    stats_by_id: dict[str, ConditionStats] = field(default_factory=dict)
    cycles: int = 0
    last_event: str = "?"
    last_operator: str = "?"
    last_fulfilled: str = "?"
    last_batch_ms: str = "0"

    def reset_catalog(self, scenario_name: str) -> None:
        self.scenario_name = scenario_name
        self.events.clear()
        self.condition_slots.clear()
        self.stats_by_id.clear()
        self.cycles = 0
        self.last_event = "?"
        self.last_operator = "?"
        self.last_fulfilled = "?"
        self.last_batch_ms = "0"

    def add_event(self, cols: list[str]) -> None:
        self.events.append(
            EventInfo(
                order=cols[1],
                etype=cols[3],
                name=cols[4],
                enabled=cols[5],
                operator=cols[6],
                condition_count=cols[7],
            ),
        )

    def add_condition_slot(self, cols: list[str]) -> None:
        cond_id = cols[2]
        if any(slot.cond_id == cond_id for slot in self.condition_slots):
            return
        slot = ConditionSlot(
            order=cols[1],
            cond_id=cond_id,
            event=cols[3],
            name=cols[4],
            ctype=cols[5],
        )
        self.condition_slots.append(slot)
        if cond_id not in self.stats_by_id:
            self.stats_by_id[cond_id] = ConditionStats(
                cond_id=cond_id,
                name=slot.name,
                event=slot.event,
                ctype=slot.ctype,
            )

    def ensure_condition_slot(self, cond_id: str, event: str, name: str, ctype: str) -> None:
        if any(slot.cond_id == cond_id for slot in self.condition_slots):
            return
        order = str(len(self.condition_slots) + 1)
        self.add_condition_slot(["COND_DEF", order, cond_id, event, name, ctype])

    def stats_for(self, cond_id: str) -> ConditionStats:
        stats = self.stats_by_id.get(cond_id)
        if stats is not None:
            return stats
        slot = next((s for s in self.condition_slots if s.cond_id == cond_id), None)
        if slot is None:
            return ConditionStats(cond_id=cond_id, name="?", event="?", ctype="?")
        stats = ConditionStats(
            cond_id=cond_id,
            name=slot.name,
            event=slot.event,
            ctype=slot.ctype,
        )
        self.stats_by_id[cond_id] = stats
        return stats

    def record_condition(self, cols: list[str]) -> None:
        cond_id = cols[1]
        event = cols[2]
        name = cols[3]
        ctype = cols[4]
        self.ensure_condition_slot(cond_id, event, name, ctype)
        stats = self.stats_for(cond_id)
        stats.name = name
        stats.event = event
        stats.ctype = ctype
        self.stats_by_id[cond_id] = stats
        stats.record(cols[5], cols[6], cols[7], cols[8], cols[9], cols[10])

    def begin_cycle(self, event: str, operator: str) -> None:
        self.cycles += 1
        self.last_event = shorten_label(event, 36)
        self.last_operator = operator

    def end_cycle(self, fulfilled: str, duration_ms: str) -> None:
        self.last_fulfilled = fulfilled
        self.last_batch_ms = duration_ms

    def condition_rows(self) -> list[list[str]]:
        if self.condition_slots:
            return [
                self.stats_for(slot.cond_id).row(slot.order)
                for slot in self.condition_slots
            ]
        return [stats.row("?") for stats in sorted(self.stats_by_id.values(), key=lambda s: s.name.lower())]

    def summary(self) -> str:
        return (
            f"scenario={shorten_label(self.scenario_name, 24)}  "
            f"cycles={self.cycles}  events={len(self.events)}  "
            f"conds={len(self.condition_slots)}  "
            f"last={self.last_event} {self.last_operator} {self.last_fulfilled} {self.last_batch_ms}ms"
        )


def shorten_label(value: str, max_len: int) -> str:
    value = value.strip()
    if len(value) <= max_len:
        return value
    return value[: max_len - 1] + "…"


def parse_number(value: str) -> float | None:
    value = value.strip()
    if not value or value == "-":
        return None
    try:
        return float(value)
    except ValueError:
        return None


def extract_message(line: str) -> str | None:
    if TAG not in line:
        return None
    match = re.search(rf"{re.escape(TAG)}\([^)]*\):\s*(.*)$", line)
    if match:
        return match.group(1).strip()
    if f"{TAG}:" in line:
        return line.split(f"{TAG}:", 1)[1].strip()
    if line.strip().startswith(TAG):
        return line.split(TAG, 1)[1].lstrip(": ").strip()
    return None


def parse_table_message(message: str) -> list[str] | None:
    if not message.startswith(TABLE_MARKER):
        return None
    return message[len(TABLE_MARKER) :].split("\t")


def clip_cell(header: str, value: str) -> str:
    width = COL_WIDTHS.get(header, len(value))
    if len(value) <= width:
        return value
    if width <= 1:
        return value[:width]
    return value[: width - 1] + "…"


def render_table(headers: tuple[str, ...], rows: list[list[str]], empty_text: str) -> list[str]:
    if not rows:
        return [empty_text]
    widths = [COL_WIDTHS.get(h, len(h)) for h in headers]
    fmt = " ".join(f"{{:{w}}}" for w in widths)
    lines = [
        fmt.format(*[clip_cell(h, h) for h in headers]),
        " ".join("-" * w for w in widths),
    ]
    for row in rows:
        padded = row + [""] * (len(headers) - len(row))
        cells = [clip_cell(headers[i], padded[i]) for i in range(len(headers))]
        lines.append(fmt.format(*cells))
    return lines


def render_dashboard(dashboard: Dashboard) -> list[str]:
    lines = [dashboard.summary(), ""]
    if dashboard.events:
        lines.append("Events")
        lines.extend(render_table(EVENT_HEADERS, [event.row() for event in dashboard.events], "(no events)"))
        lines.append("")
    lines.append("Conditions (rolling averages)")
    lines.extend(render_table(COND_HEADERS, dashboard.condition_rows(), "(waiting for conditions…)"))
    return lines


class LiveRenderer:
    def __init__(self, interval_s: float) -> None:
        self.interval_s = interval_s
        self.last_draw = 0.0
        self.initialized = False

    def maybe_draw(self, dashboard: Dashboard, force: bool = False) -> None:
        now = time.monotonic()
        if not force and now - self.last_draw < self.interval_s:
            return
        self.draw(dashboard, now)

    def draw(self, dashboard: Dashboard, now: float | None = None) -> None:
        if now is None:
            now = time.monotonic()
        lines = render_dashboard(dashboard)
        if not self.initialized:
            sys.stdout.write("\033[?25l")
            self.initialized = True
        sys.stdout.write("\033[2J\033[H")
        for line in lines:
            sys.stdout.write(line)
            sys.stdout.write("\033[K\n")
        sys.stdout.flush()
        self.last_draw = now

    def close(self) -> None:
        if self.initialized:
            sys.stdout.write("\033[?25h\033[K")
            sys.stdout.flush()


def ingest_line(dashboard: Dashboard, line: str) -> bool:
    message = extract_message(line)
    if not message:
        return False
    cols = parse_table_message(message)
    if not cols:
        return False
    if cols[0] == "SCENARIO" and cols[1] == "start":
        dashboard.reset_catalog(cols[2])
        return True
    if cols[0] == "EVENT" and len(cols) >= 8:
        dashboard.add_event(cols)
        return True
    if cols[0] == "COND_DEF" and len(cols) >= 6:
        dashboard.add_condition_slot(cols)
        return True
    if cols[0] == "BATCH" and cols[1] == "start":
        dashboard.begin_cycle(cols[2], cols[3])
        return True
    if cols[0] == "COND" and len(cols) >= 11:
        dashboard.record_condition(cols)
        return True
    if cols[0] == "BATCH" and cols[1] == "end":
        dashboard.end_cycle(cols[3], cols[4])
        return True
    return False


def clear_device_logcat() -> bool:
    try:
        result = subprocess.run(
            ["adb", "logcat", "-c"],
            capture_output=True,
            text=True,
            timeout=10,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False
    return result.returncode == 0


def process_lines(lines, live: bool, interval_s: float) -> int:
    dashboard = Dashboard()
    renderer = LiveRenderer(interval_s) if live else None
    updates = 0
    for line in lines:
        if not ingest_line(dashboard, line):
            continue
        updates += 1
        if renderer is not None:
            renderer.maybe_draw(dashboard)
    if renderer is not None:
        renderer.draw(dashboard)
        renderer.close()
    elif updates > 0:
        for line in render_dashboard(dashboard):
            print(line)
    return updates


def main() -> int:
    parser = argparse.ArgumentParser(description="Rolling-average SAC condition table")
    parser.add_argument("--stdin", action="store_true", help="Read log lines from stdin instead of adb logcat")
    parser.add_argument(
        "--no-clear",
        action="store_true",
        help="Do not run adb logcat -c before watching",
    )
    parser.add_argument(
        "--interval",
        type=float,
        default=1.0,
        help="Seconds between live table refreshes (default: 1.0)",
    )
    args = parser.parse_args()

    if args.stdin:
        count = process_lines(sys.stdin, live=False, interval_s=args.interval)
    else:
        if not args.no_clear:
            if clear_device_logcat():
                print("Cleared device logcat.", file=sys.stderr)
            else:
                print("Warning: could not clear logcat (adb missing or no device).", file=sys.stderr)
        try:
            proc = subprocess.Popen(
                ["adb", "logcat", "-v", "brief", "-s", f"{TAG}:I"],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                bufsize=1,
            )
        except FileNotFoundError:
            print("adb not found; pipe logcat in with --stdin", file=sys.stderr)
            return 1
        assert proc.stdout is not None
        print(
            f"Watching {TAG}… refresh every {args.interval:.1f}s (Ctrl+C to stop)",
            file=sys.stderr,
        )
        dashboard = Dashboard()
        renderer = LiveRenderer(args.interval)
        try:
            count = 0
            for line in proc.stdout:
                if ingest_line(dashboard, line):
                    count += 1
                    renderer.maybe_draw(dashboard)
        except KeyboardInterrupt:
            proc.terminate()
            renderer.draw(dashboard)
            renderer.close()
            print("\nStopped.", file=sys.stderr)
            return 0
        renderer.draw(dashboard)
        renderer.close()

    if count == 0:
        print("No TABLE rows found. Start a smart scenario while this script is attached.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
