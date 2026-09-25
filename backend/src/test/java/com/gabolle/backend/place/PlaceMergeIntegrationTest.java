package com.gabolle.backend.place;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 중복 장소 합치기와 되돌리기 (S15P21E201-1619). 합치기는 마이그레이션이 만든 DB 함수 {@code place_merge} 가 하고,
 * 이 시험은 그 함수를 진짜 DB 에서 부른다 — Flyway 가 시험 DB 에도 같은 함수를 만든다.
 *
 * <p>심는 것: 남는 줄(상가)과 합쳐질 줄(오픈스트리트맵). 두 사람이 저장했는데 한 사람은 둘 다 저장했다(겹침).
 * 표식은 둘 다 가진 종류 하나와 합쳐질 줄에만 있는 종류 하나. 합쳐질 줄에만 사진·영문 이름·축제 기간이 있다.
 */
class PlaceMergeIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlaceRepository placeRepository;

	private final String token = UUID.randomUUID().toString().substring(0, 8);

	private UUID keep;

	private UUID dup;

	private UUID bothSaver;

	private UUID dupSaver;

	@BeforeEach
	void seed() {
		this.bothSaver = user();
		this.dupSaver = user();
		this.keep = place("합치기시험 송정3대국밥 " + this.token, "SBIZ", null, null);
		this.dup = place("합치기시험 송정3대국밥 " + this.token, "OSM", "https://example.test/dup.jpg",
				"Songjeong Gukbap");
		tag(this.keep, "CATEGORY_TAG", "FOOD");
		tag(this.dup, "CATEGORY_TAG", "FOOD");
		tag(this.dup, "INTEREST_TAG", "NIGHT_VIEW");
		save(this.bothSaver, this.keep);
		save(this.bothSaver, this.dup);
		save(this.dupSaver, this.dup);
		this.jdbc.update("""
				INSERT INTO place_event_period (place_event_period_id, place_id, start_date, end_date)
				VALUES (?, ?, ?, ?)
				""", UUID.randomUUID(), this.dup, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
	}

	@AfterEach
	void cleanUp() {
		List<UUID> places = List.of(this.dup, this.keep);
		for (UUID placeId : places) {
			this.jdbc.update("UPDATE place SET curation_status = 'CURATED', merged_into = NULL WHERE place_id = ?",
					placeId);
		}
		for (UUID placeId : places) {
			this.jdbc.update("DELETE FROM place_merge_log WHERE merged_place_id = ? OR kept_place_id = ?", placeId,
					placeId);
			this.jdbc.update("DELETE FROM saved_place WHERE place_id = ?", placeId);
			this.jdbc.update("DELETE FROM place_feature WHERE place_id = ?", placeId);
			this.jdbc.update("DELETE FROM place WHERE place_id = ?", placeId);
		}
		this.jdbc.update("DELETE FROM app_user WHERE user_id IN (?, ?)", this.bothSaver, this.dupSaver);
	}

	@Test
	@DisplayName("🔴 합친 줄은 지우지 않고 MERGED 로 표시된다 — 찾아 주는 조회(이름 검색)에서 빠지고 번호로는 열린다")
	void theMergedRowIsMarkedNotDeleted() {
		merge();

		Place merged = this.placeRepository.findById(this.dup).orElseThrow();
		assertThat(merged.getCurationStatus().name()).isEqualTo("MERGED");
		assertThat(merged.getMergedInto()).isEqualTo(this.keep);
		assertThat(this.placeRepository.searchByName("%합치기시험 송정3대국밥 " + this.token + "%", Limit.of(10)))
				.extracting(Place::getPlaceId)
				.containsExactly(this.keep);
	}

	@Test
	@DisplayName("🔴 지금 쓰이는 참조는 남는 줄로 옮긴다 — 같은 사람이 둘 다 저장했으면 한 줄로")
	void referencesMoveToTheSurvivorWithoutDuplicates() {
		merge();

		assertThat(savedPlacesOf(this.bothSaver)).containsExactly(this.keep);
		assertThat(savedPlacesOf(this.dupSaver)).containsExactly(this.keep);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM place_event_period WHERE place_id = ?",
				Integer.class, this.keep)).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 남는 줄에 없는 것만 채운다 — 없는 표식 종류·사진·영문 이름은 오고, 있는 표식은 덮지 않는다")
	void onlyWhatTheSurvivorLacksIsFilled() {
		merge();

		assertThat(tagsOf(this.keep)).containsExactlyInAnyOrder("CATEGORY_TAG:FOOD", "INTEREST_TAG:NIGHT_VIEW");
		assertThat(tagsOf(this.dup)).containsExactly("CATEGORY_TAG:FOOD");
		assertThat(this.jdbc.queryForObject("SELECT photo_url FROM place WHERE place_id = ?", String.class, this.keep))
				.isEqualTo("https://example.test/dup.jpg");
		assertThat(this.jdbc.queryForObject("SELECT name_en FROM place WHERE place_id = ?", String.class, this.keep))
				.isEqualTo("Songjeong Gukbap");
	}

	@Test
	@DisplayName("🔴 되돌리면 전부 돌아온다 — 표시·옮긴 참조·뺀 겹침 행·채운 칸·표식·축제 기간")
	void unmergeRestoresEverything() {
		merge();

		this.jdbc.execute("SELECT place_unmerge('" + this.dup + "')");

		Place restored = this.placeRepository.findById(this.dup).orElseThrow();
		assertThat(restored.getCurationStatus().name()).isEqualTo("CURATED");
		assertThat(restored.getMergedInto()).isNull();
		assertThat(savedPlacesOf(this.bothSaver)).containsExactlyInAnyOrder(this.keep, this.dup);
		assertThat(savedPlacesOf(this.dupSaver)).containsExactly(this.dup);
		assertThat(tagsOf(this.keep)).containsExactly("CATEGORY_TAG:FOOD");
		assertThat(tagsOf(this.dup)).containsExactlyInAnyOrder("CATEGORY_TAG:FOOD", "INTEREST_TAG:NIGHT_VIEW");
		assertThat(this.jdbc.queryForObject("SELECT photo_url FROM place WHERE place_id = ?", String.class, this.keep))
				.isNull();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM place_event_period WHERE place_id = ?",
				Integer.class, this.dup)).isEqualTo(1);
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM place_merge_log WHERE merged_place_id = ? AND undone_at IS NULL", Integer.class,
				this.dup)).isZero();
	}

	@Test
	@DisplayName("같은 짝을 두 번 합쳐도 괜찮고, 이미 합쳐진 줄로는 합치지 않는다")
	void mergingIsIdempotentAndRefusesAMergedSurvivor() {
		merge();
		merge();

		UUID third = place("합치기시험 세번째 " + this.token, "OSM", null, null);
		try {
			assertThatThrownBy(() -> this.jdbc.execute("SELECT place_merge('" + third + "', '" + this.dup + "')"))
					.hasMessageContaining("남는 줄이 이미 합쳐진 줄이다");
		}
		finally {
			this.jdbc.update("DELETE FROM place WHERE place_id = ?", third);
		}
	}

	@Test
	@DisplayName("🔴 키 없는 표식(경사·조용함)을 둘 다 가져도 합쳐진다 — 운영 사본에서 유일성 위반으로 멈췄던 모양")
	void unkeyedFeaturesOnBothSidesDoNotBreakTheMerge() {
		// 경사는 둘 다, 조용함은 합쳐질 쪽만. 키 없는 표식은 (장소, 종류)가 유일하다(uq_place_feature_unkeyed).
		score(this.keep, "SLOPE_PERCENT", "3.1");
		score(this.dup, "SLOPE_PERCENT", "9.9");
		score(this.dup, "QUIETNESS_SCORE", "0.7");

		merge();

		assertThat(scoresOf(this.keep)).containsExactlyInAnyOrder("QUIETNESS_SCORE=0.7", "SLOPE_PERCENT=3.1");
		assertThat(scoresOf(this.dup)).as("남는 줄에 이미 있는 종류는 덮지 않고 합쳐진 줄에 둔다")
				.containsExactly("SLOPE_PERCENT=9.9");
	}

	private void merge() {
		this.jdbc.execute("SELECT place_merge('" + this.dup + "', '" + this.keep + "')");
	}

	/** 키 없는 점수형 표식 하나. */
	private void score(UUID placeId, String type, String value) {
		this.jdbc.update("""
				INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value, evidence_status,
				                           source_type, created_at)
				VALUES (?, ?, ?, NULL, ?::jsonb, 'ESTIMATED', 'TEST', now())
				""", UUID.randomUUID(), placeId, type, value);
	}

	private List<String> scoresOf(UUID placeId) {
		return this.jdbc.queryForList("SELECT feature_type || '=' || value::text FROM place_feature WHERE place_id = ? "
				+ "AND feature_key IS NULL ORDER BY 1", String.class, placeId);
	}

	private UUID user() {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("""
				INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at)
				VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)
				""", id, now, now);
		return id;
	}

	private UUID place(String name, String sourceType, String photoUrl, String nameEn) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO place (place_id, name_ko, name_en, category, lat, lng, source_type, source_id, photo_url,
				                   photo_source, created_at)
				VALUES (?, ?, ?, 'FOOD', 35.1787, 129.1996, ?, ?, ?, ?, now())
				""", id, name, nameEn, sourceType, "t-1619-" + id, photoUrl, photoUrl == null ? null : "시험 출처");
		return id;
	}

	private void tag(UUID placeId, String type, String key) {
		this.jdbc.update("""
				INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value, evidence_status,
				                           source_type, created_at)
				VALUES (?, ?, ?, ?, 'true'::jsonb, 'ESTIMATED', 'TEST', now())
				""", UUID.randomUUID(), placeId, type, key);
	}

	private void save(UUID userId, UUID placeId) {
		this.jdbc.update("INSERT INTO saved_place (saved_place_id, user_id, place_id, created_at) VALUES (?, ?, ?, now())",
				UUID.randomUUID(), userId, placeId);
	}

	private List<UUID> savedPlacesOf(UUID userId) {
		return this.jdbc.queryForList("SELECT place_id FROM saved_place WHERE user_id = ?", UUID.class, userId);
	}

	private List<String> tagsOf(UUID placeId) {
		return this.jdbc.queryForList(
				"SELECT feature_type || ':' || feature_key FROM place_feature WHERE place_id = ? ORDER BY 1", String.class,
				placeId);
	}
}
