package com.gabolle.backend.event.application;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.preference.application.TasteAttributionService;

/**
 * 브로커에서 받은 이벤트를 <b>한 번만</b> 반영한다 — 장부 기록과 취향 반영을 한 덩어리로
 * (S15P21E201-1501).
 *
 * <h2>🔴 왜 JPA {@code save} 를 안 쓰나</h2>
 *
 * {@code EventConsumption} 은 번호({@code event_id})를 직접 넣는 엔티티이고 {@code Persistable}
 * 도 {@code @Version} 도 없다. 그러면 Spring Data 는 그것을 <b>새것이 아니라고</b> 판단해
 * {@code persist} 가 아니라 {@code merge} 로 저장한다.
 *
 * <pre>
 * 같은 이벤트가 두 번째로 옴
 *   → merge → 행 조회 → 이미 있음 → (전 칸이 updatable=false 라) 아무것도 안 함 → 오류 없음
 * </pre>
 *
 * 즉 「기본키가 두 번째 INSERT 를 거부한다」는 전제가 <b>실제로는 일어나지 않았다.</b> 중복을
 * 잡으려던 {@code catch} 는 한 번도 안 도는 코드였고, 그 결과 재전송된 이벤트가 취향에 <b>한
 * 번 더</b> 반영됐다. 상태 이벤트(하트·끔)는 상태 표가 막아 줬지만 보기·방문은 두 번 더해졌다.
 *
 * <p>그래서 {@code INSERT … ON CONFLICT DO NOTHING} 을 쓰고 <b>넣은 행 수</b>로 판정한다.
 * {@code SavedPlaceRepository.insertIfAbsent} 와 같은 모양이다 — 판정을 데이터베이스가 하므로
 * 동시에 두 번 와도, 순서대로 두 번 와도 똑같이 막힌다.
 *
 * <h2>한 트랜잭션이어야 하는 이유</h2>
 *
 * 장부에 적는 것과 취향에 반영하는 것이 따로면, 사이에서 죽었을 때 「반영했다」고 적힌 채
 * 반영이 안 된다. 배치가 돌 때는 다음 접기가 메웠지만, 배치를 걷어내면 <b>영구 손실</b>이다.
 * 이 메서드가 둘을 묶는다 — 어디서 실패하든 둘 다 되돌아가고, 예외가 소비자로 올라가 카프카가
 * 다시 보낸다.
 *
 * <h2>{@code @Profile} 을 처음부터 단다</h2>
 *
 * {@code JdbcTemplate} 은 데이터소스가 있을 때만 생긴다. {@code TasteAttributionService} 가
 * 이것을 안 달았다가 DB 없는 컨텍스트 여섯이 한꺼번에 죽었다(S15P21E201-1500).
 */
@Component
@Profile({ "db", "dev" })
public class EventConsumptionService {

	/**
	 * 🔴 {@code ON CONFLICT (event_id) DO NOTHING} — 이미 있으면 아무것도 안 하고 <b>0 행</b>을
	 * 돌려준다. 그 0 이 곧 「이미 반영했다」다. 예외로 판정하지 않으므로 트랜잭션이 망가지지도
	 * 않는다.
	 */
	private static final String INSERT_IF_ABSENT_SQL = """
			INSERT INTO event_consumption
			       (event_id, event_type, partition_key, consumer_group, kafka_partition, kafka_offset, consumed_at)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (event_id) DO NOTHING
			""";

	private final JdbcTemplate jdbc;

	private final TasteAttributionService attribution;

	public EventConsumptionService(JdbcTemplate jdbc, TasteAttributionService attribution) {
		this.jdbc = jdbc;
		this.attribution = attribution;
	}

	/**
	 * 처음 온 이벤트면 장부에 적고 취향에 반영한다.
	 *
	 * @return 처음이라 반영했으면 {@code true}. 이미 반영한 이벤트면 {@code false} — 이건 오류가
	 *     아니라 <b>설계대로 된 것</b>이다. 릴레이가 「적어도 한 번」만 보장하므로 정상 경로다
	 */
	@Transactional
	public boolean recordFirstTime(ConsumedEvent event) {
		int inserted = this.jdbc.update(INSERT_IF_ABSENT_SQL, event.eventId(), event.eventType(), event.partitionKey(),
				event.consumerGroup(), event.kafkaPartition(), event.kafkaOffset(), event.consumedAt());
		if (inserted == 0) {
			return false;
		}
		// 취향과 무관한 이벤트는 사람이나 장소가 없다 — 여기 오는 것 대부분이 그렇다.
		if (event.userId() != null && event.placeId() != null) {
			this.attribution.apply(event.userId(), event.eventType(), event.placeId(), event.occurredAt());
		}
		return true;
	}

	/**
	 * 브로커에서 받은 것 중 반영에 필요한 것만 추린 모양.
	 *
	 * @param userId 헤더에서 읽은 사용자. 없으면 {@code null}
	 * @param placeId payload 에서 읽은 장소. 없으면 {@code null}
	 * @param occurredAt 이벤트가 일어난 시각
	 */
	public record ConsumedEvent(UUID eventId, String eventType, String partitionKey, String consumerGroup,
			int kafkaPartition, long kafkaOffset, OffsetDateTime consumedAt, UUID userId, UUID placeId,
			OffsetDateTime occurredAt) {
	}

}
