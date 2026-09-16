package com.gabolle.backend.collection.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.gabolle.backend.collection.application.CollectionService;
import com.gabolle.backend.collection.domain.Collection;
import com.gabolle.backend.collection.domain.CollectionItem;

/**
 * 컬렉션 하나 — S15P21E201-1013.
 *
 * <p>값이 없는 칸은 <b>키째 뺀다</b>({@code @JsonInclude(NON_NULL)}) — 이 저장소의 다른
 * 응답과 같은 규칙이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectionResponse(UUID collectionId, String name, String description, int count,
		List<Item> items, boolean hasMore, OffsetDateTime updatedAt) {

	public static CollectionResponse of(CollectionService.Loaded loaded) {
		Collection collection = loaded.collection();
		List<Item> items = loaded.items().stream().map(Item::of).toList();
		return new CollectionResponse(collection.getId(), collection.getName(), collection.getDescription(),
				items.size(), items, loaded.hasMore(), collection.getUpdatedAt());
	}

	/**
	 * 컬렉션에 담긴 것 하나.
	 *
	 * @param kind 🔴 <b>{@code PLACE} 인지 {@code CUSTOM} 인지를 값으로 말한다.</b> 화면이
	 *     «{@code placeId} 가 없으면 직접 적은 것» 으로 추론하지 않게 하려는 것이다 — 그렇게
	 *     두면 장소가 지워진 항목과 직접 적은 항목이 구분되지 않는다
	 * @param placeId {@code PLACE} 일 때만 온다. <b>이름·좌표·사진은 이 번호로 장소를 따로
	 *     불러 얻는다</b> — 여기 베껴 담으면 장소 이름이 바뀌었을 때 한쪽만 낡는다
	 * @param name {@code CUSTOM} 일 때만 온다
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Item(UUID itemId, CollectionItem.Kind kind, UUID placeId, String name, String locality,
			Double lat, Double lng, String photoUrl, String note, int position) {

		static Item of(CollectionItem item) {
			return new Item(item.getId(), item.getKind(), item.getPlaceId(), item.getName(), item.getLocality(),
					item.getLat(), item.getLng(), item.getPhotoUrl(), item.getNote(), item.getPosition());
		}
	}

	/** 내 컬렉션 전부. 모양은 이 저장소의 다른 목록 응답과 같다 ({@code items}·{@code count}). */
	/**
	 * 내 컬렉션 목록.
	 *
	 * @param hasMore 🔴 상한에 걸려 <b>더 있는데 안 보냈다</b> (S15P21E201-1037). 컬렉션 안쪽
	 *     항목이 잘린 것은 각 {@link CollectionResponse} 의 같은 이름 칸이 따로 알린다
	 */
	public record Page(List<CollectionResponse> items, int count, boolean hasMore) {

		public static Page of(CollectionService.Listing listing) {
			List<CollectionResponse> items = listing.items().stream().map(CollectionResponse::of).toList();
			return new Page(items, items.size(), listing.hasMore());
		}
	}
}
