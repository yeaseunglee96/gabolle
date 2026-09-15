package com.gabolle.backend.tripnaming.application;

import java.util.List;
import java.util.Set;

/**
 * 모델이 지어낸 <b>장소 이름</b>이 여행 제목에 박히는 것을 막는다 — S15P21E201-1025.
 *
 * <h2>🔴 무엇이 위험한가</h2>
 *
 * 여행 이름 짓기에서 모델이 지어낼 수 있는 것은 <b>가 보지도 않은 장소 이름</b>이다.
 * 「경주 불국사에서 보낸 이틀」이라는 제목이 부산 여행에 붙으면, 사용자는 자기가 만들지
 * 않은 일정을 자기 것으로 기억하게 된다.
 *
 * <h2>왜 「일정에 있는 낱말만」이 아닌가</h2>
 *
 * 티켓은 <i>"받은 이름이 그 낱말들 밖으로 나가면 기계가 버린다"</i> 라고 적었는데, 그것을
 * 곧이곧대로 하면 <b>한국어가 안 된다</b> — 「해운대에서 보낸 이틀」의 "에서"·"보낸"·"이틀"
 * 이 전부 장소 목록 밖이라 멀쩡한 이름이 전부 버려진다.
 *
 * <p>그래서 거르는 것을 뒤집었다. <b>평범한 말은 통과시키고, 「장소처럼 생긴 조각」만
 * 검사한다.</b> 지어낸 장소 이름은 거의 언제나 둘 중 하나다.
 *
 * <ul>
 *   <li>끝이 <b>장소 꼬리말</b>이다 — 불국{@code 사} · 광안{@code 대교} · 감천문화{@code 마을}</li>
 *   <li><b>지역 이름</b>이다 — 경주 · 제주 · 여수</li>
 * </ul>
 *
 * 그 조각이 <b>이 여행의 장소 목록에 없으면</b> 후보를 통째로 버린다.
 *
 * <h2>🔴 틀리는 방향을 한쪽으로 몰았다</h2>
 *
 * 「가을 산」처럼 장소가 아닌데 꼬리말로 끝나는 말도 같이 걸린다. <b>그건 괜찮다</b> —
 * 후보 하나를 잃을 뿐이고 다른 후보나 템플릿으로 물러서면 된다. 반대로 지어낸 장소를
 * 한 번 통과시키면 그건 <b>거짓이 화면에 박히는 것</b>이다. 두 실수의 값이 다르므로
 * 싼 쪽으로 틀리게 만들었다.
 */
public final class PlaceWordGuard {

	/**
	 * 장소 이름의 꼬리말.
	 *
	 * <p>🔴 <b>끝에서만 찾으면 안 된다.</b> 「불국사」는 걸리는데 「불국사<b>에서</b>」는
	 * 안 걸린다 — 조사 한 글자가 꼬리말을 가린다. 그래서 조각 <b>안쪽</b>에서 찾고,
	 * <b>앞에 두 글자 이상이 붙어 있을 때만</b> 장소로 본다(불국+사 · 광안+대교).
	 * 그 조건이 「산책」의 「산」이나 「회사」의 「사」 같은 평범한 말을 걸러 준다.
	 */
	private static final List<String> PLACE_SUFFIXES = List.of(
			"해수욕장", "해변", "수목원", "식물원", "동물원", "박물관", "미술관", "전망대",
			"문화마을", "유원지", "스카이워크", "전통시장", "대교", "온천", "폭포", "계곡",
			"공원", "시장", "타워", "마을", "거리", "사찰", "서원", "향교", "포구", "다리",
			"역", "항", "섬", "산", "사", "절", "성", "궁", "릉", "천",
			"대", "동", "로", "길", "리", "곶", "만", "문", "원", "탑");

	/**
	 * 지역 이름. 꼬리말이 없어서 위 규칙에 안 걸린다 — 「제주」·「경주」가 그렇다.
	 *
	 * <p>🔴 <b>부산이 이 목록에 없다.</b> 이 서비스는 부산 여행만 다루므로 「부산」은 어느
	 * 여행에서도 참이다. 목록에 넣으면 「9월 부산 혼자」 같은 멀쩡한 이름이 전부 버려진다.
	 */
	private static final Set<String> OTHER_REGIONS = Set.of(
			"서울", "인천", "대구", "대전", "광주", "울산", "세종", "경기", "강원", "충북",
			"충남", "전북", "전남", "경북", "경남", "제주", "경주", "여수", "전주", "강릉",
			"속초", "춘천", "포항", "통영", "거제", "안동", "군산", "목포", "순천", "남해",
			"가평", "양양", "태안", "보령", "김포", "수원", "파주");

	private PlaceWordGuard() {
	}

	/**
	 * 이 후보를 화면에 내보내도 되는가.
	 *
	 * @param candidate 모델이 지어낸 이름
	 * @param vocabulary 이 여행의 일정에 <b>실제로 들어 있는</b> 장소 이름들
	 * @return 지어낸 장소 이름이 안 들어 있으면 {@code true}
	 */
	public static boolean isTruthful(String candidate, List<String> vocabulary) {
		if (candidate == null || candidate.isBlank()) {
			return false;
		}
		String haystack = String.join(" ", vocabulary);

		for (String run : hangulRuns(candidate)) {
			if (haystack.contains(run)) {
				// 장소 이름 안에 통째로 들어 있는 조각이다. 볼 것 없다.
				continue;
			}
			if (looksLikeAPlace(run) && !coveredByVocabulary(run, vocabulary)) {
				return false;
			}
		}
		return true;
	}

	/** 한글이 이어지는 조각만 본다 — 숫자·기호·공백은 장소 이름이 될 수 없다. */
	private static List<String> hangulRuns(String text) {
		List<String> runs = new java.util.ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= '가' && c <= '힣') {
				current.append(c);
			}
			else if (current.length() > 0) {
				runs.add(current.toString());
				current.setLength(0);
			}
		}
		if (current.length() > 0) {
			runs.add(current.toString());
		}
		return runs;
	}

	/** 장소 이름의 꼴인가 — 「불국사에서」의 「사」처럼 <b>안쪽</b>에 있어도 찾는다. */
	private static boolean looksLikeAPlace(String run) {
		if (OTHER_REGIONS.contains(run)) {
			return true;
		}
		for (String region : OTHER_REGIONS) {
			// 「제주에서」처럼 조사가 붙은 지역 이름.
			if (run.startsWith(region)) {
				return true;
			}
		}
		for (String suffix : PLACE_SUFFIXES) {
			for (int at = run.indexOf(suffix); at >= 0; at = run.indexOf(suffix, at + 1)) {
				// 🔴 앞에 두 글자 이상이 붙어 있을 때만 장소로 본다.
				//    「불국+사」는 보고 「산+책」·「회+사」는 안 본다.
				if (at >= 2) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 「해운대에서」처럼 조사가 붙은 꼴을 통과시킨다 — 일정에 있는 장소 이름이 이 조각 안에
	 * 들어 있으면 지어낸 것이 아니다.
	 */
	private static boolean coveredByVocabulary(String run, List<String> vocabulary) {
		for (String name : vocabulary) {
			for (String word : name.split("\\s+")) {
				if (word.length() >= 2 && run.contains(word)) {
					return true;
				}
			}
		}
		return false;
	}
}
