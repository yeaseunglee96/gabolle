package com.gabolle.backend.tripnaming.application;

import java.util.List;
import java.util.Set;

/**
 * 모델이 지어낸 장소 이름이 여행 제목에 박히는 것을 막는다.
 *
 * <p>「일정에 있는 낱말만 통과」로 하면 한국어가 안 된다 — 「해운대에서 보낸 이틀」의
 * 조사와 보통 명사가 전부 장소 목록 밖이라 멀쩡한 이름까지 버려진다. 그래서 거르는 방향을
 * 뒤집어, 평범한 말은 통과시키고 장소처럼 생긴 조각(장소 꼬리말로 끝나거나 지역 이름인
 * 것)만 검사해 그것이 이 여행의 장소 목록에 없으면 후보를 통째로 버린다.
 *
 * <p>틀리는 방향은 한쪽으로 몰았다. 「가을 산」처럼 장소가 아닌 말이 같이 걸리는 것은
 * 후보 하나를 잃을 뿐이지만, 지어낸 장소를 한 번 통과시키면 거짓이 화면에 박힌다.
 */
public final class PlaceWordGuard {

	/**
	 * 장소 이름의 꼬리말. 끝에서만 찾으면 조사 한 글자가 꼬리말을 가리므로(「불국사에서」)
	 * 조각 안쪽에서 찾고, 앞에 두 글자 이상 붙어 있을 때만 장소로 본다 — 그 조건이
	 * 「산책」의 「산」 같은 평범한 말을 걸러 준다.
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
	 * <p>부산은 일부러 뺐다. 이 서비스는 부산 여행만 다루므로 「부산」은 어느 여행에서도
	 * 참이고, 넣으면 멀쩡한 이름이 전부 버려진다.
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
	 * @param vocabulary 이 여행의 일정에 실제로 들어 있는 장소 이름들
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

	/** 장소 이름의 꼴인가 — 「불국사에서」의 「사」처럼 안쪽에 있어도 찾는다. */
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
				// 앞에 두 글자 이상 붙어 있을 때만 장소로 본다 — 「불국+사」는 보고
				// 「산+책」·「회+사」는 안 본다.
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
