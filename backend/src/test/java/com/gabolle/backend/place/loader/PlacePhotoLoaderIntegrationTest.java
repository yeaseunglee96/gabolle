package com.gabolle.backend.place.loader;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.Place.PhotoLicense;
import com.gabolle.backend.place.domain.Place.PhotoSubject;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사진 적재기가 장소를 출처 열쇠로 찾고, 있는 사진은 덮지 않는다 (S15P21E201-1606).
 *
 * <p>관광공사 장소의 번호를 일부러 무작위로 만든다 — 운영의 「구상반려암」·「백양산」은 마이그레이션이
 * 손수 넣어 번호가 계산 규칙({@link TourApiPlaceLoader#placeIdOf})과 다르고, 옛 적재기는 그래서
 * 그 둘을 못 찾았다.
 */
class PlacePhotoLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String PREFIX = "photo-1606-";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceRepository placeRepository;

	@Autowired
	private PlacePhotoLoader loader;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM place WHERE source_id LIKE ?", PREFIX + "%");
	}

	@Test
	@DisplayName("🔴 번호가 계산 규칙과 다른 관광공사 장소도, 상가·OSM 장소도 출처 열쇠로 찾아 붙인다")
	void placesAreFoundBySourceKey() {
		UUID tour = place("TOURAPI", PREFIX + "tour", null, null);
		UUID osm = place("OSM", PREFIX + "osm", null, null);

		PlacePhotoLoader.Result result = this.loader.load(List.of(
				row("TOURAPI", PREFIX + "tour", null),
				row("OSM", PREFIX + "osm", null)));

		assertThat(result).isEqualTo(new PlacePhotoLoader.Result(2, 0, 0));
		assertThat(tour).isNotEqualTo(TourApiPlaceLoader.placeIdOf(PREFIX + "tour"));
		assertThat(photoOf(tour).get("photo_url")).isEqualTo("https://example.com/TOURAPI.jpg");
		assertThat(photoOf(osm).get("photo_url")).isEqualTo("https://example.com/OSM.jpg");
		assertThat(photoOf(osm).get("photo_subject")).isEqualTo("SELF");
	}

	@Test
	@DisplayName("🔴 이미 사진이 있는 장소는 그대로 둔다 — 다시 돌려도 손으로 넣은 사진이 안 바뀐다")
	void anExistingPhotoIsKept() {
		UUID sbiz = place("SBIZ", PREFIX + "sbiz", "https://example.com/old.jpg", "옛 출처");

		PlacePhotoLoader.Result result = this.loader.load(List.of(row("SBIZ", PREFIX + "sbiz", null)));

		assertThat(result).isEqualTo(new PlacePhotoLoader.Result(0, 1, 0));
		Map<String, Object> photo = photoOf(sbiz);
		assertThat(photo.get("photo_url")).isEqualTo("https://example.com/old.jpg");
		assertThat(photo.get("photo_source")).isEqualTo("옛 출처");
		assertThat(photo.get("photo_subject")).isNull();
	}

	@Test
	@DisplayName("🔴 출처가 다르면 번호가 같아도 다른 장소다 — 사진이 엇갈려 붙지 않는다")
	void theSourceTypeIsPartOfTheKey() {
		UUID osm = place("OSM", PREFIX + "same", null, null);
		UUID sbiz = place("SBIZ", PREFIX + "same", null, null);

		PlacePhotoLoader.Result result = this.loader.load(List.of(
				row("OSM", PREFIX + "same", null),
				row("SBIZ", PREFIX + "same", null)));

		assertThat(result).isEqualTo(new PlacePhotoLoader.Result(2, 0, 0));
		assertThat(photoOf(osm).get("photo_url")).isEqualTo("https://example.com/OSM.jpg");
		assertThat(photoOf(sbiz).get("photo_url")).isEqualTo("https://example.com/SBIZ.jpg");
	}

	@Test
	@DisplayName("없는 장소는 실패하지 않고 센다 — 장소 적재를 안 돌렸거나 열쇠가 틀린 것이다")
	void aMissingPlaceIsCounted() {
		PlacePhotoLoader.Result result = this.loader.load(List.of(row("KAKAO_LOCAL", PREFIX + "missing", null)));

		assertThat(result).isEqualTo(new PlacePhotoLoader.Result(0, 0, 1));
	}

	@Test
	@DisplayName("🔴 라이선스 이름·주소·원본 파일 페이지가 저장되고 장소에서 다시 읽힌다")
	void theLicenseIsStored() {
		UUID osm = place("OSM", PREFIX + "wiki", null, null);
		PhotoLicense license = new PhotoLicense("CC BY-SA 3.0", "https://creativecommons.org/licenses/by-sa/3.0",
				"https://commons.wikimedia.org/wiki/File:Busan_Modern_History_Museum-01.jpg");

		this.loader.load(List.of(row("OSM", PREFIX + "wiki", license)));

		assertThat(this.placeRepository.findById(osm).orElseThrow().getPhotoLicense()).isEqualTo(license);
	}

	private UUID place(String sourceType, String sourceId, String photoUrl, String photoSource) {
		UUID placeId = UUID.randomUUID();
		this.placeRepository.save(Place.imported(placeId, "사진 시험 " + sourceId, "CITY", "부산광역시 어딘가", 35.1,
				129.0, sourceType, sourceId, OffsetDateTime.now(), OffsetDateTime.now(), "test-1606", photoUrl,
				photoSource));
		return placeId;
	}

	private static PlacePhotoRow row(String sourceType, String sourceId, PhotoLicense license) {
		return new PlacePhotoRow(sourceType, sourceId, "https://example.com/" + sourceType + ".jpg", "출처 " + sourceType,
				PhotoSubject.SELF, license);
	}

	private Map<String, Object> photoOf(UUID placeId) {
		return this.jdbcTemplate.queryForMap(
				"SELECT photo_url, photo_source, photo_subject FROM place WHERE place_id = ?", placeId);
	}
}
