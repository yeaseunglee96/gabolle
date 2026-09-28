package com.gabolle.backend.place.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *
 * <p>🔴 <b>1인분이 아닌 값도 {@code null} 이다</b>(S15P21E201-1615, 사용자 결정). 「2인 세트 56,000원」을
 * 1인분처럼 읽으면 예산 상한이 인원수를 곱해 부풀리고, 항목 비용·합계도 틀린다. 판정은
 * {@link #perServing} 한 곳에 있고, 비용을 읽는 곳(일정 항목·합계·예산 상한·추천 결과·추천 엔진의 가격대)이
 * 전부 이 클래스를 거친다. 적재된 값은 그대로 두고 읽을 때만 「모름」으로 본다.
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
		return wonOfNode(node);
	}

	/**
	 * 이미 읽힌 값에서 꺼낸다 — 추천 엔진은 표식을 {@link JsonNode} 로 받는다.
	 *
	 * @return 원 단위 1인분 값. 값이 없거나 양수가 아니거나 1인분이 아니면 {@code null}
	 */
	public static Integer wonOfNode(JsonNode node) {
		if (node == null) {
			return null;
		}
		JsonNode won = node.path("priceWon");
		if (!won.isNumber()) {
			return null;
		}
		int krw = won.asInt();
		if (krw <= 0) {
			return null;
		}
		JsonNode menu = node.path("menu");
		return perServing(krw, menu.isString() ? menu.asString() : null) ? krw : null;
	}

	/** 메뉴 문구를 메뉴 하나씩으로 나누는 자리 — 「/」, 「, 」(쉼표 뒤 빈칸 — 「56,000」은 안 끊는다), 「·」, 「|」. */
	private static final Pattern PIECES = Pattern.compile("\\s*/\\s*|,\\s+|·|\\|");

	/** 「2인」·「3인분」·「1~2인」 — 큰 수가 2 이상이면 여러 명 몫이다. */
	private static final Pattern PEOPLE = Pattern.compile("(\\d+)\\s*(?:~\\s*(\\d+)\\s*)?인분?");

	private static final Pattern GROUP = Pattern.compile("커플|패밀리");

	private static final Pattern SET = Pattern.compile("세트|셋트|set");

	/** 「1인」이 적혀 있으면 세트여도 1인분이다 — 「1인세트」·「모둠회 세트 (1인)」. 「11인」은 아니다. */
	private static final Pattern ONE_PERSON = Pattern.compile("(?<!\\d)1\\s*인");

	/**
	 * 이 값이 한 사람 몫인가.
	 *
	 * <p>메뉴 문구는 한 줄에 여러 메뉴가 섞인 자유 글이다 — 「특미초밥 14,000원, 참치 모듬 2인 60,000원」에서
	 * 값이 14,000원이면 1인분이다. 그래서 <b>그 값이 적힌 토막</b>만 본다. 값이 문구에 안 적혀 있으면 어느
	 * 메뉴의 값인지 모르므로, 모든 토막이 1인분이 아닐 때만 아니라고 한다 — 멀쩡한 값을 추측으로 버리지 않는다.
	 * 문구가 없으면 판단할 근거가 없어 1인분으로 둔다(전과 같다).
	 */
	static boolean perServing(int won, String menu) {
		if (menu == null || menu.isBlank()) {
			return true;
		}
		List<String> pieces = new ArrayList<>();
		for (String piece : PIECES.split(menu)) {
			if (!piece.isBlank()) {
				pieces.add(piece);
			}
		}
		if (pieces.isEmpty()) {
			pieces.add(menu);
		}
		String withComma = String.format(Locale.ROOT, "%,d", won);
		String plain = Integer.toString(won);
		List<String> priced = pieces.stream()
				.filter((piece) -> piece.contains(withComma) || piece.contains(plain))
				.toList();
		if (!priced.isEmpty()) {
			return priced.stream().allMatch(MenuPriceWon::onePerson);
		}
		return pieces.stream().anyMatch(MenuPriceWon::onePerson);
	}

	private static boolean onePerson(String piece) {
		Matcher people = PEOPLE.matcher(piece);
		while (people.find()) {
			String upper = (people.group(2) != null) ? people.group(2) : people.group(1);
			if (Integer.parseInt(upper) >= 2) {
				return false;
			}
		}
		if (GROUP.matcher(piece).find()) {
			return false;
		}
		return !(SET.matcher(piece.toLowerCase(Locale.ROOT)).find() && !ONE_PERSON.matcher(piece).find());
	}
}
