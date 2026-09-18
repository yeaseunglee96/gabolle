package com.gabolle.backend.place;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.TourApiPlaceLoader;
import com.gabolle.backend.place.loader.TourApiPlaceRow;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관광공사 사진의 저작권 유형(cpyrhtDivCd) 필터링 — S15P21E201-146.
 *
 * <p>사진 있는 546곳 중 86%(468곳)가 {@code Type3}(제3자 저작물 — 재사용 전 저작권자의 별도
 * 허락이 필요)다. {@code Type1}(공공누리 제1유형 — 출처 표시하면 자유 이용)만 {@code place.photo_url}
 * 에 들어가야 한다 — 나머지는 여기서 재는 것이 전부다.
 */
class TourApiPlaceLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "tourapi-test-202609";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TourApiPlaceLoader loader;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE source_type = 'TOURAPI'");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'TOURAPI'");
	}

	@Test
	@DisplayName("🔴 Type1(공공누리 제1유형)만 photo_url 에 들어간다 — Type3(제3자 저작물)는 비운다")
	void onlyType1PhotosAreLoaded() {
		load(List.of(
				row("1", "http://tong.visitkorea.or.kr/free.jpg", "Type1"),
				row("2", "http://tong.visitkorea.or.kr/third-party.jpg", "Type3"),
				row("3", null, null)));

		Map<String, Map<String, Object>> byId = placesById();

		// 🔴 S15P21E201-1185 — 넣은 것은 http 인데 저장된 것은 https 다. 일부러 그렇다.
		//
		//    원천이 같은 호스트를 http 로도 https 로도 주는데, 평문 http 는 앱에서 한 장도
		//    안 보인다(안드로이드 API 28+ · iOS ATS · 웹 혼합 콘텐츠 차단). 적재기가
		//    PhotoUrlScheme 으로 아는 호스트만 https 로 바꾼다.
		//
		//    이 줄은 전에 http 를 그대로 기대했고, 그래서 CI 가 이 변경을 잡았다 —
		//    「원천이 준 문자열을 그대로 넣는다」가 이 시험이 지키던 계약이었기 때문이다.
		//    그 계약을 의도적으로 바꿨으므로 기대값도 함께 바꾼다.
		assertThat(byId.get("1").get("photo_url")).isEqualTo("https://tong.visitkorea.or.kr/free.jpg");
		assertThat(byId.get("1").get("photo_source")).isEqualTo("한국관광공사 공공누리 제1유형");

		// 🔴 Type3 는 사진 주소가 있어도 넣지 않는다 — 재사용 전 저작권자 허락이 필요하다.
		assertThat(byId.get("2").get("photo_url")).isNull();
		assertThat(byId.get("2").get("photo_source")).isNull();

		assertThat(byId.get("3").get("photo_url")).isNull();
		assertThat(byId.get("3").get("photo_source")).isNull();
	}

	private void load(List<TourApiPlaceRow> rows) {
		this.loader.saveChunk(rows, DATASET, OffsetDateTime.now());
	}

	private static TourApiPlaceRow row(String contentId, String firstImage, String copyrightType) {
		return new TourApiPlaceRow(contentId, "12", "A02", "A02010100", "장소 " + contentId,
				"부산광역시 어딘가", 35.1, 129.0, firstImage, copyrightType);
	}

	private Map<String, Map<String, Object>> placesById() {
		List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(
				"SELECT source_id, photo_url, photo_source FROM place WHERE source_type = 'TOURAPI'");
		return rows.stream().collect(java.util.stream.Collectors.toMap(r -> (String) r.get("source_id"), r -> r));
	}
}
