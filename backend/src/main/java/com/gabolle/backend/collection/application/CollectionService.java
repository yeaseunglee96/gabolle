package com.gabolle.backend.collection.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.collection.domain.Collection;
import com.gabolle.backend.collection.domain.CollectionItem;
import com.gabolle.backend.collection.repository.CollectionItemRepository;
import com.gabolle.backend.collection.repository.CollectionRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.PlaceNotFoundException;

/**
 * 컬렉션.
 *
 * <p>경로에 남의 번호를 넣을 자리가 없고({@code /api/v1/me/collections}), 컬렉션을 찾을 때도
 * 언제나 주인과 함께 찾는다. 번호만으로 찾고 나서 주인을 따로 검사하면 그 검사를 빠뜨릴 수
 * 있다.
 *
 * <p>없는 컬렉션과 남의 컬렉션을 같은 404 로 답한다. 구분하면 있다는 사실이 새어 나간다.
 */
@Service
@Profile({ "db", "dev" })
public class CollectionService {

	/**
	 * 한 번에 돌려주는 컬렉션 최대 개수. 상한에 걸려 잘렸다는 것은 {@code hasMore} 로 함께
	 * 알린다 — 안 알리면 목록이 조용히 잘려 사용자에게는 없어진 것으로 보인다.
	 */
	public static final int MAX_COLLECTIONS = 100;

	/** 한 컬렉션이 한 번에 돌려주는 항목 최대 개수. 담기는 개수에 끝이 없었다. */
	public static final int MAX_ITEMS_PER_COLLECTION = 500;

	private final CollectionRepository collections;

	private final CollectionItemRepository items;

	private final PlaceRepository places;

	private final Clock clock;

	public CollectionService(CollectionRepository collections, CollectionItemRepository items,
			PlaceRepository places, Clock clock) {
		this.collections = collections;
		this.items = items;
		this.places = places;
		this.clock = clock;
	}

	// ── 컬렉션 ──────────────────────────────────────────────────────────────

	/**
	 * 내 컬렉션 전부와 그 안의 항목.
	 *
	 * <p>항목을 컬렉션마다 따로 묻지 않는다 — 질의 수가 목록 길이에 비례한다.
	 */
	@Transactional(readOnly = true)
	public Listing list(UUID userId) {
		List<Collection> mine = this.collections
				.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, MAX_COLLECTIONS + 1));

		boolean hasMore = mine.size() > MAX_COLLECTIONS;
		if (hasMore) {
			mine = mine.subList(0, MAX_COLLECTIONS);
		}
		if (mine.isEmpty()) {
			return new Listing(List.of(), false);
		}

		Map<UUID, List<CollectionItem>> byCollection = new LinkedHashMap<>();
		List<UUID> ids = mine.stream().map(Collection::getId).toList();
		int ceiling = ids.size() * MAX_ITEMS_PER_COLLECTION;
		for (CollectionItem item : this.items
				.findByCollectionIdInOrderByPositionAscCreatedAtAsc(ids, PageRequest.of(0, ceiling))) {
			byCollection.computeIfAbsent(item.getCollectionId(), (key) -> new java.util.ArrayList<>()).add(item);
		}

		List<Loaded> loaded = mine.stream()
				.map((collection) -> load(collection, byCollection.getOrDefault(collection.getId(), List.of())))
				.toList();
		return new Listing(loaded, hasMore);
	}

	@Transactional(readOnly = true)
	public Loaded get(UUID userId, UUID collectionId) {
		Collection collection = mine(userId, collectionId);
		return load(collection, this.items.findByCollectionIdOrderByPositionAscCreatedAtAsc(collectionId,
				PageRequest.of(0, MAX_ITEMS_PER_COLLECTION + 1)));
	}

	@Transactional
	public Collection create(UUID userId, String name, String description) {
		return this.collections.save(
				Collection.of(UUID.randomUUID(), userId, name, description, now()));
	}

	@Transactional
	public Collection rename(UUID userId, UUID collectionId, String name, String description) {
		Collection collection = mine(userId, collectionId);
		collection.rename(name, description, now());
		return collection;
	}

	/** 컬렉션을 지우면 담긴 것도 함께 사라진다 (DB 의 {@code ON DELETE CASCADE}). */
	@Transactional
	public void delete(UUID userId, UUID collectionId) {
		this.collections.delete(mine(userId, collectionId));
	}

	// ── 담긴 것 ─────────────────────────────────────────────────────────────

	/**
	 * 우리가 아는 곳을 담는다.
	 *
	 * <p>이미 담긴 장소를 또 담아도 성공이고 두 번 들어가지 않는다. 화면에서 연타할 수 있고
	 * 통신이 끊기면 앱이 재시도한다.
	 *
	 * @throws PlaceNotFoundException 없는 장소를 담으려 했다. 안 막으면 외래키 위반이
	 *     그대로 올라와 500 이 나가고, 화면은 «서버 고장» 과 «그런 장소 없음» 을 구분 못 한다
	 */
	@Transactional
	public CollectionItem addPlace(UUID userId, UUID collectionId, UUID placeId, String note) {
		Collection collection = mine(userId, collectionId);
		if (!this.places.existsById(placeId)) {
			throw new PlaceNotFoundException(placeId);
		}

		OffsetDateTime now = now();
		this.items.insertPlaceItemIfAbsent(UUID.randomUUID(), collectionId, placeId,
				CollectionItem.normalizedNote(note), nextPosition(collectionId), now);
		collection.touch(now);

		// 넣었든 이미 있었든, 돌려주는 것은 «지금 담겨 있는 그 항목» 하나다.
		return this.items.findByCollectionIdAndPlaceId(collectionId, placeId)
				.orElseThrow(() -> new WriteReadBackFailedException(
						"방금 담은 장소를 도로 읽지 못했다: collectionId=" + collectionId + " placeId=" + placeId));
	}

	/** 사용자가 직접 적은 것을 담는다. 사진은 화면이 먼저 올리고 그 주소를 준다. */
	@Transactional
	public CollectionItem addCustom(UUID userId, UUID collectionId, String name, String locality, Double lat,
			Double lng, String photoUrl, String note) {
		Collection collection = mine(userId, collectionId);
		OffsetDateTime now = now();

		CollectionItem item = this.items.save(CollectionItem.ofCustom(UUID.randomUUID(), collectionId, name,
				locality, lat, lng, photoUrl, note, nextPosition(collectionId), now));
		collection.touch(now);
		return item;
	}

	@Transactional
	public CollectionItem editItem(UUID userId, UUID collectionId, UUID itemId, String name, String locality,
			Double lat, Double lng, String photoUrl, String note, Integer position) {
		Collection collection = mine(userId, collectionId);
		CollectionItem item = this.items.findByIdAndCollectionId(itemId, collectionId)
				.orElseThrow(() -> new CollectionNotFoundException(collectionId));

		OffsetDateTime now = now();
		if (item.getKind() == CollectionItem.Kind.CUSTOM) {
			item.editCustom(name, locality, lat, lng, photoUrl, note, position, now);
		}
		else {
			// 장소 항목은 메모와 차례만 바꾼다 — 이름·좌표·사진은 장소 표의 것이다.
			item.edit(note, position, now);
		}
		collection.touch(now);
		return item;
	}

	/** 없는 것을 빼도 성공이다 — 이미 빠진 뒤에 재시도가 도착하는 일이 흔하다. */
	@Transactional
	public void removeItem(UUID userId, UUID collectionId, UUID itemId) {
		Collection collection = mine(userId, collectionId);
		this.items.findByIdAndCollectionId(itemId, collectionId).ifPresent((item) -> {
			this.items.delete(item);
			collection.touch(now());
		});
	}

	// ── 안쪽 ────────────────────────────────────────────────────────────────

	private Collection mine(UUID userId, UUID collectionId) {
		return this.collections.findByIdAndUserId(collectionId, userId)
				.orElseThrow(() -> new CollectionNotFoundException(collectionId));
	}

	/** 새로 담은 것은 맨 뒤에 둔다. 화면이 끌어놓아 다시 매기면 그 값이 온다. */
	private int nextPosition(UUID collectionId) {
		return (int) this.items.countByCollectionId(collectionId);
	}

	private OffsetDateTime now() {
		return OffsetDateTime.now(this.clock);
	}

	/** 항목이 상한을 넘었으면 잘라서 {@link Loaded} 로 묶는다. 자른 사실은 그 안에 남는다. */
	private static Loaded load(Collection collection, List<CollectionItem> found) {
		boolean hasMore = found.size() > MAX_ITEMS_PER_COLLECTION;
		return new Loaded(collection, hasMore ? found.subList(0, MAX_ITEMS_PER_COLLECTION) : found, hasMore);
	}

	/**
	 * 컬렉션과 그 안의 항목을 함께 들고 다니는 묶음.
	 *
	 * @param hasMore 항목이 상한에 걸려 더 있는데 안 보냈다
	 */
	public record Loaded(Collection collection, List<CollectionItem> items, boolean hasMore) {
	}

	/**
	 * 내 컬렉션 목록.
	 *
	 * @param hasMore 컬렉션 자체가 상한에 걸려 더 있는데 안 보냈다
	 */
	public record Listing(List<Loaded> items, boolean hasMore) {
	}

	/**
	 * 넣은 직후 그 행을 도로 못 읽었다 — 우리 쪽 불변식이 깨진 것이다.
	 *
	 * <p>{@code IllegalArgumentException}·{@code IllegalStateException} 을 쓰지 않는 것은
	 * {@code CollectionExceptionHandler} 가 그 둘을 400 으로 내리기 때문이다. 이 예외는 아무
	 * 어드바이스도 잡지 않아 500 으로 나간다.
	 */
	public static class WriteReadBackFailedException extends RuntimeException {

		public WriteReadBackFailedException(String message) {
			super(message);
		}
	}

	/** 없는 컬렉션이거나, 있어도 내 것이 아니다. 둘을 구분해 답하지 않는다. */
	public static class CollectionNotFoundException extends RuntimeException {

		public CollectionNotFoundException(UUID collectionId) {
			super("컬렉션을 찾을 수 없습니다: " + collectionId);
		}
	}
}
