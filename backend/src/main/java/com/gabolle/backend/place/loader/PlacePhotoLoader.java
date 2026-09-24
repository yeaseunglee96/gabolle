package com.gabolle.backend.place.loader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 이미 있는 장소에 사진만 붙인다.
 *
 * <p>{@code TourApiPlaceLoader} 는 이미 있는 장소를 고치지 않는다. 장소 본문은 한 원천이
 * 주인이어야 해서 옳은 규칙이지만, 사진은 나중에 다른 원천에서 온다 — 본 API 의 사진은 대부분
 * 재사용이 막힌 유형이라 비워 두고 따로 신청해 받는 원천이 뒤에 붙는다. 그것까지 장소 적재
 * 규칙에 묶으면 장소를 지웠다 다시 넣지 않는 한 영영 못 채운다. 그래서 사진만 갱신하는 길을
 * 따로 연다.
 *
 * <p>장소는 {@code source_type}+{@code source_id} 로 찾는다 — 어느 출처의 장소든 붙고, 번호가 계산
 * 규칙과 다르게 만들어진 장소도 찾는다({@link PlaceRepository#findBySourceTypeAndSourceIdIn}).
 * 이미 사진이 있는 장소는 건드리지 않는다({@link Place#attachPhoto}).
 *
 * <p>마이그레이션으로 하지 않는 것은 그것이 서버가 뜰 때 돌기 때문이다. 아직 없는 장소에는
 * 0 행을 고치고 끝나고, 그 뒤에 장소를 넣어도 사진은 영영 안 들어간다. 적재기는 사람이 순서를
 * 정해 부른다.
 */
@Component
@Profile({ "db", "dev" })
public class PlacePhotoLoader {

	private final PlaceRepository placeRepository;

	public PlacePhotoLoader(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	@Transactional
	public Result load(List<PlacePhotoRow> rows) {
		// 출처마다 한 번씩 읽는다 — 줄마다 물으면 수백 번 왕복한다.
		Map<String, Place> places = new HashMap<>();
		rows.stream()
				.collect(Collectors.groupingBy(PlacePhotoRow::sourceType,
						Collectors.mapping(PlacePhotoRow::sourceId, Collectors.toSet())))
				.forEach((sourceType, sourceIds) -> this.placeRepository
						.findBySourceTypeAndSourceIdIn(sourceType, sourceIds)
						.forEach((place) -> places.put(keyOf(place.getSourceType(), place.getSourceId()), place)));

		int attached = 0;
		int hadPhoto = 0;
		int noPlace = 0;
		for (PlacePhotoRow row : rows) {
			Place place = places.get(keyOf(row.sourceType(), row.sourceId()));
			if (place == null) {
				// 실패시키지 않고 센다. 순서를 뒤집어 돌렸을 때 "장소를 먼저 넣어라" 를 숫자로
				// 알려 주는 것이 이 값의 목적이다.
				noPlace++;
				continue;
			}
			if (place.attachPhoto(row.photoUrl(), row.attribution(), row.subject(), row.license())) {
				attached++;
			}
			else {
				hadPhoto++;
			}
		}
		// save 를 따로 부르지 않는다. 위에서 읽은 Place 는 영속 상태라 트랜잭션이 끝날 때 바뀐
		// 값이 그대로 나간다.
		return new Result(attached, hadPhoto, noPlace);
	}

	private static String keyOf(String sourceType, String sourceId) {
		return sourceType + '\u0000' + sourceId;
	}

	/**
	 * {@code noPlace} 가 크면 실패가 아니라 장소 적재를 안 돌린 순서 문제거나 열쇠가 틀린 것이다.
	 * {@code hadPhoto} 는 이미 사진이 있어 그대로 둔 곳이다 — 같은 파일을 두 번 돌리면 두 번째는 전부 여기로 간다.
	 */
	public record Result(int attached, int hadPhoto, int noPlace) {

		public Result plus(Result other) {
			return new Result(this.attached + other.attached, this.hadPhoto + other.hadPhoto,
					this.noPlace + other.noPlace);
		}

		@Override
		public String toString() {
			return "붙임 %d · 이미 사진이 있어 그대로 둠 %d · 붙일 장소 없어 넘김 %d".formatted(this.attached,
					this.hadPhoto, this.noPlace);
		}
	}
}
