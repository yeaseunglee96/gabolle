package com.gabolle.backend.place;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.NearbyPlaceItem;
import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S15P21E201-1211 — 장소 <b>상세에는 있고 목록에는 없는 칸</b>이 새로 생기면 여기가 빨개진다.
 *
 * <h2>왜 필요한가 — 같은 일이 네 번 났다</h2>
 *
 * 상세 응답에 칸을 더할 때 <b>「목록에도 실어야 하나」를 묻는 자리가 코드에 없다.</b> 사람이
 * 매번 기억해야 하고, <b>네 번 빠졌다. 네 번 다 배포된 뒤에 화면을 보고 알았다.</b>
 *
 * <ul>
 *   <li>사진 주소와 출처가 상세에만 있어 홈 카드가 사진을 못 그렸다 ({@code S15P21E201-1120})</li>
 *   <li>영문 주소가 상세에만 있어 <b>영어 이름 바로 밑에 한글 주소</b>가 붙었다 ({@code -1194})</li>
 *   <li>그 칸이 <b>화면에서 안 옮겨졌다</b> — 타입엔 있는데 값이 버려졌다 ({@code -1195})</li>
 *   <li>「무엇을 찍은 사진인가」가 상세에만 있어 <b>뱃지를 못 달았다</b> ({@code -1205})</li>
 * </ul>
 *
 * <h2>🔴 이 검사가 하지 <b>않는</b> 것</h2>
 *
 * <b>「목록은 상세가 가진 칸을 다 가져야 한다」고 하지 않는다.</b> 목록이 상세보다 작은 것은
 * 정상이다 — 피처 목록이나 출처를 목록에 실으면 그게 오히려 결함이다. 그 규칙으로 만들면
 * <b>아래 여섯이 매번 빨개지고, 지킬 수 없는 규칙은 꺼진다.</b>
 *
 * <h2>🔴 아래 목록은 주장이 아니라 사진이다</h2>
 *
 * <b>「이 칸은 목록에 실으면 안 된다」를 하나도 주장하지 않는다.</b> 2026-09-18 현재 상세에만
 * 있는 칸을 <b>그대로 찍어 둔 것</b>뿐이다. 그래서 <b>무엇이 옳은지 몰라도</b> 이 검사를 둘 수
 * 있고, 요구하는 것은 하나다 — <b>상세에 칸을 더할 때 목록을 한 번 생각했다는 표시.</b>
 *
 * <h2>이 검사가 빨개질 두 경우 — 둘 다 정상이다</h2>
 *
 * <ol>
 *   <li><b>상세에 칸이 생겼다.</b> 목록에도 실을지 정하고, 안 실기로 했으면 아래 목록에 한 줄
 *       더한다</li>
 *   <li><b>목록에 실렸다.</b> 아래 목록에서 그 줄을 뺀다</li>
 * </ol>
 *
 * <p>🔴 <b>같은 MR 에서 상세와 목록에 함께 더하면 아무 일도 안 난다.</b> 간격이 안 변하기
 * 때문이다 — <b>이미 답한 사람을 붙잡지 않는다.</b>
 *
 * <h2>🟢 DB 가 필요 없다</h2>
 *
 * 레코드의 칸 이름만 읽으므로 Spring 컨텍스트도 안 띄운다. <b>도커가 꺼진 PC 에서도 돌고
 * 1초 안에 끝난다</b> — 사람을 가리키는 표를 묻는 검사({@code S15P21E201-1196})가 Postgres 를
 * 요구하는 것과 다르다.
 *
 * <h2>왜 장소 짝 하나인가</h2>
 *
 * 짝으로 보이는 것이 여섯 있지만 <b>증거가 있는 것은 장소뿐</b>이다 — 위 네 사례가 전부 거기서
 * 났다. 나머지는 <b>짝인지조차 확실하지 않다</b>(목록을 감싸는 봉투이지 상세의 짝이 아닌 것이
 * 섞여 있다). 지금 넣으면 거짓 실패가 나고, <b>거짓 실패가 한 번 나면 사람이 검사를 끈다.</b>
 */
class PlaceListDetailFieldGapTest {

	/**
	 * 2026-09-18 현재 <b>상세에만</b> 있는 칸. 주장이 아니라 사진이다 — 위 머리말 참고.
	 *
	 * <p>여섯 다 목록에 없는 것이 정상이라고 <b>지금은</b> 보고 있다. 무거운 것(피처 목록),
	 * 출처, 요청한 일정에 들어 있는지, 영업시간·가격대, 그리고 언어 판정 결과다.
	 */
	private static final List<String> DETAIL_ONLY_FIELDS = List.of(
			"features", "itineraryInclusion", "openingHours", "priceLevel", "provenance", "resolvedLanguage");

	@Test
	@DisplayName("🔴 상세에만 있는 칸 목록이 그대로다 — 달라졌으면 목록에도 실을지 정하라는 뜻이다")
	void detailOnlyFieldsAreAccountedFor() {
		Set<String> inLists = new LinkedHashSet<>(componentNames(PlaceSummaryResponse.class));
		inLists.addAll(componentNames(NearbyPlaceItem.class));

		List<String> detailOnly = new ArrayList<>(componentNames(PlaceDetailResponse.class));
		detailOnly.removeAll(inLists);

		List<String> appeared = new ArrayList<>(detailOnly);
		appeared.removeAll(DETAIL_ONLY_FIELDS);
		List<String> nowInLists = new ArrayList<>(DETAIL_ONLY_FIELDS);
		nowInLists.removeAll(detailOnly);

		assertThat(appeared).as("""

				🔴 장소 상세에 칸이 생겼는데 목록에는 없습니다: %s

				   이 칸을 목록(검색·갈래·근처)에도 실어야 합니까?
					 (가) 실어야 한다 — PlaceSummaryResponse·NearbyPlaceItem 과 그 팩토리를 고치십시오.
						  값이 실제로 옮겨지는지는 ListResponseFieldsTest 가 잽니다
					 (나) 상세 전용이다 — 아래 목록에 한 줄 더하십시오

				   🔴 목록에 줄을 더하는 것 자체는 목록 응답에 아무것도 싣지 않습니다.
					  그건 (가) 를 골랐을 때 하는 별개의 일입니다.
				""".formatted(appeared)).isEmpty();

		assertThat(nowInLists).as("""

				목록에 실린 칸입니다: %s

				   PlaceListDetailFieldGapTest 의 DETAIL_ONLY_FIELDS 에서 그 줄을 빼십시오.
				   이 목록은 "상세에만 있는 칸" 의 사진이고, 실렸으면 더는 거기 있으면 안 됩니다.
				""".formatted(nowInLists)).isEmpty();
	}

	private static List<String> componentNames(Class<?> record) {
		return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
	}
}
