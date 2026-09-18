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
 * 목록 응답이 칸을 <b>실제로 싣는가</b> — 같은 병이 <b>세 번</b> 났다.
 *
 * <ul>
 *   <li>S15P21E201-1120 — 사진 주소·출처가 상세에만 있었다</li>
 *   <li>S15P21E201-1194 — 영문 주소가 상세에만 있었다. 목록은 이름만 두 언어였다</li>
 *   <li>S15P21E201-1205 — 「무엇을 찍은 사진인가」가 상세에만 있었다. 목록은 사진을 싣는데
 *       그것이 무엇을 찍은 것인지 말할 방법이 없었다</li>
 * </ul>
 *
 * <p>칸을 더해 놓고 값을 안 옮기면 화면은 여전히 그것을 못 그린다. 그 종류의 실수는 컴파일도
 * 통과하고 기존 검사도 통과한다 — 그래서 여기서 값이 끝까지 가는지를 본다.
 *
 * <p>🔴 이름이 한동안 {@code ListResponsePhotoFieldsTest} 였다. 사진에서 시작한 파일인데 주소도,
 * 「무엇을 찍은 사진인가」도 보게 되면서 <b>이름이 내용보다 좁아졌다.</b> 세 번째 주제가 들어올 때
 * 고쳤다 — 「나중에」로 미루면 다음 사람이 네 번째를 또 「사진」 파일에 넣는다.
 *
 * <p>🔴 이 파일이 재는 것은 <b>「칸이 있을 때 값이 실제로 옮겨지는가」</b>다.
 * <b>「상세에 있는 칸이 목록에 없는가」</b>는 다른 질문이고 {@code PlaceListDetailFieldGapTest} 가 잰다.
 * 목록에 칸을 더할 때 <b>「목록에도
 * 실었나」</b>를 사람이 기억하지 않아도 되게 하는 자리다.
 *
 * <p>DB 를 안 띄운다. 만드는 쪽({@code of}·{@code from})이 값을 옮기는지가 이 검사의 질문이고,
 * 그건 객체 하나로 답할 수 있다.
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
		// 🔴 지금 화면이 읽는 칸이다. 새 칸을 더하면서 이걸 건드리면 그 화면이 깨진다.
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
		// 차례를 통째로 고정한다. 맨 뒤만 보면 가운데를 끼워 넣어도 안 걸리고, 앞만 보면
		// 뒤에서 자리가 바뀌어도 안 걸린다.
		assertThat(componentNames(PlaceSummaryResponse.class)).containsExactly(
				"placeId", "nameKo", "nameEn", "category", "address", "lat", "lng", "matchedField",
				"photoUrl", "photoSource", "addressEn", "photoSubject");
		assertThat(componentNames(NearbyPlaceItem.class)).containsExactly(
				"placeId", "nameKo", "nameEn", "category", "address", "lat", "lng", "distanceM", "hasPhoto",
				"photoUrl", "photoSource", "addressEn", "photoSubject");
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

	/**
	 * 영문 주소가 붙은 장소.
	 *
	 * <p>🔴 {@code Place} 에는 영문 칸을 채우는 <b>자바 통로가 없다</b> — 수집 파이프라인이 SQL 로만
	 * 넣고 앱은 읽기만 한다(그래서 {@code imported(...)} 에도 setter 에도 없다). 그 칸 하나를
	 * 재려고 DB 를 띄우는 것은 이 검사가 답하려는 질문에 비해 과하므로 여기서만 직접 심는다.
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

	private static List<String> componentNames(Class<?> record) {
		return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
	}
}
