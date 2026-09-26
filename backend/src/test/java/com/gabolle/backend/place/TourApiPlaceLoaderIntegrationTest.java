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
 * 관광공사 사진의 저작권 유형(cpyrhtDivCd) 필터링. {@code Type1}(공공누리 제1유형 — 출처를
 * 표시하면 자유 이용)만 {@code place.photo_url} 에 들어가고, {@code Type3}(제3자 저작물 —
 * 재사용 전 저작권자의 별도 허락이 필요)는 비운다.
 */
class TourApiPlaceLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "tourapi-test-202609";

	/** 이 시험이 쓰는 contentid 머리. 정본의 contentid(숫자)와도, 다른 시험의 머리와도 안 겹친다. */
	private static final String ID_PREFIX = "test-1748-tourapi-";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TourApiPlaceLoader loader;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		// 🔴 이 시험이 넣은 행만 지운다(S15P21E201-1748). 전에는 TOURAPI 행을 통째로 지웠는데, 마이그레이션이
		//    넣은 TOURAPI 정본(도시 탐험 시장 등)에 MANUAL 출처 태그가 붙은 뒤로(V20260918160000) 외래키
		//    fk_place_feature_place 에 막혔다. 통째로 지우면 남의 시험이 믿는 정본도 사라진다.
		//    태그는 출처값이 아니라 장소로 찾아 지운다 — 적재기가 붙이는 표식의 출처가 바뀌어도 안 막힌다.
		this.jdbcTemplate.update("""
				DELETE FROM place_feature WHERE place_id IN (
				    SELECT place_id FROM place WHERE source_type = 'TOURAPI' AND source_id LIKE ?)
				""", ID_PREFIX + "%");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'TOURAPI' AND source_id LIKE ?", ID_PREFIX + "%");
	}

	@Test
	@DisplayName("🔴 Type1(공공누리 제1유형)만 photo_url 에 들어간다 — Type3(제3자 저작물)는 비운다")
	void onlyType1PhotosAreLoaded() {
		load(List.of(
				row("1", "http://tong.visitkorea.or.kr/free.jpg", "Type1"),
				row("2", "http://tong.visitkorea.or.kr/third-party.jpg", "Type3"),
				row("3", null, null)));

		Map<String, Map<String, Object>> byId = placesById();

		// 넣은 것은 http 인데 기대값이 https 인 것은 일부러다 — 평문 http 사진은 앱에서
		// 한 장도 안 보여서(안드로이드 API 28+ · iOS ATS · 웹 혼합 콘텐츠 차단)
		// 적재기가 PhotoUrlScheme 으로 아는 호스트만 https 로 바꾼다.
		assertThat(byId.get("1").get("photo_url")).isEqualTo("https://tong.visitkorea.or.kr/free.jpg");
		assertThat(byId.get("1").get("photo_source")).isEqualTo("한국관광공사 공공누리 제1유형");

		// Type3 는 사진 주소가 있어도 넣지 않는다.
		assertThat(byId.get("2").get("photo_url")).isNull();
		assertThat(byId.get("2").get("photo_source")).isNull();

		assertThat(byId.get("3").get("photo_url")).isNull();
		assertThat(byId.get("3").get("photo_source")).isNull();
	}

	private void load(List<TourApiPlaceRow> rows) {
		this.loader.saveChunk(rows, DATASET, OffsetDateTime.now());
	}

	private static TourApiPlaceRow row(String contentId, String firstImage, String copyrightType) {
		return new TourApiPlaceRow(ID_PREFIX + contentId, "12", "A02", "A02010100", "장소 " + contentId,
				"부산광역시 어딘가", 35.1, 129.0, firstImage, copyrightType);
	}

	private Map<String, Map<String, Object>> placesById() {
		List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(
				"SELECT source_id, photo_url, photo_source FROM place WHERE source_type = 'TOURAPI' AND source_id LIKE ?",
				ID_PREFIX + "%");
		// 머리를 떼고 돌려준다 — 시험 본문은 "1"·"2"·"3" 으로 읽는다.
		return rows.stream().collect(java.util.stream.Collectors.toMap(
				r -> ((String) r.get("source_id")).substring(ID_PREFIX.length()), r -> r));
	}
}
