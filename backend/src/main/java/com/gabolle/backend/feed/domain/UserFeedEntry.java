package com.gabolle.backend.feed.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 앱을 켰을 때 보이는 줄 하나.
 *
 * <p>{@link #payload} 에 카드를 그릴 값(이름·사진·한 줄 설명)을 복사해 둔다. 그래서 읽을 때
 * 장소 표를 조인하지 않는다. 대가는 원본이 바뀌면 사본이 낡는 것이다 — 장소 이름이 바뀌어도
 * 다음 세대가 만들어질 때까지 옛 이름이 나간다.
 *
 * <p>그래서 가격·영업시간처럼 틀리면 사람이 헛걸음하는 값은 넣지 않는다. 그건 상세 화면에서
 * 그때 조회한다.
 */
@Entity
@Table(name = "user_feed")
public class UserFeedEntry {

	@EmbeddedId
	private FeedEntryId id;

	@Enumerated(EnumType.STRING)
	@Column(name = "item_type", nullable = false, length = 30, updatable = false)
	private FeedItemType itemType;

	@Column(name = "item_id", nullable = false, updatable = false)
	private UUID itemId;

	@Column(name = "score")
	private Double score;

	/**
	 * 왜 이것이 여기 있는가. 화면의 "이런 이유로 골랐어요" 가 이 값으로 만들어진다.
	 * 빌 수 없다 — DB 가 막는다({@code ck_user_feed_has_reason}).
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "reason_codes", nullable = false)
	private String[] reasonCodes;

	/** JSON 문자열로 들고 다니되 저장은 jsonb 로 간다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false)
	private String payload;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected UserFeedEntry() {
	}

	private UserFeedEntry(FeedEntryId id, FeedItemType itemType, UUID itemId, Double score, String[] reasonCodes,
			String payload, OffsetDateTime createdAt) {
		this.id = id;
		this.itemType = itemType;
		this.itemId = itemId;
		this.score = score;
		this.reasonCodes = reasonCodes;
		this.payload = payload;
		this.createdAt = createdAt;
	}

	/**
	 * {@code reasonCodes} 가 비면 DB 가 저장을 거부한다. 여기서 미리 막지 않는 것은
	 * 검사가 두 곳에 있으면 한쪽만 고쳐지기 때문이다 — 판정은 DB 하나가 한다.
	 */
	public static UserFeedEntry of(UUID buildId, int position, FeedItemType itemType, UUID itemId, Double score,
			String[] reasonCodes, String payload, OffsetDateTime createdAt) {
		return new UserFeedEntry(new FeedEntryId(buildId, position), itemType, itemId, score, reasonCodes, payload,
				createdAt);
	}

	public FeedEntryId getId() {
		return this.id;
	}

	public UUID getBuildId() {
		return this.id.getBuildId();
	}

	public int getPosition() {
		return this.id.getPosition();
	}

	public FeedItemType getItemType() {
		return this.itemType;
	}

	public UUID getItemId() {
		return this.itemId;
	}

	public Double getScore() {
		return this.score;
	}

	public String[] getReasonCodes() {
		return this.reasonCodes;
	}

	public String getPayload() {
		return this.payload;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}
}
