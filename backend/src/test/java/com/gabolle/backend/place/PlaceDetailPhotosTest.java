package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.gabolle.backend.place.service.ItineraryMembershipPort;
import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlacePhoto;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlacePhotoRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.service.PlaceDetailService;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * S15P21E201-1840 — 장소 상세의 사진 여러 장. 대표 사진이 맨 앞이고, 같은 사진은 두 번 싣지 않으며,
 * 사진이 없으면 {@code photos} 키 자체가 빠져 이미 배포된 앱이 지금처럼 그린다.
 */
class PlaceDetailPhotosTest {

	private static final UUID PLACE_ID = UUID.fromString("00000000-0000-0000-0000-000000001840");

	private static final String REP = "https://tong.visitkorea.or.kr/cms2/website/44/1074744.jpg";

	private final PlaceRepository placeRepository = mock(PlaceRepository.class);

	private final PlacePhotoRepository photoRepository = mock(PlacePhotoRepository.class);

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	private PlaceDetailService service;

	private Place place;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		PlaceFeatureRepository features = mock(PlaceFeatureRepository.class);
		UserPlaceCodeMapRepository codeMap = mock(UserPlaceCodeMapRepository.class);
		ObjectProvider<ItineraryMembershipPort> membership = mock(ObjectProvider.class);
		given(features.findByPlaceId(PLACE_ID)).willReturn(List.of());
		given(codeMap.findAll()).willReturn(List.of());
		this.service = new PlaceDetailService(this.placeRepository, features, codeMap, membership, this.objectMapper,
				this.photoRepository);
		this.place = Place.imported(PLACE_ID, "국제시장", "CITY", "부산 중구 중구로 36", 35.1, 129.0, "TOURAPI", "132191",
				OffsetDateTime.now(), OffsetDateTime.now(), "v1");
		given(this.placeRepository.findById(PLACE_ID)).willReturn(Optional.of(this.place));
	}

	@Test
	void 대표_사진이_맨_앞이고_갤러리가_순서대로_붙으며_같은_사진은_한_번만_싣는다() {
		this.place.attachPhoto(REP, "한국관광공사", Place.PhotoSubject.SELF, null);
		// 목(mock)은 given(...) 밖에서 먼저 만든다 — 안에서 만들면 Mockito 가 스텁을 겹쳐 잡는다.
		List<PlacePhoto> stored = List.of(
				photo(REP, "출처 : 부산관광아카이브", null),
				photo("https://j15e201.p.ssafy.io/photos/place-photos/busan-archive/METADATA004398.jpg",
						"출처 : 부산관광아카이브", new Place.PhotoLicense("공공누리 제1유형", null,
								"https://archive.visitbusan.net/dataSearch/view.nm?dataSid=METADATA004398&menuCd=36")));
		given(this.photoRepository.findByPlaceIdOrderByPositionAsc(PLACE_ID)).willReturn(stored);

		List<PlaceDetailResponse.Photo> photos = this.service.get(PLACE_ID, null).photos();

		assertThat(photos).extracting(PlaceDetailResponse.Photo::url).containsExactly(REP,
				"https://j15e201.p.ssafy.io/photos/place-photos/busan-archive/METADATA004398.jpg");
		assertThat(photos.get(0).source()).isEqualTo("한국관광공사");
		assertThat(photos.get(1).license().name()).isEqualTo("공공누리 제1유형");
	}

	@Test
	void 대표_사진이_없어도_갤러리만으로_목록을_만든다() {
		List<PlacePhoto> stored = List.of(photo("https://j15e201.p.ssafy.io/photos/a.jpg", "출처 : 부산관광아카이브", null));
		given(this.photoRepository.findByPlaceIdOrderByPositionAsc(PLACE_ID)).willReturn(stored);

		assertThat(this.service.get(PLACE_ID, null).photos()).hasSize(1);
	}

	@Test
	void 사진이_하나도_없으면_photos_키가_응답에서_빠진다() throws Exception {
		given(this.photoRepository.findByPlaceIdOrderByPositionAsc(PLACE_ID)).willReturn(List.of());

		String json = this.objectMapper.writeValueAsString(this.service.get(PLACE_ID, null));

		assertThat(json).doesNotContain("\"photos\"");
	}

	private static PlacePhoto photo(String url, String source, Place.PhotoLicense license) {
		PlacePhoto p = mock(PlacePhoto.class);
		given(p.getUrl()).willReturn(url);
		given(p.getSource()).willReturn(source);
		given(p.getLicense()).willReturn(license);
		return p;
	}
}
