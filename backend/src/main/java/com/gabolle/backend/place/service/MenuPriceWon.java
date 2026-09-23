package com.gabolle.backend.place.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 적재된 대표 메뉴 값 하나를 읽어 원 단위 숫자를 꺼낸다. 값 문자열만 있으면 되므로 DB 없이
 * 검사할 수 있도록 {@link OpeningHoursValue} 와 같이 떼어 뒀다.
 *
 * <p>값은 적재기({@code PlaceFeatureNdjsonReader.readPriceNarrative})가 낸 모양 그대로다.
 *
 * <pre>
 * {"priceWon":39000,"menu":"스텔라마리스 굴 플레이트 39,000원"}
 * </pre>
 *
 * <p>🔴 <b>모르는 것을 0원으로 바꾸지 않는다.</b> 값이 있는 곳은 아직 일부이고, 0원은 화면에서
 * <b>「무료」</b>로 읽힌다 — 프론트가 {@code estimatedCostKrw === 0} 을 무료로 그린다
 * ({@code frontend/app/trips/[id]/itinerary.tsx}). 모름을 0으로 접으면 조사가 안 된 곳이
 * 공짜인 것처럼 보이고, 그 잘못은 합계에 섞여 들어가 어느 화면에도 안 보인다. 그래서 읽을 수
 * 없는 값은 전부 {@code null} 로 답한다 — 던지지 않는다.
 *
 * <p>양수가 아닌 값도 {@code null} 이다. 2026-09-22 운영 실측으로 189행의 범위는
 * 2,050원~182,000원이고 0이나 빈 값은 없었다 — 즉 이 방어로 버려지는 실제 자료는 없다.
 *
 * <p><b>대표 메뉴 한 가지의 값이다.</b> 그 장소에서 쓸 돈 전부가 아니다. 입장료·교통비는 아직
 * 자료가 없고, 그래서 이 값을 더한 합계는 언제나 <b>「적어도 이만큼」</b>이다.
 */
public final class MenuPriceWon {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/** 가격 피처의 갈래. {@code V20260922080000} 이 {@code ck_place_feature_type} 에 더했다. */
	public static final String FEATURE_TYPE = "MENU_PRICE_WON";

	private MenuPriceWon() {
	}

	/**
	 * @param value {@code place_feature.value} 문자열. {@code null} 이어도 된다
	 * @return 원 단위 값. 값이 없거나 읽을 수 없거나 양수가 아니면 {@code null}
	 */
	public static Integer wonOf(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		JsonNode node;
		try {
			node = MAPPER.readTree(value);
		}
		catch (JacksonException ex) {
			// 값 하나가 깨졌다고 추천 응답 전체를 실패시키지 않는다. 모르는 것으로 둔다.
			return null;
		}
		JsonNode won = node.path("priceWon");
		if (!won.isNumber()) {
			return null;
		}
		int krw = won.asInt();
		return (krw > 0) ? krw : null;
	}
}
