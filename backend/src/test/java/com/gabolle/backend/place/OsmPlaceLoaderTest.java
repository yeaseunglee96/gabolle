package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.loader.OsmPlaceCategory;
import com.gabolle.backend.place.loader.OsmPlaceLoader;
import com.gabolle.backend.place.loader.OsmPoiReader;
import com.gabolle.backend.place.loader.OsmPoiRow;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * OSM 부산 장소를 {@code place} 로 옮기는 규칙.
 *
 * <p>재는 것은 셋이다 — <b>모르는 것을 지어내지 않는가</b>, <b>숙소를 식당으로 만들지 않는가</b>,
 * <b>같은 곳을 두 번 넣지 않는가</b>. 셋 다 틀려도 적재는 성공으로 끝나고 개수만 이상해진다.
 */
class OsmPlaceLoaderTest {

	private final PlaceRepository placeRepository = mock(PlaceRepository.class);

	// ── 갈래 대응 ──────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 호텔 안에 식당이 있어도 숙소다 — 차례를 바꾸면 모텔 601곳이 음식점이 된다")
	void lodgingWinsOverTheRestaurantInsideIt() {
		assertThat(OsmPlaceCategory.of(Map.of("tourism", "hotel", "amenity", "restaurant")))
				.isEqualTo("LODGING");
	}

	@Test
	@DisplayName("숙소 다섯 갈래가 모두 LODGING 이다")
	void everyLodgingTagMapsToLodging() {
		for (String kind : List.of("hotel", "motel", "guest_house", "hostel", "apartment")) {
			assertThat(OsmPlaceCategory.of(Map.of("tourism", kind))).as(kind).isEqualTo("LODGING");
		}
	}

	@Test
	@DisplayName("카페와 빵집은 CAFE_HEALING, 식당·술집은 FOOD")
	void foodAndCafeAreToldApart() {
		assertThat(OsmPlaceCategory.of(Map.of("amenity", "cafe"))).isEqualTo("CAFE_HEALING");
		assertThat(OsmPlaceCategory.of(Map.of("shop", "bakery"))).isEqualTo("CAFE_HEALING");
		assertThat(OsmPlaceCategory.of(Map.of("amenity", "restaurant"))).isEqualTo("FOOD");
		assertThat(OsmPlaceCategory.of(Map.of("amenity", "pub"))).isEqualTo("FOOD");
	}

	@Test
	@DisplayName("🔴 모르는 태그는 갈래를 지어내지 않는다 — 은행·주유소가 여행 후보에 섞이면 안 된다")
	void unknownTagsGetNoCategory() {
		assertThat(OsmPlaceCategory.of(Map.of("amenity", "bank"))).isNull();
		assertThat(OsmPlaceCategory.of(Map.of("amenity", "fuel"))).isNull();
		assertThat(OsmPlaceCategory.of(Map.of("shop", "supermarket"))).isNull();
		assertThat(OsmPlaceCategory.of(Map.of())).isNull();
		assertThat(OsmPlaceCategory.of(null)).isNull();
	}

	// ── 읽기 ──────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 버린 줄을 이유별로 센다 — 「적게 들어갔다」의 원인을 개수만 보고 알 수 있어야 한다")
	void countsWhyEachLineWasDropped(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("poi.ndjson");
		Files.writeString(file, String.join("\n",
				// 넣는다
				"{\"type\":\"node\",\"id\":1,\"lat\":35.1,\"lon\":129.0,"
						+ "\"tags\":{\"name\":\"어느식당\",\"amenity\":\"restaurant\"}}",
				// 이름이 없다
				"{\"type\":\"node\",\"id\":2,\"lat\":35.1,\"lon\":129.0,\"tags\":{\"amenity\":\"cafe\"}}",
				// 아는 갈래가 없다
				"{\"type\":\"node\",\"id\":3,\"lat\":35.1,\"lon\":129.0,"
						+ "\"tags\":{\"name\":\"어느은행\",\"amenity\":\"bank\"}}",
				// 좌표가 없다
				"{\"type\":\"node\",\"id\":4,\"tags\":{\"name\":\"어느카페\",\"amenity\":\"cafe\"}}"),
				StandardCharsets.UTF_8);

		List<OsmPoiRow> taken = new ArrayList<>();
		OsmPoiReader.Counts counts = OsmPoiReader.read(file, 10, taken::addAll);

		assertThat(counts.total()).isEqualTo(4);
		assertThat(counts.taken()).isEqualTo(1);
		assertThat(counts.noName()).isEqualTo(1);
		assertThat(counts.noCategory()).isEqualTo(1);
		assertThat(counts.noCoordinates()).isEqualTo(1);
		assertThat(taken).singleElement().satisfies((row) -> {
			assertThat(row.name()).isEqualTo("어느식당");
			assertThat(row.category()).isEqualTo("FOOD");
			assertThat(row.address()).as("주소 태그가 없으면 null 이다 — 지어내지 않는다").isNull();
		});
	}

	@Test
	@DisplayName("주소는 있는 조각만 큰 단위부터 이어 붙인다")
	void addressIsJoinedFromWhateverExists(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("poi.ndjson");
		Files.writeString(file, "{\"type\":\"node\",\"id\":9,\"lat\":35.1,\"lon\":129.0,\"tags\":"
				+ "{\"name\":\"어느카페\",\"amenity\":\"cafe\",\"addr:city\":\"부산광역시\","
				+ "\"addr:street\":\"해운대해변로\",\"addr:housenumber\":\"264\"}}", StandardCharsets.UTF_8);

		List<OsmPoiRow> taken = new ArrayList<>();
		OsmPoiReader.read(file, 10, taken::addAll);

		assertThat(taken).singleElement()
				.extracting(OsmPoiRow::address)
				.isEqualTo("부산광역시 해운대해변로 264");
	}

	// ── 넣기 ──────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 같은 OSM 번호는 한 번만 넣는다 — 같은 파일을 두 번 돌려도 행이 두 배가 되지 않는다")
	void theSameOsmIdIsStoredOnce() {
		when(this.placeRepository.findAllById(anyIterable())).thenReturn(List.of());
		OsmPlaceLoader loader = new OsmPlaceLoader(this.placeRepository);

		int saved = loader.saveChunk(List.of(
				new OsmPoiRow(11L, "어느식당", "FOOD", null, 35.1, 129.0),
				new OsmPoiRow(11L, "어느식당", "FOOD", null, 35.1, 129.0)),
				"osm-test", OffsetDateTime.now());

		assertThat(saved).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 이미 있는 장소는 고치지 않는다 — 먼저 들어온 정본을 존중한다")
	void existingPlacesAreLeftAlone() {
		Place already = Place.imported(OsmPlaceLoader.placeIdOf(12L), "먼저 들어온 이름", "FOOD", null,
				35.1, 129.0, "TOURAPI", "12", OffsetDateTime.now(), null, "tour-1");
		when(this.placeRepository.findAllById(anyIterable())).thenReturn(List.of(already));
		OsmPlaceLoader loader = new OsmPlaceLoader(this.placeRepository);

		int saved = loader.saveChunk(
				List.of(new OsmPoiRow(12L, "OSM 이름", "FOOD", null, 35.1, 129.0)),
				"osm-test", OffsetDateTime.now());

		assertThat(saved).isZero();
		verify(this.placeRepository, never()).saveAll(anyIterable());
	}

	@Test
	@DisplayName("출처와 번호를 그대로 남긴다 — 어디서 온 행인지 되짚을 수 있어야 한다")
	void keepsTheSourceAndItsId() {
		when(this.placeRepository.findAllById(anyIterable())).thenReturn(List.of());
		OsmPlaceLoader loader = new OsmPlaceLoader(this.placeRepository);

		loader.saveChunk(List.of(new OsmPoiRow(368601281L, "부산게스트하우스", "LODGING", null, 35.1591, 129.1071)),
				"osm-busan-2026-09", OffsetDateTime.now());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Place>> saved = ArgumentCaptor.forClass(List.class);
		verify(this.placeRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).singleElement().satisfies((place) -> {
			assertThat(place.getSourceType()).isEqualTo("OSM");
			assertThat(place.getSourceId()).isEqualTo("368601281");
			assertThat(place.getCategory()).isEqualTo("LODGING");
		});
	}
}
