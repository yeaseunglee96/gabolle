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
 * <h2>{@link #payload} 가 이 설계의 핵심이다</h2>
 *
 * 카드 하나를 그리는 데 필요한 값(이름·사진·한 줄 설명)을 여기에 <b>복사해</b> 둔다.
 * 그래서 읽을 때 장소 표를 조인하지 않는다 — 조회 한 번, 조인 0 회.
 * 이것이 "읽을 때 서버는 보여주기만 한다" 의 실제 구현이다.
 *
 * <p>이렇게 사본을 두는 것을 비정규화(<b>빠르게 읽으려고 같은 값을 일부러 두 곳에 두는
 * 것</b>)라고 한다. 대가는 명확하다: <b>원본이 바뀌면 사본이 낡는다.</b> 장소 이름이
 * 바뀌어도 이 사본은 다음 세대가 만들어질 때까지 옛 이름을 보여준다.
 *
 * <p>🔴 <b>그래서 무엇을 넣지 않을지가 무엇을 넣을지보다 중요하다.</b>
 * 가격·영업시간처럼 <b>틀리면 사람이 헛걸음하는 값은 넣지 않는다.</b> 그건 상세 화면에서
 * 그때 조회한다. 피드는 원래 "지금 이 순간의 진실" 이 아니라 "최근에 고른 추천" 이고,
 * 그 차이를 넘어서는 값을 사본에 담는 순간 이 설계는 거짓말을 하기 시작한다.
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
	 *
	 * <p>🔴 <b>빌 수 없다.</b> DB 가 막는다({@code ck_user_feed_has_reason}).
	 * 이 저장소는 추천을 "왜 이것이 나왔고 왜 저것이 안 나왔는가" 로 설명할 수 있게
	 * 만들어 왔는데({@code recommendation_candidate} 가 탈락 후보까지 남기는 이유),
	 * 피드는 그 설명이 가장 쉽게 사라지는 자리다 — 계산이 끝난 결과만 남기 때문에.
	 * 빈 배열을 막으면 채우는 쪽이 이유를 만들 수밖에 없다.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "reason_codes", nullable = false)
	private String[] reasonCodes;

	/** 카드를 그리는 데 필요한 값의 사본. JSON 문자열로 들고 다니되 저장은 jsonb 로 간다. */
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
	 * 줄 하나를 만든다.
	 *
	 * <p>🔴 {@code reasonCodes} 가 비면 DB 가 저장을 거부한다. 여기서 미리 막지 않는 이유는
	 * 검사가 두 곳에 있으면 한쪽만 고쳐지는 날이 오기 때문이다 — 판정은 DB 하나가 한다.
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
