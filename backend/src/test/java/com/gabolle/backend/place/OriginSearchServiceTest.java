package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.adapter.OriginCandidate;
import com.gabolle.backend.place.adapter.OriginSearchPort;
import com.gabolle.backend.place.adapter.OriginSearchProperties;
import com.gabolle.backend.place.api.OriginSearchResponse;
import com.gabolle.backend.place.service.OriginSearchService;
import com.gabolle.backend.place.service.PlaceRequestException;

/**
 * {@link OriginSearchService} 를 DB 없이 가짜 포트로 돌린다. 짧은 검색어는 결과를 버리는
 * 것으로 부족하고 외부 호출 자체가 없어야 하므로, 호출 횟수를 세는 포트로 실측한다.
 */
class OriginSearchServiceTest {

	private final CountingOriginSearchPort kakaoPort = new CountingOriginSearchPort(List.of(
			new OriginCandidate("부산역", "부산 동구 중앙대로 206", 35.1151, 129.0413, "12345",
					OriginCandidate.Source.KAKAO_LOCAL)));

	private final CountingOriginSearchPort fallbackPort = new CountingOriginSearchPort(List.of());

	private final OriginSearchProperties properties = new OriginSearchProperties();

	private final OriginSearchService service;

	OriginSearchServiceTest() {
		this.properties.setKakaoRestApiKey("test-rest-key");
		this.service = new OriginSearchService(this.kakaoPort, this.fallbackPort, this.properties);
	}

	@Test
	@DisplayName("🔴 한 글자 검색어는 QUERY_TOO_SHORT 로 거절하고 카카오 포트를 부르지 않는다")
	void oneCharacterQueryNeverCallsAdapter() {
		assertThatThrownBy(() -> this.service.search("서", 10))
				.isInstanceOf(PlaceRequestException.class)
				.extracting(exception -> ((PlaceRequestException) exception).getCode())
				.isEqualTo("QUERY_TOO_SHORT");

		assertThat(this.kakaoPort.callCount()).isZero();
	}

	@Test
	@DisplayName("공백만 다듬고 나면 2자 미만인 검색어도 같은 이유로 거절한다")
	void queryIsTrimmedBeforeLengthCheck() {
		assertThatThrownBy(() -> this.service.search("  가  ", 10))
				.isInstanceOf(PlaceRequestException.class);

		assertThat(this.kakaoPort.callCount()).isZero();
	}

	@Test
	@DisplayName("정상 검색어는 카카오 후보를 그대로 돌려주고 degraded 가 아니다")
	void normalQueryReturnsKakaoCandidates() {
		OriginSearchResponse response = this.service.search("서면역", 10);

		assertThat(response.degraded()).isFalse();
		assertThat(response.degradedReason()).isNull();
		assertThat(response.items()).hasSize(1);
		assertThat(response.items().get(0).name()).isEqualTo("부산역");
		assertThat(response.items().get(0).source()).isEqualTo("KAKAO_LOCAL");
		assertThat(this.kakaoPort.callCount()).isEqualTo(1);
		assertThat(this.fallbackPort.callCount()).isZero();
	}

	@Test
	@DisplayName("🔴 limit 상한을 넘긴 요청도 설정된 maxLimit 을 넘지 않는다")
	void limitIsClampedToMaxLimit() {
		this.properties.setMaxLimit(2);
		List<OriginCandidate> many = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			many.add(new OriginCandidate("장소" + index, "주소" + index, 35.0 + index, 129.0 + index, "id" + index,
					OriginCandidate.Source.KAKAO_LOCAL));
		}
		this.kakaoPort.setCandidates(many);

		OriginSearchResponse response = this.service.search("서면역", 100);

		assertThat(response.limit()).isEqualTo(2);
		assertThat(response.items()).hasSizeLessThanOrEqualTo(2);
	}

	/** 호출 횟수를 세는 가짜 포트. */
	private static final class CountingOriginSearchPort implements OriginSearchPort {

		private List<OriginCandidate> candidates;

		private final AtomicInteger calls = new AtomicInteger();

		private CountingOriginSearchPort(List<OriginCandidate> candidates) {
			this.candidates = candidates;
		}

		void setCandidates(List<OriginCandidate> candidates) {
			this.candidates = candidates;
		}

		int callCount() {
			return this.calls.get();
		}

		@Override
		public List<OriginCandidate> search(String query, int limit) {
			this.calls.incrementAndGet();
			return this.candidates;
		}
	}
}
