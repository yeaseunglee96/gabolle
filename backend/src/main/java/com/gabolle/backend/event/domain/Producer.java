package com.gabolle.backend.event.domain;

/**
 * 이벤트를 만든 쪽. 분석에서 이 구분이 신뢰 경계가 된다 — 클라이언트가 보낸 값은 조작될 수
 * 있고, 서버 Outbox 가 만든 값은 업무 트랜잭션 안에서 나온 것이라 믿을 수 있다.
 * 표를 나누지 않고 이 칸으로 가르는 것은 requestId 조인에 매번 UNION 을 쓰지 않으려는 것이다.
 */
public enum Producer {
    /** 웹·앱이 보냄. 노출·상세조회·건너뜀 등 화면에서만 알 수 있는 것 */
    CLIENT,
    /** 서버 업무 API 가 트랜잭션 안에서 만듦 */
    SERVER
}
