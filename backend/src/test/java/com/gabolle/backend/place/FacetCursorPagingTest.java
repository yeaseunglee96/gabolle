package com.gabolle.backend.place;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import com.gabolle.backend.place.api.PlacePageResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.PlaceRequestException;
import com.gabolle.backend.place.service.PlaceSearchService;
import com.gabolle.backend.place.service.SearchCursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 갈래 목록의 이어받기 커서 — S15P21E201-2060.
 *
 * <p>2026-10-16 운영 웹 점검에서 {@code /explore} 의 축제(76곳)가 20곳에서 끊겼다. API 가
 * {@code hasNext:true} 인데 {@code nextCursor:null} 이라 앞 쪽 뒤를 아무도 못 불렀고, 한 쪽이 최대 50개라
 * 한 번에 받는 길도 없었다. DB 없이 돈다 — 저장소는 가짜이고, 정렬된 목록에서 앞 N행만 돌려준다.
 */
class FacetCursorPagingTest {

	private static final String TYPE = "INTEREST_TAG";

	private static final String KEY = "FESTIVAL";

	private final PlaceRepository placeRepository = mock(PlaceRepository.class);

	private final PlaceFeatureRepository featureRepository = mock(PlaceFeatureRepository.class);

	private final PlaceSearchService service = new PlaceSearchService(this.placeRepository, this.featureRepository);

	/** DB 가 이름순으로 돌려주는 줄 — 앞에서부터 Limit 개만 나간다. */
	private final List<Place> catalog = new ArrayList<>();

	private final Map<UUID, PlaceFeature> featureByPlace = new HashMap<>();

	@BeforeEach
	void wireRepositories() {
		when(this.placeRepository.findHavingFeature(eq(TYPE), eq(KEY), any(Limit.class))).thenAnswer(invocation -> {
			int max = ((Limit) invocation.getArgument(2)).max();
			return List.copyOf(this.catalog.subList(0, Math.min(max, this.catalog.size())));
		});
		when(this.featureRepository.findByPlaceIdIn(anyCollection())).thenAnswer(invocation -> {
			Collection<UUID> ids = invocation.getArgument(0);
			return ids.stream().map(this.featureByPlace::get).toList();
		});
	}

	private void addPlaces(int count) {
		IntStream.rangeClosed(1, count).forEach(number -> addPlace(String.format("축제%03d", number), true));
	}

	private void addPlace(String nameKo, boolean present) {
		Place place = Place.imported(UUID.randomUUID(), nameKo, "FESTIVAL_EVENT", "부산", 35.1, 129.0, "TOURAPI",
				UUID.randomUUID().toString(), OffsetDateTime.now(), null, "test", null, null);
		this.catalog.add(place);
		PlaceFeature feature = mock(PlaceFeature.class);
		when(feature.getPlaceId()).thenReturn(place.getPlaceId());
		when(feature.getFeatureType()).thenReturn(TYPE);
		when(feature.getFeatureKey()).thenReturn(KEY);
		// 확인된 부재(값이 false)는 갈래 목록에서 빠진다 — 서비스가 걸러야 하는 줄.
		when(feature.indicatesPresence()).thenReturn(present);
		this.featureByPlace.put(place.getPlaceId(), feature);
	}

	/** 커서를 따라 끝까지 간다. 각 쪽을 순서대로 돌려준다. */
	private List<PlacePageResponse> walk(int limit) {
		List<PlacePageResponse> pages = new ArrayList<>();
		String cursor = null;
		for (int guard = 0; guard < 200; guard++) {
			PlacePageResponse page = this.service.searchByFacet(TYPE, KEY, limit, cursor);
			pages.add(page);
			if (!page.hasNext()) {
				return pages;
			}
			cursor = page.nextCursor();
		}
		throw new AssertionError("커서가 끝나지 않는다");
	}

	/** 거절되면 그 코드를, 아니면 실패한다. */
	private static String rejectionCodeOf(ThrowingCallable call) {
		Throwable thrown = catchThrowable(call);
		assertThat(thrown).isInstanceOf(PlaceRequestException.class);
		return ((PlaceRequestException) thrown).getCode();
	}

	private static List<String> names(PlacePageResponse page) {
		return page.items().stream().map(PlaceSummaryResponse::nameKo).toList();
	}

	@Test
	@DisplayName("🔴 hasNext 가 참이면 nextCursor 도 있다 — 전에는 null 이라 20곳 뒤를 못 봤다")
	void hasNextComesWithACursor() {
		addPlaces(76);

		PlacePageResponse first = this.service.searchByFacet(TYPE, KEY, 20, null);

		assertThat(first.items()).hasSize(20);
		assertThat(first.hasNext()).isTrue();
		assertThat(first.nextCursor()).isNotBlank();
		assertThat(first.rankTruncated()).isFalse();
	}

	@Test
	@DisplayName("🔴 커서를 따라가면 76곳이 한 곳도 빠지거나 겹치지 않고 20 · 20 · 20 · 16 으로 나온다")
	void followingTheCursorReachesEveryPlaceOnce() {
		addPlaces(76);

		List<PlacePageResponse> pages = walk(20);

		assertThat(pages).extracting(page -> page.items().size()).containsExactly(20, 20, 20, 16);
		List<String> all = pages.stream().flatMap(page -> names(page).stream()).toList();
		assertThat(all).hasSize(76).doesNotHaveDuplicates();
		assertThat(all).isEqualTo(this.catalog.stream().map(Place::getNameKo).toList());
		PlacePageResponse last = pages.get(pages.size() - 1);
		assertThat(last.hasNext()).isFalse();
		assertThat(last.nextCursor()).isNull();
	}

	@Test
	@DisplayName("한 쪽 최대치(50)로도 끝까지 간다 — 50 + 26")
	void maxPageSizeAlsoWalksToTheEnd() {
		addPlaces(76);

		List<PlacePageResponse> pages = walk(50);

		assertThat(pages).extracting(page -> page.items().size()).containsExactly(50, 26);
		assertThat(pages.stream().flatMap(page -> names(page).stream()).distinct().count()).isEqualTo(76);
	}

	@Test
	@DisplayName("딱 한 쪽에 들어가면 hasNext 도 nextCursor 도 없다")
	void exactlyOnePageHasNoNext() {
		addPlaces(20);

		PlacePageResponse page = this.service.searchByFacet(TYPE, KEY, 20, null);

		assertThat(page.items()).hasSize(20);
		assertThat(page.hasNext()).isFalse();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	@DisplayName("🔴 첫 쪽은 전과 같은 질의 하나다 — limit+1 행만 읽는다 (커서가 첫 화면 비용을 키우지 않는다)")
	void firstPageStillReadsOnlyLimitPlusOneRows() {
		addPlaces(76);

		this.service.searchByFacet(TYPE, KEY, 20, null);

		ArgumentCaptor<Limit> limits = ArgumentCaptor.forClass(Limit.class);
		verify(this.placeRepository, times(1)).findHavingFeature(eq(TYPE), eq(KEY), limits.capture());
		assertThat(limits.getValue().max()).isEqualTo(21);
	}

	@Test
	@DisplayName("커서 없이 부르던 세 인자 형태는 그대로 첫 쪽을 준다")
	void threeArgumentFormStillReturnsTheFirstPage() {
		addPlaces(30);

		PlacePageResponse page = this.service.searchByFacet(TYPE, KEY, 20);

		assertThat(page.items()).hasSize(20);
		assertThat(page.hasNext()).isTrue();
		assertThat(page.nextCursor()).isNotBlank();
	}

	@Test
	@DisplayName("🔴 확인된 부재로 걸러지는 줄이 있어도 쪽 경계가 밀리지 않는다 — 가운데 쪽도 꽉 차고 빠진 곳이 없다")
	void filteredOutRowsDoNotShiftPageBoundaries() {
		// 7번째와 25번째와 26번째가 «없다고 확인된» 줄이다. 25 · 26 은 둘째 쪽 한가운데 걸린다.
		Set<Integer> absent = Set.of(7, 25, 26);
		IntStream.rangeClosed(1, 60).forEach(number -> addPlace(String.format("축제%03d", number), !absent.contains(number)));

		List<PlacePageResponse> pages = walk(20);

		// 걸러진 뒤 57곳 → 20 · 20 · 17. 가운데 쪽이 19나 18로 줄어들면 안 된다.
		assertThat(pages).extracting(page -> page.items().size()).containsExactly(20, 20, 17);
		Set<String> seen = new LinkedHashSet<>();
		pages.forEach(page -> seen.addAll(names(page)));
		assertThat(seen).hasSize(57);
		assertThat(seen).doesNotContain("축제007", "축제025", "축제026");
	}

	@Test
	@DisplayName("🔴 다른 갈래의 커서로는 이어받을 수 없다 — INVALID_CURSOR")
	void cursorFromAnotherFacetIsRejected() {
		addPlaces(76);
		String foreign = SearchCursor.of(SearchCursor.fingerprint("facet|INTEREST_TAG|NATURE", null, 20), 20).encode();

		assertThat(rejectionCodeOf(() -> this.service.searchByFacet(TYPE, KEY, 20, foreign))).isEqualTo("INVALID_CURSOR");
	}

	@Test
	@DisplayName("한 쪽 크기를 바꿔 이어받으면 거절된다 — offset 이 다른 뜻이 되기 때문이다")
	void cursorWithAnotherLimitIsRejected() {
		addPlaces(76);
		String cursor = this.service.searchByFacet(TYPE, KEY, 20, null).nextCursor();

		assertThat(rejectionCodeOf(() -> this.service.searchByFacet(TYPE, KEY, 50, cursor))).isEqualTo("INVALID_CURSOR");
	}

	@Test
	@DisplayName("🔴 지문이 맞아도 offset 이 갈래 상한(1,000)을 넘으면 거절된다 — 큰 offset 으로 표를 훑게 두지 않는다")
	void hugeOffsetIsRejectedEvenWithAValidFingerprint() {
		addPlaces(76);
		String forged = SearchCursor.of(SearchCursor.fingerprint("facet|" + TYPE + "|" + KEY, null, 20), 1_001).encode();

		assertThat(rejectionCodeOf(() -> this.service.searchByFacet(TYPE, KEY, 20, forged))).isEqualTo("INVALID_CURSOR");
	}

	@Test
	@DisplayName("끝을 지난 offset 은 빈 쪽이다 — 오류가 아니다")
	void offsetPastTheEndIsAnEmptyPage() {
		addPlaces(30);
		String pastEnd = SearchCursor.of(SearchCursor.fingerprint("facet|" + TYPE + "|" + KEY, null, 20), 100).encode();

		PlacePageResponse page = this.service.searchByFacet(TYPE, KEY, 20, pastEnd);

		assertThat(page.items()).isEmpty();
		assertThat(page.hasNext()).isFalse();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	@DisplayName("🔴 읽기 상한까지 읽고도 확인된 장소가 모자라면 rankTruncated 로 알린다 — 조용히 자르지 않는다")
	void scanCapIsReportedAsTruncated() {
		// 전부 «없다고 확인된» 줄 1,500개 — 읽는 행을 두 배씩 늘려 상한(1,200)에서 멈춘다.
		IntStream.rangeClosed(1, 1_500).forEach(number -> addPlace(String.format("부재%04d", number), false));

		PlacePageResponse page = this.service.searchByFacet(TYPE, KEY, 20, null);

		assertThat(page.items()).isEmpty();
		assertThat(page.hasNext()).isFalse();
		assertThat(page.rankTruncated()).isTrue();
		ArgumentCaptor<Limit> limits = ArgumentCaptor.forClass(Limit.class);
		verify(this.placeRepository, org.mockito.Mockito.atLeastOnce()).findHavingFeature(eq(TYPE), eq(KEY), limits.capture());
		assertThat(limits.getAllValues()).extracting(Limit::max).containsExactly(21, 42, 84, 168, 336, 672, 1_200);
	}
}
