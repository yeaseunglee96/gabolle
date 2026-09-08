package com.gabolle.backend.event.domain;

/**
 * 이벤트를 만든 쪽 (DR-13).
 *
 * <p>🔴 이 구분이 분석에서 신뢰 경계가 된다. 클라이언트가 보낸 값은 조작될 수 있고,
 * 서버 Outbox 가 만든 값은 비즈니스 트랜잭션 안에서 나온 것이라 믿을 수 있다.
 * 한 표에 섞어 두는 대신 이 칸으로 가른다 — 표를 나누면 requestId 로 조인할 때
 * 매번 UNION 을 써야 한다.
 */
public enum Producer {
    /** 웹·앱이 보냄. 노출·상세조회·건너뜀 등 화면에서만 알 수 있는 것 */
    CLIENT,
    /** 서버 비즈니스 API 가 트랜잭션 안에서 만듦 */
    SERVER
}
