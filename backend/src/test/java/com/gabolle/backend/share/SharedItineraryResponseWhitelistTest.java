package com.gabolle.backend.share;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.share.presentation.dto.SharedItineraryResponse;

/**
 * S15P21E201-332 완료 기준 — "나중에 응답에 칸이 하나 늘어도 잡아내는 자동 검사가 있다".
 *
 * <p>공유 응답은 로그인 없이 아무나 여는 화면에 나간다. 그래서 이 record 와 안쪽 record 의 필드 이름
 * <b>전부</b>를 허용 목록과 대조한다 — 값이 비어 있는지가 아니라 <b>칸 자체가 없는지</b>를 본다. 누가 칸 하나를
 * 더하면 이 테스트가 빨개지고, 그 사람은 "이 값을 공개해도 되는가" 를 한 번은 생각하게 된다. 늘리는 것이
 * 맞다면 허용 목록도 함께 고친다 — 그 diff 가 리뷰에서 눈에 띈다.
 */
class SharedItineraryResponseWhitelistTest {

	private static final Set<String> ROOT_ALLOWED = Set.of("title", "startDate", "finishDate", "version", "days",
			"expiresAt", "notShared");

	private static final Set<String> DAY_ALLOWED = Set.of("date", "items");

	private static final Set<String> ITEM_ALLOWED = Set.of("sequence", "placeName", "category", "startsAt", "endsAt",
			"stayMinutes");

	/** 이름에 이 조각이 들어가면 어디에도 있어선 안 된다 — 출발지 좌표·연락처·예산·인원·계정 식별자. */
	private static final List<String> FORBIDDEN_FRAGMENTS = List.of("origin", "lat", "lng", "budget", "party",
			"contact", "phone", "email", "userid", "owner", "createdby", "address");

	@Test
	@DisplayName("🔴 공유 응답의 필드 이름은 허용 목록과 정확히 같다 — 칸이 하나 늘면 빨개진다")
	void fieldNamesMatchWhitelistExactly() {
		assertThat(names(SharedItineraryResponse.class)).containsExactlyInAnyOrderElementsOf(ROOT_ALLOWED);
		assertThat(names(SharedItineraryResponse.Day.class)).containsExactlyInAnyOrderElementsOf(DAY_ALLOWED);
		assertThat(names(SharedItineraryResponse.Item.class)).containsExactlyInAnyOrderElementsOf(ITEM_ALLOWED);
	}

	@Test
	@DisplayName("🔴 출발지·연락처·예산·인원·계정 식별자를 뜻하는 이름은 어느 record 에도 없다")
	void noPrivateLookingNamesAnywhere() {
		List<String> all = new java.util.ArrayList<>();
		all.addAll(names(SharedItineraryResponse.class));
		all.addAll(names(SharedItineraryResponse.Day.class));
		all.addAll(names(SharedItineraryResponse.Item.class));

		for (String name : all) {
			String lower = name.toLowerCase(Locale.ROOT);
			for (String fragment : FORBIDDEN_FRAGMENTS) {
				assertThat(lower).as("필드 '%s' 가 금지 조각 '%s' 를 담는다", name, fragment).doesNotContain(fragment);
			}
		}
	}

	@Test
	@DisplayName("고지문 목록은 응답에 없는 것의 이름이다 — 넷 그대로")
	void notSharedNamesWhatIsAbsent() {
		assertThat(SharedItineraryResponse.NOT_SHARED).containsExactly("origin", "contact", "budget", "partySize");
		// 고지문에 적힌 이름이 실제로 응답 필드에 없는지도 본다 — 문구와 사실이 어긋나면 고지문이 거짓이 된다.
		for (String absent : SharedItineraryResponse.NOT_SHARED) {
			assertThat(names(SharedItineraryResponse.class)).doesNotContain(absent);
		}
	}

	private static List<String> names(Class<?> recordClass) {
		assertThat(recordClass.isRecord()).as("%s 는 record 여야 한다", recordClass).isTrue();
		return Arrays.stream(recordClass.getRecordComponents()).map(RecordComponent::getName).toList();
	}
}
