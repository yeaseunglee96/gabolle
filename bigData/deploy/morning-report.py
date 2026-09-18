#!/usr/bin/env python3
"""BIMS 수집 아침 점검 — 매일 05:00(KST) MatterMost 로 보낸다 (S15P21E201-633).

무엇을 보나
  1. 러너 6개가 살아 있는가
  2. 어제 실제로 얼마나 모았는가 (컨테이너가 살아 있어도 안 모을 수 있다)
  3. 디스크가 남았는가

🔴 **"떠 있다" 와 "모으고 있다" 는 다르다.** 인증키가 막히면 컨테이너는 멀쩡히 Up 인
   채로 오류만 세면서 아무것도 안 쌓는다. 그래서 상태만 보지 않고 **어제 쌓인 줄 수를
   기대치와 견준다.** 이 점검의 존재 이유가 그것이다.

🔴 **왜 컨테이너가 아니라 호스트에서 도는가.** 점검을 7번째 컨테이너로 만들면 컴포즈가
   통째로 죽는 날 알림도 같이 죽는다 — 가장 알아야 할 순간에 조용해진다. 감시자는
   감시 대상과 운명을 같이하면 안 된다.

🔴 **왜 파이썬인가.** 이 저장소는 Node 로 되어 있지만 **서버 호스트에는 Node 가 없다**
   (수집기는 컨테이너 안에서 돈다). 점검까지 컨테이너로 만들면 위의 이유로 안 되고,
   호스트에 Node 를 새로 까는 것은 이 하나를 위해 서버 상태를 바꾸는 일이다.
   python3 는 우분투에 이미 있고 표준 라이브러리만으로 충분하다.

실행
  python3 morning-report.py                  # 보낸다
  python3 morning-report.py --dry-run        # 보내지 않고 화면에만
"""
import json
import os
import re
import subprocess
import sys
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

# 🔴 S15P21E201-891 — 9/13에 서버 디스크가 62%까지 찼다가 손으로 치워 32%로
# 내렸다. 이 보고서는 그 전부터 디스크 숫자를 찍고 있었지만 경고는 없었다 —
# 숫자만 있고 문턱이 없어서 62%가 될 때까지 아무도 안 봤다. 70을 넘으면
# 머리말도 🔴로 바뀐다.
DISK_WARN_PERCENT = 70

KST = timezone(timedelta(hours=9))
HERE = Path(__file__).resolve().parent
DATA = HERE / "data" / "raw" / "transit"
ASSIGN = HERE.parent / "config" / "bims-assign.json"

# compose.yaml 의 서비스 이름과 같아야 한다.
CONTAINERS = {
    "bims-rleaderjoon": "rleaderjoon",
    "bims-masdf13": "masdf13",
    "bims-yeaseung": "yeaseung.lee96",
    "bims-jinmiri": "jinmiri",
    "bims-mojinseong": "모진성",
    "bims-kojihyeok": "고지혁",
}

DRY = "--dry-run" in sys.argv


def sh(*args):
    try:
        return subprocess.run(args, capture_output=True, text=True, timeout=60).stdout.strip()
    except Exception:
        return ""


def expected_calls_per_key():
    """배정표에서 기대치를 읽는다. 🔴 여기 숫자를 적어 두지 않는다 — 배정이 바뀌면
    이 점검이 조용히 틀린 기준으로 판정하게 된다."""
    try:
        plan = json.loads(ASSIGN.read_text(encoding="utf-8"))
        per_route = plan["상수"]["노선당 하루 호출"]
        return {a["담당"]: len(a.get("노선", [])) * per_route for a in plan["배정"]}
    except Exception:
        return {}


def was_running_yesterday(container, now):
    """이 컨테이너가 어제가 끝나기 전부터 떠 있었나.

    어제 자정(KST) 이전에 시작했다면 어제치가 있어야 마땅하다. 오늘 새로 올린 것과
    어제부터 있었는데 아무것도 못 모은 것을 가르는 자리다 — 뒤엣것만 사고다.
    """
    started = sh("docker", "inspect", "-f", "{{.State.StartedAt}}", container)
    if not started:
        return False
    try:
        # 도커는 나노초까지 준다. 파이썬이 못 읽으므로 마이크로초까지만 남긴다.
        s = started.replace("Z", "+00:00")
        if "." in s:
            base, rest = s.split(".", 1)
            frac, tz = rest[:6], rest[rest.find("+") if "+" in rest else len(rest):]
            s = f"{base}.{frac}{tz or '+00:00'}"
        return datetime.fromisoformat(s).astimezone(KST) < now.replace(
            hour=0, minute=0, second=0, microsecond=0)
    except Exception:
        return False


def human(n):
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024:
            return f"{n:.0f}{unit}"
        n /= 1024
    return f"{n:.0f}PB"


def main():
    now = datetime.now(KST)

    # --date 로 지난 날을 다시 볼 수 있다. 알림이 안 갔거나 뒤늦게 확인할 때 쓴다.
    if "--date" in sys.argv:
        yday = sys.argv[sys.argv.index("--date") + 1]
    else:
        yday = (now - timedelta(days=1)).strftime("%Y-%m-%d")

    running = {name for name in sh("docker", "ps", "--format", "{{.Names}}").splitlines()}
    expected = expected_calls_per_key()

    rows, alerts = [], []
    total_calls = total_bytes = total_routes = 0
    alive = 0

    for container, instance in CONTAINERS.items():
        up = container in running
        alive += up

        f = DATA / f"bims-{yday}-{instance}.ndjson"
        calls = size = routes = 0
        if f.exists():
            size = f.stat().st_size
            seen = set()
            with f.open(encoding="utf-8", errors="replace") as fh:
                for line in fh:
                    if not line.strip():
                        continue
                    calls += 1
                    # routeId 만 뽑는다. 줄 하나가 수십 KB 라 통째로 파싱하면 느리다.
                    i = line.find('"routeId":"')
                    if i >= 0:
                        seen.add(line[i + 11 : line.find('"', i + 11)])
            routes = len(seen)

        exp = expected.get(instance, 0)
        pct = (calls / exp * 100) if exp else None

        # 🔴 "어제 데이터가 없다" 를 판정하려면 **어제 이 컨테이너가 있었는지**를 알아야 한다.
        #
        #    처음에는 "수집이 0이면 경고하지 않는다" 로 두었다 — 오늘 새로 올린 컨테이너를
        #    장애로 보고하지 않으려는 뜻이었다. 그런데 **인증키가 완전히 막히면 정확히 0** 이다.
        #    즉 가장 알아야 할 상황에서 입을 다무는 조건이었다.
        #
        #    0 을 무시하는 대신 시작 시각을 본다. 어제가 끝나기 전부터 떠 있었다면 어제치가
        #    있어야 마땅하고, 없으면 그것이 바로 사고다.
        existed = was_running_yesterday(container, now)

        if not up:
            alerts.append(f"`{container}` 가 **떠 있지 않습니다**")
        elif not existed:
            pass  # 어제 없던 컨테이너. 어제치가 없는 게 당연하다
        elif calls == 0:
            alerts.append(f"`{container}` 어제 **한 건도 못 모았습니다** — 인증키가 막혔는지 보십시오")
        elif exp and pct is not None and pct < 50:
            alerts.append(f"`{container}` 어제 수집이 기대의 **{pct:.0f}%** 뿐입니다 (키가 막혔을 수 있습니다)")

        rows.append((instance, up, calls, exp, pct, routes, size))
        total_calls += calls
        total_bytes += size
        total_routes += routes

    # 오류는 컨테이너 로그에서 센다. 수집기는 API 오류 응답을 성공으로 세지 않고
    # ⚠ 로 남긴다 — HTTP 200 에 오류 XML 이 오는 경우가 그렇다.
    errors = 0
    for container in CONTAINERS:
        out = sh("docker", "logs", "--since", "24h", container)
        errors += sum(1 for line in out.splitlines() if "⚠" in line or "🔴" in line)

    disk = sh("df", "-h", "--output=used,size,pcent", str(HERE)).splitlines()
    disk_line = disk[-1].strip() if len(disk) > 1 else "?"
    disk_pcent_match = re.search(r"(\d+)%", disk_line)
    if disk_pcent_match and int(disk_pcent_match.group(1)) >= DISK_WARN_PERCENT:
        alerts.append(f"디스크가 **{disk_pcent_match.group(1)}%** 로 {DISK_WARN_PERCENT}% 를 넘었습니다 — 정리가 필요합니다 (S15P21E201-891)")

    # 🔴 머리말과 본문이 어긋나면 안 된다. 본문에 "한 건도 안 모였다" 가 있는데 머리말이
    #    ✅ 이면, 목록에서 제목만 보는 사람은 정상이라고 읽는다 — 알림이 거짓말이 된다.
    any_collected = total_calls > 0
    healthy = alive == len(CONTAINERS) and not alerts and any_collected
    head = "✅" if healthy else "🔴"
    lines = [
        f"#### {head} BIMS 수집 아침 점검 — {now:%Y-%m-%d %H:%M} (KST)",
        "",
        f"**러너** {alive}/{len(CONTAINERS)} 정상",
    ]

    if alerts:
        lines += ["", "**🔴 봐야 할 것**"] + [f"- {a}" for a in alerts]

    lines += [
        "",
        f"**어제({yday}) 모은 것**",
        "",
        "| 키 | 호출 | 기대 | 노선 | 크기 |",
        "|---|---:|---:|---:|---:|",
    ]
    for instance, up, calls, exp, pct, routes, size in rows:
        mark = "" if up else " ⛔"
        exp_txt = f"{exp:,} ({pct:.0f}%)" if exp and pct is not None else "-"
        lines.append(f"| {instance}{mark} | {calls:,} | {exp_txt} | {routes} | {human(size)} |")
    lines.append(f"| **합계** | **{total_calls:,}** | | **{total_routes}** | **{human(total_bytes)}** |")

    lines += [
        "",
        f"**오류(24시간)** {errors}건",
        f"**디스크** {disk_line}",
    ]

    if total_calls == 0:
        lines += ["", "🔴 **어제 하루 동안 한 건도 안 모였습니다.** 실시간 데이터는 소급 수집이 안 됩니다."]

    text = "\n".join(lines)
    print(text)

    hook = os.environ.get("MATTERMOST_WEBHOOK", "").strip()
    if DRY:
        print("\n[--dry-run] 보내지 않았습니다.")
        return 0
    if not hook:
        print("\n🔴 MATTERMOST_WEBHOOK 이 비어 있어 보내지 못했습니다.", file=sys.stderr)
        return 2

    payload = json.dumps({"text": text, "username": "BIMS", "icon_emoji": ":bus:"}).encode()
    req = urllib.request.Request(hook, data=payload, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as res:
            print(f"\n보냄: HTTP {res.status}")
        return 0
    except Exception as e:
        print(f"\n🔴 보내지 못했습니다: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
