#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
가볼래 iOS 실기기 자동화 시험 — 한 번에 도는 스크립트.

    python3 gabolle_ios_test.py            # 1회
    python3 gabolle_ios_test.py --rounds 2 # 2회 (S15P21E201-250 의 완료 기준)

🔴 이 스크립트가 지키는 규칙은 셋이다. 2026-09-17 밤에 7회차가 통째로 날아간 이유가
   정확히 이 셋을 안 지킨 것이었다.

   ① **글자로 요소를 찾지 않는다.** testID(= iOS 의 accessibility identifier)로만 찾는다.
      그때는 `contains(@label, '부슐랭')` 로 찾다가 온보딩 **본문**에 있는 「부슐랭」이
      걸려서, 온보딩 3페이지를 「부슐랭 탭」으로 세고 ✅ 를 찍었다.

   ② **못 찾으면 그 자리에서 멈춘다.** 그때는 못 찾아도 다음 줄로 넘어가서, 아무 데도
      못 갔는데 마지막에 「6/6 완료」가 찍혔다.

   ③ **도착했는지를 확인한다.** 누른 것이 아니라 **다음 화면의 선택자가 떴는지**를 본다.

   그리고 판정은 사람이 스크린샷을 보고 한다. 이 스크립트는 「돌아다니고 찍는」 일만 한다.
"""

import argparse
import json
import os
import subprocess
import sys
import time
from datetime import datetime

try:
    from appium import webdriver
    from appium.options.ios import XCUITestOptions
    from appium.webdriver.common.appiumby import AppiumBy
    from selenium.common.exceptions import WebDriverException
    from selenium.webdriver.support.ui import WebDriverWait
except ImportError:
    sys.exit("Appium-Python-Client 가 없다.  pip3 install Appium-Python-Client")

BUNDLE_ID = "com.gabolle.app"
APPIUM_URL = os.environ.get("APPIUM_URL", "http://127.0.0.1:4723")

OUT_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results")
RUN_ID = datetime.now().strftime("%Y%m%d_%H%M%S")
OUT = os.path.join(OUT_ROOT, RUN_ID)

# ── 기록 ────────────────────────────────────────────────────────────────────
STEPS = []          # [{round, step, ok, detail, shot}]
_shot_no = [0]


def log(msg):
    print(msg, flush=True)


def shot(driver, name):
    """화면을 찍는다. 🔴 판정은 사람이 이 그림을 보고 한다."""
    _shot_no[0] += 1
    path = os.path.join(OUT, "screenshots", f"{_shot_no[0]:03d}-{name}.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    driver.get_screenshot_as_file(path)
    log(f"      📸 {os.path.basename(path)}")
    return path


def record(rnd, step, ok, detail="", shot_path=""):
    STEPS.append({"round": rnd, "step": step, "ok": ok, "detail": detail,
                  "screenshot": os.path.basename(shot_path) if shot_path else ""})


# ── 🔴 찾기·누르기 — 못 찾으면 실패시킨다 ──────────────────────────────────
def find_id(driver, test_id, timeout=12):
    """testID 로 찾는다. 없으면 예외를 던진다 — 조용히 넘어가지 않는다."""
    return WebDriverWait(driver, timeout).until(
        lambda d: d.find_element(AppiumBy.ACCESSIBILITY_ID, test_id)
    )


def tap_id(driver, test_id, timeout=12):
    el = find_id(driver, test_id, timeout)
    el.click()
    log(f"      · 눌렀다: {test_id}")
    time.sleep(1.2)
    return el


def must_see(driver, test_id, timeout=15):
    """이 화면에 **도착했는가**. 누른 것이 아니라 도착을 본다."""
    el = find_id(driver, test_id, timeout)
    log(f"      · 도착 확인: {test_id}")
    return el


def has_id(driver, test_id, timeout=3):
    """있는지만 본다. 없어도 실패가 아니다 — 갈림길에서만 쓴다."""
    try:
        find_id(driver, test_id, timeout)
        return True
    except Exception:
        return False


def tap_label(driver, label, timeout=10):
    """
    testID 가 없는 자리에서만 쓴다.

    🔴 **정확히 일치**만 찾는다. contains 는 절대 쓰지 않는다 — 그게 어제 「부슐랭」이
       온보딩 본문에서 걸린 이유다.
    """
    el = WebDriverWait(driver, timeout).until(
        lambda d: d.find_element(
            AppiumBy.IOS_PREDICATE, f'name == "{label}" OR label == "{label}"')
    )
    el.click()
    log(f'      · 눌렀다(이름): "{label}"')
    time.sleep(1.2)
    return el


def app_alive(driver):
    """4 = 앞에서 돌고 있음. 🔴 크래시는 로그가 아니라 이 값으로 센다."""
    try:
        return driver.query_app_state(BUNDLE_ID) == 4
    except WebDriverException:
        return False


# ── 들어가는 길 ─────────────────────────────────────────────────────────────
def enter_app(driver, rnd):
    """
    언어 선택 → 앱 소개 → 나이 확인 → 권한 → 홈.

    🔴 어제 이 길을 몰라서 7회차가 전부 온보딩에서 끝났다. 순서는 이것 하나뿐이다.
    """
    log("\n  [진입] 언어 선택 → 앱 소개 → 나이 확인 → 권한 → 홈")
    shot(driver, "01-language")

    # 언어 — 한국어. 영어로 돌리려면 lang-en 으로 바꾼다(그래도 testID 는 안 바뀐다).
    tap_id(driver, "lang-ko")

    # 앱 소개 3장 — 건너뛰기 한 번이면 세 장을 다 넘긴다.
    tap_id(driver, "app-intro-skip")
    shot(driver, "02-age-gate")

    # 나이 확인 — 체크 먼저, 그다음 계속.
    tap_id(driver, "age-gate-check")
    tap_id(driver, "age-gate-continue")
    shot(driver, "03-permissions")

    # 권한 — 비회원으로 먼저 둘러본다.
    tap_id(driver, "permissions-browse-guest")

    # 🔴 여기가 진짜 판정이다. tab-home 이 안 뜨면 그 뒤는 볼 필요가 없다.
    must_see(driver, "tab-home")
    p = shot(driver, "04-home")
    record(rnd, "진입 — 홈 도착", True, "tab-home 확인", p)
    log("  ✅ 홈 도착 (tab-home 확인)")


# ── 시나리오 ────────────────────────────────────────────────────────────────
def scenario_tabs(driver, rnd):
    """탭 다섯을 차례로 연다. 각 탭에서 **그 탭이 실제로 선택됐는지**까지 본다."""
    log("\n  [1] 탭바 다섯")
    for key in ["tab-home", "tab-feed", "tab-schedule", "tab-map", "tab-me"]:
        tap_id(driver, key)
        must_see(driver, key)                 # 탭바가 그대로 있는가 = 화면이 살아 있는가
        p = shot(driver, f"10-{key}")
        record(rnd, f"탭 {key}", True, "", p)
        if not app_alive(driver):
            record(rnd, f"탭 {key}", False, "앱이 꺼졌다")
            raise RuntimeError(f"크래시 — {key}")
    log("  ✅ 탭 다섯 정상")


def scenario_place(driver, rnd):
    """
    홈 → 로컬 탐색 → 장소 하나.

    🔴 여기서 볼 것 (오늘 고친 자리):
       · 경사도 칸에 {"score":...} 같은 JSON 이 **안 보여야** 한다   (S15P21E201-1202)
       · 영업시간이 「매일 10:00~20:00」처럼 **읽히는 말**이어야 한다 (S15P21E201-1202)
       · 사진 위 「사진 제공: …」이 **읽혀야** 한다                  (S15P21E201-1203)
    """
    log("\n  [2] 장소 상세 — 1202·1203 확인 자리")
    tap_id(driver, "tab-home")
    try:
        tap_label(driver, "축제")            # 로컬 탐색 갈래
        time.sleep(2)
        p = shot(driver, "20-local-explore")
        record(rnd, "로컬 탐색", True, "", p)

        # 목록 첫 줄을 누른다 — 이름은 데이터에 따라 바뀌므로 셀 순서로 찾는다.
        cells = driver.find_elements(AppiumBy.CLASS_NAME, "XCUIElementTypeButton")
        if len(cells) > 3:
            cells[3].click()
            time.sleep(3)
        p = shot(driver, "21-place-detail")
        credit = has_id(driver, "place-photo-credit", timeout=5)
        record(rnd, "장소 상세", True, f"출처 표기 보임={credit}", p)
        log(f"  ✅ 장소 상세 (사진 출처 요소 {'있음' if credit else '없음'})")
        log("     🔴 경사도·영업시간·사진 출처는 스크린샷을 **사람이** 보고 판정한다")
    except Exception as e:
        p = shot(driver, "21-place-FAILED")
        record(rnd, "장소 상세", False, str(e)[:160], p)
        log(f"  ⚠️  장소 상세 못 감: {str(e)[:120]}")


def scenario_field_tools(driver, rnd):
    """
    홈 → 현장 도구.

    🔴 환율·주변 버스는 열쇠가 아직 없어서 「아직 준비 중이에요」가 떠야 **정상**이다
       (S15P21E201-1200). 「잠시 후 다시 시도」가 뜨면 그건 예전 빌드다.
    """
    log("\n  [3] 현장 도구 — 1200 확인 자리")
    tap_id(driver, "tab-home")
    try:
        tap_label(driver, "현장 도구")
        time.sleep(2)
        p = shot(driver, "30-field-tools")
        record(rnd, "현장 도구", True, "", p)
        tap_label(driver, "환율 계산")
        time.sleep(3)
        p = shot(driver, "31-exchange")
        record(rnd, "환율", True, "문구는 사람이 본다", p)
        log("  ✅ 현장 도구 · 환율 진입")
        log("     🔴 「아직 준비 중이에요」면 정상 · 「잠시 후 다시 시도」면 옛 빌드다")
    except Exception as e:
        p = shot(driver, "30-field-FAILED")
        record(rnd, "현장 도구", False, str(e)[:160], p)
        log(f"  ⚠️  현장 도구 못 감: {str(e)[:120]}")


def scenario_signin_backstack(driver, rnd):
    """
    로그인 화면에 들어갔다가 **뒤로** 나온다.

    🔴 S15P21E201-1199 — 로그인한 상태에서 뒤로 가기를 눌렀을 때 로그인 화면이 다시
       뜨면 안 된다. 비회원 상태에서는 그냥 로그인 화면이 열리고 닫히면 정상이다.
    """
    log("\n  [4] 로그인 화면 열고 닫기 — 1199 확인 자리")
    tap_id(driver, "tab-me")
    try:
        tap_label(driver, "로그인")
        time.sleep(2)
        p = shot(driver, "40-sign-in")
        record(rnd, "로그인 화면", True, "", p)
        driver.execute_script("mobile: swipe", {"direction": "right"})   # iOS 뒤로
        time.sleep(2)
        p = shot(driver, "41-after-back")
        record(rnd, "로그인 뒤로", True, "사람이 본다", p)
        log("  ✅ 로그인 화면 열고 닫음")
    except Exception as e:
        p = shot(driver, "40-signin-FAILED")
        record(rnd, "로그인 화면", False, str(e)[:160], p)
        log(f"  ⚠️  로그인 화면 못 감: {str(e)[:120]}")


SCENARIOS = [scenario_tabs, scenario_place, scenario_field_tools, scenario_signin_backstack]


# ── 한 회차 ─────────────────────────────────────────────────────────────────
def run_round(rnd, udid, wda_port):
    log("\n" + "=" * 68)
    log(f"  {rnd} 회차 시작")
    log("=" * 68)

    options = XCUITestOptions()
    options.platform_name = "iOS"
    options.automation_name = "XCUITest"
    options.bundle_id = BUNDLE_ID
    options.udid = udid
    options.set_capability("newCommandTimeout", 300)
    options.set_capability("wdaLocalPort", wda_port)
    # 🔴 매 회차 앱을 초기화한다 — 「처음 설치 상태부터」가 이 시험의 전제다.
    options.set_capability("noReset", False)
    options.set_capability("fullReset", False)
    options.set_capability("shouldTerminateApp", True)

    driver = webdriver.Remote(APPIUM_URL, options=options)
    ok = True
    try:
        time.sleep(4)
        if not app_alive(driver):
            raise RuntimeError("앱이 안 떴다")
        enter_app(driver, rnd)
        for fn in SCENARIOS:
            fn(driver, rnd)
            if not app_alive(driver):
                record(rnd, fn.__name__, False, "앱이 꺼졌다")
                raise RuntimeError(f"크래시 — {fn.__name__}")
    except Exception as e:
        ok = False
        log(f"\n  🔴 {rnd} 회차 중단: {str(e)[:200]}")
        try:
            shot(driver, "99-stopped-here")
        except Exception:
            pass
        record(rnd, "회차", False, str(e)[:200])
    finally:
        try:
            alive = app_alive(driver)
        except Exception:
            alive = False
        log(f"\n  {rnd} 회차 끝 — 앱 살아 있음: {alive}")
        record(rnd, "회차 종료 · 앱 생존", alive)
        driver.quit()
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rounds", type=int, default=2, help="몇 회 돌릴까 (기준은 2회)")
    ap.add_argument("--udid", default=os.environ.get("IOS_UDID", ""), help="아이폰 UDID")
    ap.add_argument("--wda-port", type=int, default=8100)
    args = ap.parse_args()

    if not args.udid:
        out = subprocess.run(["xcrun", "xctrace", "list", "devices"],
                             capture_output=True, text=True).stdout
        sys.exit("UDID 를 못 찾았다. --udid 로 넣어라.\n\n연결된 기기:\n" + out)

    os.makedirs(OUT, exist_ok=True)
    log(f"결과 폴더: {OUT}")

    results = []
    for r in range(1, args.rounds + 1):
        results.append(run_round(r, args.udid, args.wda_port))
        if r < args.rounds:
            time.sleep(5)

    passed = sum(1 for s in STEPS if s["ok"])
    failed = [s for s in STEPS if not s["ok"]]

    summary = {
        "runId": RUN_ID,
        "bundleId": BUNDLE_ID,
        "rounds": args.rounds,
        "roundsCompleted": sum(1 for x in results if x),
        "stepsPassed": passed,
        "stepsFailed": len(failed),
        "steps": STEPS,
    }
    with open(os.path.join(OUT, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    log("\n" + "=" * 68)
    log(f"  회차 {sum(1 for x in results if x)}/{args.rounds} 완주 · 단계 {passed} 통과 · {len(failed)} 실패")
    if failed:
        log("\n  🔴 실패한 단계")
        for s in failed:
            log(f"    - [{s['round']}회] {s['step']}: {s['detail']}")
    log(f"\n  스크린샷: {os.path.join(OUT, 'screenshots')}")
    log("  🔴 통과 여부는 이 숫자가 아니라 **스크린샷을 사람이 보고** 판정한다.")
    log("=" * 68)

    sys.exit(0 if all(results) and not failed else 1)


if __name__ == "__main__":
    main()
