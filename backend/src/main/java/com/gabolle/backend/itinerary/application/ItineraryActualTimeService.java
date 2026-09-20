package com.gabolle.backend.itinerary.application;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryItemActualRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 방문지에 실제로 도착·출발한 시각을 적는다.
 * 판을 만들지 않는다 — 이것이 편집과 갈리는 지점이다. 실제 시각은 판에 실리지 않으므로 낡은
 * 판 위의 요청을 409 로 막을 이유가 없다. 화면이 3판을 보고 있는데 그 사이 5판이 됐어도
 * "그 방문지에 몇 시에 도착했다" 는 사실은 어긋나지 않는다. 그래서 {@code baseVersion} 을 안 받는다.
 * 대신 그 방문지가 지금도 일정에 있는지는 본다. 최신 판에 없는 {@code itemKey} 에 적은 기록은
 * 어느 화면에도 안 보이므로 성공으로 답하면 거짓말이 된다.
 * 검사 순서가 계약이다 — {@link ItineraryAccess#requireEditor}(편집과 같은 판정), 최신 판
 * 내용에서 {@code itemKey} 찾기(없으면 404), 그다음 {@link ItineraryItemActual} 생성자의 값
 * 검증(시각이 하나도 없거나 출발이 도착보다 앞서면 400).
 * 권한 판정이 값 검증보다 먼저다. 뒤집으면 남의 여행에 아무 본문이나 보낸 사람이 "그 항목은
 * 없다"·"시각이 이상하다" 같은 답으로 그 일정의 내부를 알아낼 수 있다.
 * 지나간 날짜를 막지 않는다. 여행에서 돌아와 저녁에 몰아 적는 것이 주된 사용 방식이라
 * "오늘이 아니면 거부" 는 기능을 없애는 검사다. 미래 시각도 막지 않는다 — 그 판정을 넣으면
 * 서버 시계와 기기 시계의 차이가 곧 사용자 오류로 보인다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryActualTimeService {

	private final ItineraryAccess itineraryAccess;

	private final ItineraryRepository itineraryRepository;

	private final ItineraryItemActualRepository actualRepository;

	public ItineraryActualTimeService(ItineraryAccess itineraryAccess, ItineraryRepository itineraryRepository,
			ItineraryItemActualRepository actualRepository) {
		this.itineraryAccess = itineraryAccess;
		this.itineraryRepository = itineraryRepository;
		this.actualRepository = actualRepository;
	}

	/**
	 * 그 방문지의 실제 시각을 덮어쓴다. 보낸 것이 최종 상태이므로 도착만 보내면 출발은
	 * {@code null} 이 된다 — 부분 갱신이 아니다.
	 *
	 * @param arrivedAt 실제 도착 시각. {@code departedAt} 이 있으면 없을 수 있다
	 * @param departedAt 실제 출발 시각. {@code arrivedAt} 이 있으면 없을 수 있다
	 * @param userId 요청자. 여행의 OWNER·EDITOR 여야 한다
	 * @throws ItineraryRevision.ItemNotFoundException 최신 판에 그 방문지가 없다 — 404
	 * @throws ItineraryItemActual.NoTimeGivenException 도착도 출발도 없다 — 400
	 * @throws ItineraryItemActual.DepartedBeforeArrivedException 출발이 도착보다 앞선다 — 400
	 */
	@Transactional
	public ItineraryItemActual record(String itineraryId, String itemKey, Instant arrivedAt, Instant departedAt,
			String userId) {

		ItineraryAccess.Access access = this.itineraryAccess.requireEditor(itineraryId, userId);

		requireItemInLatestVersion(itineraryId, access.itinerary().latestVersion(), itemKey);

		ItineraryItemActual actual = new ItineraryItemActual(itineraryId, itemKey, arrivedAt, departedAt,
				userId, Instant.now());
		return this.actualRepository.upsert(actual);
	}

	/**
	 * 최신 판에서 찾는다. 지운 항목의 {@code itemKey} 로 보낸 요청을 성공으로 답하면 저장은 되는데
	 * 어느 화면에도 안 나타난다 — 사용자에게는 "적었는데 사라졌다" 다.
	 * {@code ItineraryEditService} 는 같은 확인을 바탕 판에서 하는데, 그쪽은 그 판을 복사해 다음
	 * 판을 만드는 것이 목적이라 기준이 다르다.
	 */
	private void requireItemInLatestVersion(String itineraryId, int latestVersion, String itemKey) {
		ItineraryContent content = this.itineraryRepository.findContent(itineraryId, latestVersion)
				.orElseThrow(() -> new IllegalStateException(
						"일정의 최신 판(version=" + latestVersion + ") 내용이 없다: itineraryId=" + itineraryId));

		boolean found = content.items().stream()
				.map(ItineraryItem::itemKey)
				.anyMatch(itemKey::equals);

		if (!found) {
			throw new ItineraryRevision.ItemNotFoundException(itemKey);
		}
	}
}
