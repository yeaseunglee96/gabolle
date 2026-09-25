package com.gabolle.backend.place.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 장소 이름에서 전국 프랜차이즈 상표를 찾는다 (S15P21E201-1616).
 *
 * <p>쓰는 곳이 둘이다 — 추천 점수를 낮추고(동네 가게가 먼저 나오게), 한 일정에 같은 상표를 한 번만 넣는다.
 *
 * <p>🔴 <b>띄어쓰기에 기대지 않는다.</b> 운영 이름은 「배스킨라빈스광안역점」처럼 붙여 쓰거나, 「투썸 플레이스
 * 괴정점」처럼 상표 안을 띄우거나, 「Starbucks」처럼 영어로 온다. 그래서 이름에서 빈칸을 모두 빼고 소문자로 맞춘
 * 뒤 <b>별칭으로 시작하는가</b>를 본다. 흔들리는 표기(「파리바게트」)도 별칭으로 받는다.
 *
 * <p>전국 프랜차이즈만 넣는다. 부산의 지점 있는 동네 맛집(쌍둥이돼지국밥·개미집 등)은 안 넣는다 — 그 가게들이
 * 「동네 가게가 먼저」의 동네 가게 쪽이다. 2026-09-25 운영 장소 6,931곳 중 367곳(카페 214 · 음식 153)이 잡혔다.
 */
public final class ChainBrand {

	/** 상표 → 별칭. 별칭은 빈칸 없이 소문자로 적는다(이름도 그렇게 맞춰 비교한다). */
	private static final Map<String, List<String>> BRANDS = Map.ofEntries(
			Map.entry("스타벅스", List.of("스타벅스", "starbucks")),
			Map.entry("투썸플레이스", List.of("투썸플레이스", "투썸", "atwosomeplace", "twosomeplace")),
			Map.entry("이디야", List.of("이디야", "ediya")),
			Map.entry("메가커피", List.of("메가커피", "메가mgc커피", "메가엠지씨커피", "megacoffee", "megamgc")),
			Map.entry("컴포즈커피", List.of("컴포즈커피", "composecoffee")),
			Map.entry("빽다방", List.of("빽다방", "paikdabang")),
			Map.entry("할리스", List.of("할리스", "hollys")),
			Map.entry("커피빈", List.of("커피빈", "coffeebean")),
			Map.entry("엔제리너스", List.of("엔제리너스", "angelinus")),
			Map.entry("파스쿠찌", List.of("파스쿠찌", "pascucci")),
			Map.entry("탐앤탐스", List.of("탐앤탐스", "tomntoms")),
			Map.entry("폴바셋", List.of("폴바셋", "paulbassett")),
			Map.entry("더벤티", List.of("더벤티", "theventi")),
			Map.entry("매머드커피", List.of("매머드커피", "매머드익스프레스", "mammoth")),
			Map.entry("텐퍼센트커피", List.of("텐퍼센트", "tenpercent")),
			Map.entry("하삼동커피", List.of("하삼동커피")),
			Map.entry("커피베이", List.of("커피베이", "coffeebay")),
			Map.entry("드롭탑", List.of("드롭탑", "droptop")),
			Map.entry("카페베네", List.of("카페베네", "caffebene")),
			Map.entry("요거프레소", List.of("요거프레소", "yogerpresso")),
			Map.entry("공차", List.of("공차", "gongcha")),
			Map.entry("설빙", List.of("설빙", "sulbing")),
			Map.entry("배스킨라빈스", List.of("배스킨라빈스", "베스킨라빈스", "baskinrobbins")),
			Map.entry("던킨", List.of("던킨", "dunkin")),
			Map.entry("파리바게뜨", List.of("파리바게뜨", "파리바게트", "parisbaguette")),
			Map.entry("뚜레쥬르", List.of("뚜레쥬르", "touslesjours")),
			Map.entry("맘스터치", List.of("맘스터치", "momstouch")),
			Map.entry("롯데리아", List.of("롯데리아", "lotteria")),
			Map.entry("맥도날드", List.of("맥도날드", "mcdonald")),
			Map.entry("버거킹", List.of("버거킹", "burgerking")),
			Map.entry("KFC", List.of("kfc", "케이에프씨")),
			Map.entry("노브랜드버거", List.of("노브랜드버거", "nobrandburger")),
			Map.entry("서브웨이", List.of("서브웨이", "subway")),
			Map.entry("도미노피자", List.of("도미노피자", "dominos", "domino's")),
			Map.entry("피자헛", List.of("피자헛", "pizzahut")),
			Map.entry("파파존스", List.of("파파존스", "papajohn")),
			Map.entry("교촌치킨", List.of("교촌", "kyochon")),
			Map.entry("BBQ", List.of("bbq치킨", "비비큐")),
			Map.entry("bhc", List.of("bhc", "비에이치씨")),
			Map.entry("굽네치킨", List.of("굽네")),
			Map.entry("노랑통닭", List.of("노랑통닭")),
			Map.entry("본죽", List.of("본죽", "본도시락")),
			Map.entry("김밥천국", List.of("김밥천국")),
			Map.entry("이삭토스트", List.of("이삭토스트")),
			Map.entry("명륜진사갈비", List.of("명륜진사갈비")),
			Map.entry("쿠우쿠우", List.of("쿠우쿠우")),
			Map.entry("유가네닭갈비", List.of("유가네")),
			Map.entry("홍콩반점", List.of("홍콩반점")),
			Map.entry("새마을식당", List.of("새마을식당")),
			Map.entry("역전우동", List.of("역전우동")),
			Map.entry("한신포차", List.of("한신포차")),
			Map.entry("봉구스밥버거", List.of("봉구스")),
			Map.entry("신전떡볶이", List.of("신전떡볶이")),
			Map.entry("엽기떡볶이", List.of("엽기떡볶이", "동대문엽기떡볶이")),
			Map.entry("킹콩부대찌개", List.of("킹콩부대찌개")),
			Map.entry("쥬씨", List.of("쥬씨")));

	/** (별칭, 상표) — 긴 별칭부터 본다. 「엽기떡볶이」보다 「동대문엽기떡볶이」가 먼저 맞아야 하는 식이다. */
	private static final List<Map.Entry<String, String>> ALIASES = aliasesLongestFirst();

	private ChainBrand() {
	}

	/**
	 * @param name 장소 이름. {@code null} 이어도 된다
	 * @return 상표 이름. 사전에 없으면 {@code null}
	 */
	public static String brandOf(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		String normalized = name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String> alias : ALIASES) {
			if (normalized.startsWith(alias.getKey())) {
				return alias.getValue();
			}
		}
		return null;
	}

	private static List<Map.Entry<String, String>> aliasesLongestFirst() {
		List<Map.Entry<String, String>> out = new ArrayList<>();
		BRANDS.forEach((brand, aliases) -> aliases.forEach((alias) -> out.add(Map.entry(alias, brand))));
		out.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
		return List.copyOf(out);
	}
}
