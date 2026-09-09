package com.gabolle.backend.place;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.service.PlaceRequestException;
import com.gabolle.backend.place.service.SearchCursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 커서 왕복과 거부 조건 — DB 도 스프링도 없이 돈다.
 */
class SearchCursorTest {

	@Test
	@DisplayName("encode 한 뒤 decode 하면 fingerprint 와 offset 이 그대로 온다")
	void roundTrips() {
		String fingerprint = SearchCursor.fingerprint("감천", "ATTRACTION", 20);
		SearchCursor cursor = SearchCursor.of(fingerprint, 40);

		SearchCursor decoded = SearchCursor.decode(cursor.encode());

		assertThat(decoded.fingerprint()).isEqualTo(fingerprint);
		assertThat(decoded.offset()).isEqualTo(40);
	}

	@Test
	@DisplayName("검색 조건이 하나라도 다르면 fingerprint 도 달라진다")
	void fingerprintChangesWithCondition() {
		String base = SearchCursor.fingerprint("감천", "ATTRACTION", 20);

		assertThat(SearchCursor.fingerprint("감천", "ATTRACTION", 20)).isEqualTo(base);
		assertThat(SearchCursor.fingerprint("감천", "CAFE", 20)).isNotEqualTo(base);
		assertThat(SearchCursor.fingerprint("감천", "ATTRACTION", 30)).isNotEqualTo(base);
		assertThat(SearchCursor.fingerprint("해운대", "ATTRACTION", 20)).isNotEqualTo(base);
	}

	@Test
	@DisplayName("🔴 조건이 바뀐 커서를 그대로 받아들이면 다른 질의의 결과가 섞인다 — 그래서 fingerprint 를 검사 대상으로 노출한다")
	void decodeExposesFingerprintForCallerToCompare() {
		// SearchCursor.decode 자체는 형식만 본다. fingerprint 불일치 거부는 이 값을 들고 있는
		// PlaceSearchService.resolveOffset 의 몫이라 이 값이 정확히 나오는지만 여기서 본다.
		String fingerprint = SearchCursor.fingerprint("감천", null, 20);
		SearchCursor decoded = SearchCursor.decode(SearchCursor.of(fingerprint, 5).encode());

		assertThat(decoded.fingerprint()).isEqualTo(fingerprint);
	}

	@Test
	@DisplayName("base64 자체가 깨진 문자열은 INVALID_CURSOR 로 거부된다")
	void rejectsNotBase64String() {
		assertThatThrownBy(() -> SearchCursor.decode("not-a-valid-cursor!!"))
				.isInstanceOf(PlaceRequestException.class)
				.satisfies(ex -> assertThat(((PlaceRequestException) ex).getCode()).isEqualTo("INVALID_CURSOR"));
	}

	@Test
	@DisplayName("base64 는 맞지만 구분자(:)가 없으면 거부된다")
	void rejectsMissingSeparator() {
		String malformed = encode("no-separator-here");

		assertThatThrownBy(() -> SearchCursor.decode(malformed)).isInstanceOf(PlaceRequestException.class);
	}

	@Test
	@DisplayName("offset 이 숫자가 아니면 거부된다")
	void rejectsNonNumericOffset() {
		String malformed = encode("abcdef0123456789:not-a-number");

		assertThatThrownBy(() -> SearchCursor.decode(malformed)).isInstanceOf(PlaceRequestException.class);
	}

	@Test
	@DisplayName("offset 이 음수면 거부된다")
	void rejectsNegativeOffset() {
		String malformed = encode("abcdef0123456789:-1");

		assertThatThrownBy(() -> SearchCursor.decode(malformed)).isInstanceOf(PlaceRequestException.class);
	}

	private String encode(String raw) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}
}
