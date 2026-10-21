package com.gabolle.backend.place;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.gabolle.backend.place.api.NearbyPlaceItem;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.domain.Place;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 목록 응답에 칸을 더해 놓고 값을 안 옮기면 컴파일도 기존 검사도 통과하므로, 만드는
 * 쪽({@code of}·{@code from})이 값을 끝까지 옮기는지 본다.
 *
 * <p>"상세에 있는 칸이 목록에 없는가" 는 다른 질문이고 {@code PlaceListDetailFieldGapTest}
 * 가 잰다. 객체 하나로 답할 수 있어 DB 를 안 띄운다.
 */
class ListResponseFieldsTest {

	private static final String PHOTO = "https://tong.visitkorea.or.kr/cms/haeundae.jpg";

	private static final String SOURCE = "한국관광공사 (공공누리 제1유형)";

	private static final String ADDRESS_EN = "Udong, Haeundae-gu, Busan";

	private static Place placeWithPhoto(String photoUrl, String photoSource) {
		return Place.imported(UUID.randomUUID(), "해운대해수욕장", "SEA_BEACH", "부산 해운대구 우동",
				35.1585, 129.1598, "TOURAPI", "264570",
				OffsetDateTime.now(), null, "test", photoUrl, photoSource);
	}

	@Test
	@DisplayName("검색 목록은 사진 주소와 출처를 같이 싣는다")
	void searchSummaryCarriesPhotoUrlAndSource() {
		PlaceSummaryResponse response = PlaceSummaryResponse.of(placeWithPhoto(PHOTO, SOURCE),
				PlaceSummaryResponse.MatchedField.NAME_KO);

		assertThat(response.photoUrl()).isEqualTo(PHOTO);
		assertThat(response.photoSource()).isEqualTo(SOURCE);
	}

	@Test
	@DisplayName("갈래 목록도 같이 싣는다 — 만드는 통로가 둘이라 한쪽만 고치기 쉽다")
	void facetSummaryCarriesPhotoUrlAndSource() {
		PlaceSummaryResponse response = PlaceSummaryResponse.ofFacetMatch(placeWithPhoto(PHOTO, SOURCE));

		assertThat(response.photoUrl()).isEqualTo(PHOTO);
		assertThat(response.photoSource()).isEqualTo(SOURCE);
	}

	@Test
	@DisplayName("근처 장소도 같이 싣고, 기존 hasPhoto 는 그대로 참이다")
	void nearbyItemCarriesPhotoUrlAndKeepsHasPhoto() {
		NearbyPlaceItem item = NearbyPlaceItem.from(placeWithPhoto(PHOTO, SOURCE), 120L);

		assertThat(item.photoUrl()).isEqualTo(PHOTO);
		assertThat(item.photoSource()).isEqualTo(SOURCE);
		// 지금 화면이 읽는 칸이라 새 칸을 더하면서 건드리면 안 된다.
		assertThat(item.hasPhoto()).isTrue();
	}

	@Test
	@DisplayName("사진이 없으면 두 칸 다 비어 나간다 — 빈 문자열을 지어내지 않는다")
	void placesWithoutAPhotoCarryNulls() {
		Place bare = placeWithPhoto(null, null);

		assertThat(PlaceSummaryResponse.of(bare, null).photoUrl()).isNull();
		assertThat(PlaceSummaryResponse.of(bare, null).photoSource()).isNull();
		assertThat(NearbyPlaceItem.from(bare, 10L).photoUrl()).isNull();
		assertThat(NearbyPlaceItem.from(bare, 10L).hasPhoto()).isFalse();
	}

	@Test
	@DisplayName("🔴 새 칸은 맨 뒤에 있다 — 앞자리를 밀면 이미 배포된 앱이 깨진다")
	void newFieldsAreAppendedAtTheEnd() {
		// 맨 뒤만 보면 가운데를 끼워 넣어도 안 걸리므로 차례를 통째로 고정한다.
		assertThat(componentNames(PlaceSummaryResponse.class)).containsExactly(
				"placeId", "nameKo", "nameEn", "category", "address", "lat", "lng", "matchedField",
				"photoUrl", "photoSource", "addressEn", "photoSubject", "photoLicense", "localNames", "localAddresses");
		assertThat(componentNames(NearbyPlaceItem.class)).containsExactly(
				"placeId", "nameKo", "nameEn", "category", "address", "lat", "lng", "distanceM", "hasPhoto",
				"photoUrl", "photoSource", "addressEn", "photoSubject", "photoLicense", "localNames", "localAddresses");
	}

	@Test
	@DisplayName("목록 셋이 영문 주소를 싣는다 — 이름만 두 언어이고 주소는 한글이던 자리")
	void listsCarryAddressEn() {
		Place place = placeWithAddressEn(ADDRESS_EN);

		assertThat(PlaceSummaryResponse.of(place, PlaceSummaryResponse.MatchedField.NAME_KO).addressEn())
				.isEqualTo(ADDRESS_EN);
		assertThat(PlaceSummaryResponse.ofFacetMatch(place).addressEn()).isEqualTo(ADDRESS_EN);
		assertThat(NearbyPlaceItem.from(place, 120L).addressEn()).isEqualTo(ADDRESS_EN);
	}

	@Test
	@DisplayName("🔴 한글 주소가 영문 칸으로 새지 않는다 — getAddress() 를 두 번 넘겨도 컴파일은 통과한다")
	void addressEnIsNotTheKoreanAddress() {
		Place place = placeWithAddressEn(ADDRESS_EN);

		PlaceSummaryResponse summary = PlaceSummaryResponse.of(place, null);
		assertThat(summary.address()).isEqualTo(place.getAddress());
		assertThat(summary.addressEn()).isEqualTo(place.getAddressEn()).isNotEqualTo(summary.address());

		NearbyPlaceItem nearby = NearbyPlaceItem.from(place, 10L);
		assertThat(nearby.address()).isEqualTo(place.getAddress());
		assertThat(nearby.addressEn()).isEqualTo(place.getAddressEn()).isNotEqualTo(nearby.address());
	}

	@Test
	@DisplayName("영문 주소가 없으면 그 키는 아예 안 나간다 — 한글로 대신 채우지 않는다")
	void placesWithoutAnEnglishAddressCarryNull() {
		Place place = placeWithPhoto(PHOTO, SOURCE);

		assertThat(PlaceSummaryResponse.of(place, null).addressEn()).isNull();
		assertThat(PlaceSummaryResponse.ofFacetMatch(place).addressEn()).isNull();
		assertThat(NearbyPlaceItem.from(place, 10L).addressEn()).isNull();
	}

	@Test
	@DisplayName("🔴 목록 둘이 일본어·중국어 주소를 싣는다 — 이름만 번역되고 주소는 영어·한국어이던 자리(S15P21E201-1876)")
	void listsCarryLocalAddresses() {
		Place place = placeWithPhoto(PHOTO, SOURCE);
		ReflectionTestUtils.setField(place, "addressJa", "釜山広域市 海雲台区 ヘウンデヘビョンロ264");
		ReflectionTestUtils.setField(place, "addressZhHant", " 釜山廣域市海雲臺區海雲臺海邊路264 ");
		ReflectionTestUtils.setField(place, "addressZhHans", "  ");

		// 빈 칸은 빠지고, 앞뒤 공백은 뗀다 — 이름(localNames)과 같은 규칙.
		assertThat(PlaceSummaryResponse.of(place, null).localAddresses()).containsExactly(
				java.util.Map.entry("ja", "釜山広域市 海雲台区 ヘウンデヘビョンロ264"),
				java.util.Map.entry("zh-Hant", "釜山廣域市海雲臺區海雲臺海邊路264"));
		assertThat(PlaceSummaryResponse.ofFacetMatch(place).localAddresses()).containsKeys("ja", "zh-Hant");
		assertThat(NearbyPlaceItem.from(place, 10L).localAddresses()).containsKeys("ja", "zh-Hant");
		assertThat(PlaceSummaryResponse.of(placeWithPhoto(PHOTO, SOURCE), null).localAddresses()).isEmpty();
	}

	/**
	 * {@code Place} 에는 영문 칸을 채우는 자바 통로가 없어(수집 파이프라인이 SQL 로만 넣는다)
	 * 리플렉션으로 직접 심는다.
	 */
	private static Place placeWithAddressEn(String addressEn) {
		Place place = placeWithPhoto(PHOTO, SOURCE);
		ReflectionTestUtils.setField(place, "addressEn", addressEn);
		return place;
	}

	@Test
	@DisplayName("목록 셋이 「무엇을 찍은 사진인가」를 싣는다 — 사진은 싣는데 그 뜻은 못 싣던 자리")
	void listsCarryPhotoSubject() {
		Place place = placeWithPhoto(PHOTO, SOURCE);
		ReflectionTestUtils.setField(place, "photoSubject", Place.PhotoSubject.VENUE);

		assertThat(PlaceSummaryResponse.of(place, null).photoSubject()).isEqualTo(Place.PhotoSubject.VENUE);
		assertThat(PlaceSummaryResponse.ofFacetMatch(place).photoSubject()).isEqualTo(Place.PhotoSubject.VENUE);
		assertThat(NearbyPlaceItem.from(place, 30L).photoSubject()).isEqualTo(Place.PhotoSubject.VENUE);
	}

	@Test
	@DisplayName("🔴 SELF 를 VENUE 로 바꿔 보내지 않는다 — 뱃지가 엉뚱한 사진에 붙는다")
	void photoSubjectIsCarriedAsIs() {
		Place place = placeWithPhoto(PHOTO, SOURCE);
		ReflectionTestUtils.setField(place, "photoSubject", Place.PhotoSubject.SELF);

		assertThat(PlaceSummaryResponse.of(place, null).photoSubject())
				.isEqualTo(place.getPhotoSubject()).isEqualTo(Place.PhotoSubject.SELF);
		assertThat(NearbyPlaceItem.from(place, 30L).photoSubject())
				.isEqualTo(place.getPhotoSubject()).isEqualTo(Place.PhotoSubject.SELF);
	}

	@Test
	@DisplayName("무엇을 찍었는지 모르면 그 키는 아예 안 나간다 — SELF 로 지어내지 않는다")
	void unknownPhotoSubjectCarriesNull() {
		Place place = placeWithPhoto(PHOTO, SOURCE);

		assertThat(PlaceSummaryResponse.of(place, null).photoSubject()).isNull();
		assertThat(PlaceSummaryResponse.ofFacetMatch(place).photoSubject()).isNull();
		assertThat(NearbyPlaceItem.from(place, 30L).photoSubject()).isNull();
	}

	/** 위키미디어 사진(CC BY-SA 등)은 라이선스 이름과 링크를 같이 보여야 쓸 수 있다 (S15P21E201-1606). */
	@Test
	@DisplayName("🔴 목록 셋이 사진 라이선스를 싣는다 — 칸만 더하고 값을 안 옮기면 컴파일은 통과한다")
	void listsCarryPhotoLicense() {
		Place place = placeWithPhoto(null, null);
		Place.PhotoLicense license = new Place.PhotoLicense("CC BY-SA 3.0",
				"https://creativecommons.org/licenses/by-sa/3.0",
				"https://commons.wikimedia.org/wiki/File:Busan_Modern_History_Museum-01.jpg");
		place.attachPhoto(PHOTO, "Wikimedia Commons", Place.PhotoSubject.SELF, license);

		assertThat(PlaceSummaryResponse.of(place, null).photoLicense()).isEqualTo(license);
		assertThat(PlaceSummaryResponse.ofFacetMatch(place).photoLicense()).isEqualTo(license);
		assertThat(NearbyPlaceItem.from(place, 30L).photoLicense()).isEqualTo(license);
		assertThat(NearbyPlaceItem.from(placeWithPhoto(PHOTO, SOURCE), 30L).photoLicense())
				.as("라이선스 칸으로 받은 것이 없으면 비어 나간다").isNull();
	}

	private static List<String> componentNames(Class<?> record) {
		return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
	}
}
