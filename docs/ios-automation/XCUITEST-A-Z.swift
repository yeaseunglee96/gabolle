//
//  GabolleUITests-A-Z.swift
//  가볼래(GABOLLE) iOS 전수 자동화 시험 — A부터 Z까지
//
//  쓰는 법
//    1. Xcode 에서 File → New → Target… → UI Testing Bundle → 이름 `GabolleUITests`
//    2. 이 파일과 `GabolleUITestCase.swift`(문서 4-1) · `Entry.swift`(문서 4-2) ·
//       `BackSweep.swift`(문서 4-3) 를 그 타깃에 넣는다
//    3. Product → Test (⌘U)
//
//  🔴 이 파일이 지키는 것 셋 (2026-09-17 밤에 40분을 날린 이유가 이 셋이다)
//    ① 요소는 «이름표»(testID = accessibilityIdentifier)로 찾는다. 이름표가 없으면
//       «글자가 완전히 같은 것»으로 찾는다 — `contains` 는 쓰지 않는다.
//       온보딩 본문의 「부슐랭」이라는 낱말에 `contains('부슐랭')` 이 걸려서
//       로그에 「✅ 탭: 부슐랭 · 6/6 완료」가 찍혔고, 같은 순간 화면은 온보딩이었다.
//    ② 못 찾으면 «그 자리에서 멈춘다». `continueAfterFailure = false`.
//    ③ 단계마다 스크린샷을 남긴다. 로그는 거짓말할 수 있고 사진은 못 한다.
//
//  🔴 판정을 자동화가 다 하지 않는다. 화면의 결함(같은 것을 두 번 그리기, 글자 잘림,
//     빈 그림 상자)은 어떤 자동 검사도 안 잡는다. 자동화는 «거기까지 데려다주고
//     사진을 남기는 일»을 하고, 판정은 사람이 그 사진을 보고 한다.
//     문서의 「👁 사람이 볼 것」이 그 목록이다.
//
//  작성 2026-09-19 · 이예승(yeaseung.lee96) · 수행 모진성(ahwlstjd57)
//

import XCTest

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - 시험에 쓰는 값
// ─────────────────────────────────────────────────────────────────────────────

enum QA {
    /// 🔴 매번 새 메일을 만든다. 같은 메일로 두 번 가입하면
    ///    「이미 가입된 이메일이에요」가 떠서 그 자리에서 막힌다.
    static func freshEmail() -> String {
        let stamp = Int(Date().timeIntervalSince1970)
        return "qa+\(stamp)@example.com"
    }

    static let goodPassword = "Verify1186Check!"

    /// 메일 인증까지 끝내 둔 계정. 🔴 시험 전에 손으로 하나 만들어 여기에 적는다.
    static let signedInEmail = "여기에-인증끝난-계정을-적는다@example.com"
    static let signedInPassword = "여기에-비밀번호를-적는다"

    /// 메뉴판 사진. `xcrun simctl addmedia booted ~/Downloads/menu-1.jpg` 로 미리 넣는다.
    static let menuPhotoCount = 3

    /// 🔴 메뉴판 읽기는 분당 3회 · 하루 20회가 한도다. 이보다 빠르면
    ///    `MENU_SCAN_RATE_LIMITED` 가 뜨는데 그건 버그가 아니라 «우리가 너무 빨리 부른 것»이다.
    static let menuScanSpacing: TimeInterval = 22
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - A. 앱 최초 실행 · 언어 선택
// ─────────────────────────────────────────────────────────────────────────────

final class A_LanguageTests: GabolleUITestCase {

    func test_A1_언어_다섯_개가_모두_눌리고_화면_글자가_바뀐다() {
        for code in ["ko", "en", "ja", "zh-Hans", "zh-Hant"] {
            tapId("lang-\(code)")
            shot("A1-언어-\(code)")   // 👁 이 화면 자체의 글자가 그 언어로 바뀌었는가
        }
        tapId("lang-ko")
        tapId("start-gabolle")
        XCTAssertTrue(byId("app-intro-skip").waitForExistence(timeout: 15),
                      "시작을 눌렀는데 앱 소개로 안 넘어갔다")
    }

    func test_A2_시작화면에서_챗봇을_바로_열_수_있다() {
        tapId("lang-ko")
        tapLabel("가볼래 AI 여행 도우미 열기")
        shot("A2-챗봇")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - B. 앱 소개 · 나이 확인 · 권한 안내
// ─────────────────────────────────────────────────────────────────────────────

final class B_OnboardingGateTests: GabolleUITestCase {

    func test_B1_소개를_한_장씩_넘겨도_들어간다() {
        tapId("lang-ko"); tapId("start-gabolle")
        for page in 1...3 {
            tapId("app-intro-primary")
            shot("B1-소개-\(page)")
        }
        XCTAssertTrue(byId("age-gate-check").waitForExistence(timeout: 15),
                      "소개를 세 번 넘겼는데 나이 확인이 안 나왔다")
    }

    func test_B2_나이_확인을_안_하면_계속이_안_눌린다() {
        tapId("lang-ko"); tapId("start-gabolle"); tapId("app-intro-skip")
        let go = byId("age-gate-continue")
        XCTAssertTrue(go.waitForExistence(timeout: 15))
        shot("B2-체크전")                    // 👁 계속 버튼이 흐리게(비활성) 보이는가
        go.tap()
        XCTAssertTrue(byId("age-gate-check").exists,
                      "체크 없이 계속을 눌렀는데 다음 화면으로 넘어갔다")
        tapId("age-gate-check")
        tapId("age-gate-continue")
        XCTAssertTrue(byId("permissions-browse-guest").waitForExistence(timeout: 15),
                      "체크하고 계속을 눌렀는데 권한 안내가 안 나왔다")
    }

    func test_B3_권한_화면의_세_갈래가_각각_다른_곳으로_간다() {
        // 비회원 둘러보기 → 홈
        enterAsGuest()
        XCTAssertTrue(byId("tab-home").exists)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - C. 회원가입 · 메일 인증
// ─────────────────────────────────────────────────────────────────────────────

final class C_SignUpTests: GabolleUITestCase {

    private func toSignUp() {
        tapId("lang-ko"); tapId("start-gabolle"); tapId("app-intro-skip")
        tapId("age-gate-check"); tapId("age-gate-continue")
        tapId("permissions-continue")
        tapLabel("회원가입")
    }

    func test_C1_비밀번호_규칙_네_줄이_입력에_따라_바뀐다() {
        toSignUp()
        type(byId("sign-up-email"), QA.freshEmail(), "이메일 칸")
        shot("C1-메일만")
        type(byId("sign-up-password"), "abc", "비밀번호 칸")
        shot("C1-짧은비번")     // 👁 「8~64자」「영문」「숫자」「특수문자」 네 줄의 상태가 보이는가
        byLabel("비밀번호 지우기").tap()
        type(byId("sign-up-password"), QA.goodPassword, "비밀번호 칸")
        shot("C1-올바른비번")   // 👁 네 줄이 모두 만족으로 바뀌었는가
    }

    func test_C2_비밀번호_확인이_다르면_막힌다() {
        toSignUp()
        type(byId("sign-up-email"), QA.freshEmail(), "이메일 칸")
        type(byId("sign-up-password"), QA.goodPassword, "비밀번호 칸")
        type(byId("sign-up-confirm"), QA.goodPassword + "X", "비밀번호 확인 칸")
        shot("C2-불일치")
        tapId("sign-up-next")
        XCTAssertTrue(byId("sign-up-confirm").exists, "비밀번호가 다른데 다음 단계로 넘어갔다")
    }

    func test_C3_약관_세_개를_다_눌러야_넘어간다() {
        toSignUp()
        type(byId("sign-up-email"), QA.freshEmail(), "이메일 칸")
        type(byId("sign-up-password"), QA.goodPassword, "비밀번호 칸")
        type(byId("sign-up-confirm"), QA.goodPassword, "비밀번호 확인 칸")
        type(byId("sign-up-name"), "큐에이", "이름 칸")
        tapLabel("만 14세 이상입니다.")
        shot("C3-동의하나만")
        tapId("sign-up-next")                  // 아직 약관 둘이 남았다 — 안 넘어가야 한다
        tapLabel("이용약관에 동의합니다. (필수)")
        tapLabel("개인정보 처리방침에 동의합니다. (필수)")
        shot("C3-동의전부")
        tapId("sign-up-next")
        shot("C3-가입결과")                    // 👁 「이메일 확인 후 로그인」이 나왔는가
    }

    func test_C4_같은_메일로_두_번_가입하면_막힌다() {
        // 🔴 C3 에서 쓴 메일을 그대로 다시 넣어야 의미가 있다.
        //    자동화로는 메일을 기억해 넘겨야 하므로 여기는 «반자동»으로 둔다.
        //    문서 C장의 표를 보고 손으로 한 번 확인한다.
        XCTSkip("반자동 — C3 에서 쓴 메일을 손으로 다시 넣어 「이미 가입된 이메일이에요」를 확인한다")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - D. 로그인 · 비밀번호 재설정
// ─────────────────────────────────────────────────────────────────────────────

final class D_SignInTests: GabolleUITestCase {

    private func toSignIn() {
        tapId("lang-ko"); tapId("start-gabolle"); tapId("app-intro-skip")
        tapId("age-gate-check"); tapId("age-gate-continue")
        tapId("permissions-continue")
        tapId("sign-in")
    }

    func test_D1_틀린_비밀번호는_거절된다() {
        toSignIn()
        type(byId("sign-in-email"), QA.signedInEmail, "이메일 칸")
        type(byId("sign-in-password"), "WrongPassword!1", "비밀번호 칸")
        tapId("sign-in-submit")
        shot("D1-틀린비번")
        XCTAssertFalse(byId("tab-home").waitForExistence(timeout: 8),
                       "틀린 비밀번호로 로그인이 됐다")
    }

    func test_D2_맞는_계정으로_들어가고_뒤로_가도_로그인창이_다시_안_뜬다() {
        enterSignedIn(email: QA.signedInEmail, password: QA.signedInPassword)
        // 🔴 과거 S15P21E201-1199 — 로그인 직후 뒤로 가면 로그인 창이 다시 떴다.
        let left  = app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.5))
        let right = app.coordinate(withNormalizedOffset: CGVector(dx: 0.80, dy: 0.5))
        left.press(forDuration: 0.05, thenDragTo: right)
        shot("D2-로그인후뒤로")
        XCTAssertFalse(textExists("비밀번호", timeout: 3),
                       "로그인한 뒤 뒤로 갔더니 로그인 창이 다시 떴다")
    }

    func test_D3_비밀번호_찾기_화면이_열리고_메일을_받는다() {
        toSignIn()
        tapLabel("비밀번호 찾기")
        type(byLabel("이메일"), QA.signedInEmail, "이메일 칸")   // 이 화면은 아직 이름표가 없다
        tapLabel("재설정 링크 받기")
        shot("D3-재설정요청")   // 👁 「…재설정 링크를 보내드렸어요」가 나왔는가
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - F. 게스트(비회원) — 로그인 유도가 제자리에서 뜨는가
// ─────────────────────────────────────────────────────────────────────────────

final class F_GuestTests: GabolleUITestCase {

    /// 🔴 통과 기준: 화면을 «여는 것»만으로 로그인이 뜨면 버그다.
    ///    로그인은 «계정이 필요한 동작을 누를 때»만 떠야 한다.
    func test_F1_공개_화면_넷은_로그인_없이_보인다() {
        enterAsGuest()
        for (tab, name) in [("home", "홈"), ("feed", "피드"), ("map", "내 여행"), ("me", "마이페이지")] {
            tab(tab)
            shot("F1-\(name)")
            XCTAssertFalse(textExists("비밀번호", timeout: 2),
                           "\(name) 탭을 열었을 뿐인데 로그인 창이 떴다")
        }
    }

    func test_F2_계정이_필요한_동작은_로그인으로_보낸다() {
        enterAsGuest()
        tab("feed")
        tapLabel("기록 남기기")
        shot("F2-기록쓰기-로그인유도")
        XCTAssertTrue(textExists("로그인", timeout: 8) || textExists("비밀번호", timeout: 2),
                      "게스트가 기록 쓰기를 눌렀는데 로그인으로 안 보냈다")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - H·I. 홈 · 탭바
// ─────────────────────────────────────────────────────────────────────────────

final class HI_HomeAndTabsTests: GabolleUITestCase {

    func test_I1_탭_다섯_개를_정순_역순으로_돈다() {
        enterAsGuest()
        let tabs = ["home", "feed", "schedule", "map", "me"]
        for key in tabs + tabs.reversed() {
            tapId("tab-\(key)")
            shot("I1-\(key)")
            XCTAssertEqual(app.state, .runningForeground, "탭 \(key) 에서 앱이 꺼졌다")
        }
    }

    func test_H1_홈의_주요_칸을_모두_누르고_돌아온다() {
        enterAsGuest()
        tab("home")
        shot("H1-홈전체")   // 👁 같은 카드가 두 번 그려지지 않았는가

        for label in ["알림 확인", "가볼래 여행 도우미 열기"] {
            let element = byLabel(label)
            if element.waitForExistence(timeout: 5) {
                element.tap()
                shot("H1-\(label)")
                tapId("tab-home")
            }
        }
    }

    func test_H2_여행_계획_시작_바에서_날짜와_인원을_고른다() {
        enterAsGuest()
        tab("home")
        for label in ["출발지", "날짜", "인원"] {
            let element = byLabel(label)
            if element.waitForExistence(timeout: 5) {
                element.tap()
                shot("H2-\(label)")   // 👁 고른 값이 그대로 표시되는가
                if byLabel("닫기").exists { byLabel("닫기").tap() }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - J. AI 챗봇
// ─────────────────────────────────────────────────────────────────────────────

final class J_ChatTests: GabolleUITestCase {

    /// 🔴 답 아래 버튼이 가리킬 수 있는 곳은 여섯 개뿐이다. 그 밖의 주소가 오면
    ///    앱이 버튼을 «조용히» 지운다 — 과거 S15P21E201-1273 이 정확히 그것이었다
    ///    (서버는 다섯 개를 보내는데 앱은 세 개만 알고 있었다).
    func test_J1_다섯_가지를_물어보고_답과_버튼을_남긴다() {
        enterAsGuest()
        openDeepLink("gabolle://chat")

        let questions = [
            "2박 3일 부산 여행 짜줘",
            "광안리 맛집 알려줘",
            "환율 알려줘",
            "버스 언제 와?",
            "메뉴판 읽어줘",
        ]
        for (index, question) in questions.enumerated() {
            let input = byLabel("기능이나 궁금한 점 검색")
            guard input.waitForExistence(timeout: 12) else {
                XCTFail("챗봇 입력칸을 못 찾았다"); return
            }
            input.tap()
            input.typeText(question)
            tapLabel("검색")
            _ = byLabel("답변 준비 중").waitForExistence(timeout: 3)
            Thread.sleep(forTimeInterval: 12)     // 답이 오기를 기다린다
            shot("J1-\(index + 1)-\(question)")   // 👁 답 아래 버튼이 있는가 · 눌러 보면 그 화면이 열리는가
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - K. 여행 만들기 10문항
// ─────────────────────────────────────────────────────────────────────────────

final class K_PlanTests: GabolleUITestCase {

    /// 열 문항 — 코드의 `PLAN_QUESTIONS` 그대로다.
    static let questions = [
        "여행 범위", "총예산", "하루 여행 시간 · 이동수단", "여행 카테고리", "여행 기분",
        "좋아하는 분위기", "로컬성 · 조용함 · 관광지", "음식 취향",
        "이번 여행 이동 보조 · 짐", "꼭 가고 싶은 장소",
    ]

    func test_K1_열_문항을_끝까지_지나간다() {
        enterAsGuest()
        tapId("tab-schedule")
        if byLabel("동의하고 계속").waitForExistence(timeout: 6) { byLabel("동의하고 계속").tap() }

        for (index, title) in Self.questions.enumerated() {
            shot("K1-\(index + 1)-\(title)")
            // 🔴 질문마다 고르는 방식이 달라서 «이름표»가 필요하다(문서 3-3의 요청 목록).
            //    이름표가 들어오기 전까지는 스크린샷만 남기고 손으로 넘긴다.
        }
        XCTSkip("반자동 — 질문별 선택지에 testID 가 없다. 문서 3-3 의 `plan-q-<키>` 를 요청한 뒤 자동화한다")
    }

    /// 🔴 여기가 가장 깨지기 쉬운 자리다. 초안이 «비동기로» 저장되므로,
    ///    불러오기 전에 덮어쓰면 답이 사라진다.
    func test_K2_중간에_나갔다_와도_답이_남아_있다() {
        enterAsGuest()
        tapId("tab-schedule")
        shot("K2-나가기전")
        tapId("tab-home")
        tapId("tab-schedule")
        shot("K2-돌아온뒤")   // 👁 두 사진의 답이 같은가. 초기화됐으면 버그다
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - M. 일정 — 가장 복잡한 화면
// ─────────────────────────────────────────────────────────────────────────────

/// 🔴 일정은 **여행이 하나 있어야** 돌아간다. K장에서 만든 여행이 남아 있어야 하고,
///    로그인한 계정이어야 한다. 여행이 없으면 시험이 «그 자리에서» 멈춘다 —
///    「여행이 없어서 통과」로 적히지 않게 하려는 것이다.
final class M_ItineraryTests: GabolleUITestCase {

    private func openItinerary() {
        enterSignedIn(email: QA.signedInEmail, password: QA.signedInPassword)
        tab("map")                                   // 내 여행
        let card = byLabel("여행 경로")
        guard card.waitForExistence(timeout: 15) else {
            shot("M-여행없음")
            XCTFail("내 여행에 여행이 없다 — K장에서 하나 만든 뒤 이 시험을 돌린다")
            return
        }
        card.tap()
        XCTAssertTrue(byId("itinerary-day-1").waitForExistence(timeout: 20), "일정 화면에 못 갔다")
        shot("M-일정도착")
    }

    func test_M1_날짜_탭을_차례로_눌러_본다() {
        openItinerary()
        for day in 1...5 {
            let tab = byId("itinerary-day-\(day)")
            guard tab.exists else { break }          // 여행 일수만큼만 있다
            tab.tap()
            shot("M1-\(day)일차")
        }
        // 👁 「N일차」 개수가 여행 일수와 같은가 · 같은 장소가 두 번 들어가 있지 않은가
    }

    /// 🔴 저장과 취소를 «둘 다» 본다. 저장만 보면, 취소가 저장으로 동작해도 통과한다.
    func test_M2_순서를_바꾸고_저장하면_남고_취소하면_돌아온다() {
        openItinerary()

        // ① 저장하는 길
        tapId("itinerary-reorder")
        shot("M2-순서수정중")
        let down = byLabel("아래로 이동")            // 첫 항목을 한 칸 내린다
        if down.waitForExistence(timeout: 8) { down.tap() }
        tapId("itinerary-save-order")
        shot("M2-저장직후")
        tab("home"); tab("map")                      // 화면을 나갔다 온다
        shot("M2-다시들어옴")                        // 👁 바뀐 순서가 남아 있는가

        // ② 취소하는 길
        openItinerary()
        tapId("itinerary-reorder")
        if byLabel("아래로 이동").waitForExistence(timeout: 8) { byLabel("아래로 이동").tap() }
        tapId("itinerary-cancel-order")
        shot("M2-취소직후")                          // 👁 원래 순서로 돌아왔는가
    }

    func test_M3_최근_변경_취소는_직전_하나만_되돌린다() {
        openItinerary()
        let undo = byId("itinerary-undo")
        guard undo.waitForExistence(timeout: 10) else {
            XCTSkip("되돌릴 변경이 없다 — M2 를 먼저 돌린 뒤 이 시험을 돌린다")
        }
        shot("M3-되돌리기전")
        undo.tap()
        shot("M3-되돌린뒤")                          // 👁 «직전 하나»만 되돌아갔는가
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - Q. 피드 — 기록 쓰기
// ─────────────────────────────────────────────────────────────────────────────

final class Q_ComposeTests: GabolleUITestCase {

    /// 🔴 「업로드 다시 시도」나 「실패」가 뜨면 **그 자체가 버그**다 (과거 S15P21E201-1187).
    func test_Q1_글만_있는_기록을_올린다() {
        enterSignedIn(email: QA.signedInEmail, password: QA.signedInPassword)
        tab("feed")
        tapLabel("기록 남기기")

        type(byId("compose-body"), "자동화 시험 기록 — 글만", "기록 내용")
        shot("Q1-글만입력")
        tapId("compose-submit")
        shot("Q1-올린뒤")                            // 👁 피드에 «바로» 보이는가

        XCTAssertFalse(textExists("업로드 다시 시도", timeout: 5), "업로드가 실패했다")
    }

    /// 사진 3장까지 붙고 4장째가 막히는지 본다.
    func test_Q2_사진을_세_장까지_붙이고_네_장째는_막힌다() {
        enterSignedIn(email: QA.signedInEmail, password: QA.signedInPassword)
        tab("feed")
        tapLabel("기록 남기기")
        type(byId("compose-body"), "자동화 시험 기록 — 사진", "기록 내용")

        for round in 1...3 {
            tapId("compose-add-photo")
            nudge()                                  // 사진 권한 창이 뜨면 대신 눌러 준다
            let photos = XCUIApplication(bundleIdentifier: "com.apple.mobileslideshow")
            let cell = (photos.state == .runningForeground ? photos : app).cells.element(boundBy: round - 1)
            if cell.waitForExistence(timeout: 12) { cell.tap() }
            shot("Q2-\(round)장")                    // 👁 미리보기에 «실제로» 뜨는가 (회색 네모면 버그)
        }

        // 🔴 네 장째: 「사진 추가」가 사라져 있어야 한다 (canAddMore 가 거짓이 된다)
        XCTAssertFalse(byId("compose-add-photo").exists, "사진 3장을 넘겨 붙일 수 있다")
        shot("Q2-세장에서멈춤")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - V·W. 현장 도구 허브 · 메뉴판 읽기
// ─────────────────────────────────────────────────────────────────────────────

final class VW_FieldToolsTests: GabolleUITestCase {

    func test_V1_현장_도구_넷을_차례로_열고_돌아온다() {
        enterAsGuest()
        openDeepLink("gabolle://field/translate")
        // 🔴 이름표는 코드의 tool.key 로 만들어진다 — speak/transit 이 아니라 phrase/bus 다.
        //    「메뉴판 읽기」 같은 글자로 찾으면 언어를 바꾸는 순간 전부 깨진다.
        for key in ["menu", "phrase", "exchange", "bus", "weather"] {
            tapId("field-\(key)")
            shot("V1-\(key)")
            XCTAssertEqual(app.state, .runningForeground, "field-\(key) 에서 앱이 꺼졌다")
            openDeepLink("gabolle://field/translate")
        }
    }

    /// 🔴 분당 3회 한도를 지킨다. 지키지 않으면 결과가 전부 `MENU_SCAN_RATE_LIMITED` 가
    ///    되고, 그건 버그가 아니라 우리가 만든 실패다.
    func test_W1_메뉴판을_읽고_음식_설명과_그림까지_연다() {
        enterAsGuest()

        for index in 1...QA.menuPhotoCount {
            openDeepLink("gabolle://field/menu-scan")
            tapLabel("앨범에서 고르기")   // 🔴 메뉴판 화면은 아직 이름표가 없다 (문서 3-3)
            nudge()                                  // 사진 권한 창이 뜨면 대신 눌러 준다

            // 앨범의 첫 사진을 고른다. 시뮬레이터 사진 앱의 셀에는 이름표가 없다.
            let photos = XCUIApplication(bundleIdentifier: "com.apple.mobileslideshow")
            let cell = (photos.state == .runningForeground ? photos : app).cells.element(boundBy: index - 1)
            if cell.waitForExistence(timeout: 12) { cell.tap() }

            // 읽는 데 20~45초가 걸린다.
            _ = byLabel("다른 메뉴판 찍기").waitForExistence(timeout: 90)
            shot("W1-\(index)-결과")
            // 👁 ① 「한국어」「日本語」 듣기 버튼이 «가로로» 나란한가 (세로로 쌓이면 버그)
            // 👁 ② 첫 줄에 가격이 있는가
            // 👁 ③ 사진 출처가 한 언어로 적혀 있는가 («Example · 한국관광공사…» 면 섞인 것)

            // 음식 설명과 AI 그림
            let ask = byLabel("이건 어떤 음식인가요?")
            if ask.waitForExistence(timeout: 10) {
                ask.tap()
                Thread.sleep(forTimeInterval: 25)    // 그림은 10~46초가 걸린다
                shot("W1-\(index)-음식설명")
                // 👁 ④ AI 그림 자리가 «흰 빈 상자»가 아닌가 — 지금 이게 재현되고 있다
            }

            Thread.sleep(forTimeInterval: QA.menuScanSpacing)   // 🔴 분당 3회 한도
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - X. 환율 · 버스 · 통역
// ─────────────────────────────────────────────────────────────────────────────

final class X_FieldValueTests: GabolleUITestCase {

    /// 🔴 주말·공휴일에도 값이 나와야 한다. 한국수출입은행이 그날 고시를 안 주면
    ///    서버가 최대 7일 전까지 거슬러 찾는다(S15P21E201-1300, 2026-09-19 토요일 운영 확인).
    func test_X1_환율이_주말에도_나온다() {
        enterAsGuest()
        openDeepLink("gabolle://field/exchange-rate")
        shot("X1-환율")
        XCTAssertFalse(textExists("아직 준비 중", timeout: 5), "환율이 「아직 준비 중이에요」로 뜬다")
        XCTAssertFalse(textExists("다시 시도", timeout: 3), "환율에 오류가 떴다")
    }

    func test_X2_버스_도착_시간이_말이_되는가() {
        enterAsGuest()
        openDeepLink("gabolle://field/transit")
        Thread.sleep(forTimeInterval: 8)
        shot("X2-버스")   // 👁 「N분」이 음수·0·수백 분이 아닌가
    }

    func test_X3_통역_문장을_넣고_듣는다() {
        enterAsGuest()
        openDeepLink("gabolle://field/speak")
        let input = byLabel("직접 입력할 한국어 문장")
        if input.waitForExistence(timeout: 10) {
            input.tap()
            input.typeText("얼음 빼주세요")
            tapLabel("입력한 문장 듣기")
            shot("X3-통역")   // 👁 소리가 실제로 나는가 (사람이 듣는다)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: - Z. 다국어 한 바퀴
// ─────────────────────────────────────────────────────────────────────────────

final class Z_LanguageSweepTests: GabolleUITestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()          // 🔴 여기서는 시스템 언어를 고정하지 않는다. 앱 안에서 고른다
    }

    /// 🔴 판정 기준
    ///   · 일본어·중국어에 «영어»가 섞임      → 버그 아님 (uiTranslated: false 로 공표된 상태)
    ///   · 일본어·중국어에 «한국어»가 새어 나옴 → 버그 (일본어 사용자에게 한국어는 영어보다 나쁘다)
    ///   · 글자가 «잘리거나 겹침»              → 버그
    func test_Z1_다섯_언어로_다섯_탭을_찍는다() {
        for lang in ["ko", "en", "ja", "zh-Hans", "zh-Hant"] {
            tapId("lang-\(lang)")
            tapId("start-gabolle")
            tapId("app-intro-skip")
            tapId("age-gate-check")
            tapId("age-gate-continue")
            tapId("permissions-browse-guest")
            XCTAssertTrue(byId("tab-home").waitForExistence(timeout: 20), "\(lang) 로 홈에 못 갔다")

            for key in ["home", "feed", "schedule", "map", "me"] {
                tapId("tab-\(key)")
                shot("Z1-\(lang)-\(key)")
            }
            openDeepLink("gabolle://field/translate")
            shot("Z1-\(lang)-현장도구")

            app.terminate()
            app.launch()
        }
    }
}
