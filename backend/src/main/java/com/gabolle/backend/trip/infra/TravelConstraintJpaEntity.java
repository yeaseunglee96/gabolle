package com.gabolle.backend.trip.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.gabolle.backend.trip.domain.TravelConstraintStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code user_travel_constraint} 표 매핑 — S15P21E201-1231.
 *
 * <p>🔴 <b>사람당 한 줄</b>이라 {@code user_id} 가 곧 기본키다. 따로 id 칸을 두면 「같은
 * 사람의 답이 둘」이 들어갈 자리가 생기고, 그때 어느 것이 최신인지를 응용이 판단해야 한다.
 * 판단할 일이 없게 만드는 쪽이 낫다.
 *
 * <p>{@code value} 는 {@link TravelConstraintStatus#SAVED} 일 때만 찬다 — DB 의
 * {@code ck_user_travel_constraint_value_matches_status} 가 그것을 강제하고, 이 클래스의
 * {@link #saved}·{@link #withoutValue} 두 만드는 자리가 같은 것을 먼저 막는다.
 */
@Entity
@Table(name = "user_travel_constraint")
public class TravelConstraintJpaEntity {

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 10)
	private TravelConstraintStatus status;

	/** 🔴 칸으로 쪼개지 않는다 — 추천 엔진에 통째로 넘기는 값이다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "value")
	private String valueJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected TravelConstraintJpaEntity() {
		// JPA 전용
	}

	private TravelConstraintJpaEntity(UUID userId, TravelConstraintStatus status, String valueJson,
			OffsetDateTime now) {
		this.userId = userId;
		this.status = status;
		this.valueJson = valueJson;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 「저장하고 시작」 — 값이 반드시 있다. */
	public static TravelConstraintJpaEntity saved(UUID userId, String valueJson, OffsetDateTime now) {
		requireValue(valueJson);
		return new TravelConstraintJpaEntity(userId, TravelConstraintStatus.SAVED, valueJson, now);
	}

	/** 「나중에」·「다시 묻지 않기」 — 값이 없다. */
	public static TravelConstraintJpaEntity withoutValue(UUID userId, TravelConstraintStatus status,
			OffsetDateTime now) {
		requireNotSaved(status);
		return new TravelConstraintJpaEntity(userId, status, null, now);
	}

	/** 이미 있는 줄을 새 답으로 바꾼다. {@code created_at} 은 그대로 둔다 — 처음 답한 시각이다. */
	public void update(TravelConstraintStatus status, String valueJson, OffsetDateTime now) {
		if (status == TravelConstraintStatus.SAVED) {
			requireValue(valueJson);
			this.valueJson = valueJson;
		}
		else {
			requireNotSaved(status);
			// 🔴 값을 반드시 지운다. 안 지우면 「나중에」인데 값이 남아 DB 제약에 걸리고,
			//    걸리지 않더라도 그 값이 어디서 왔는지 아무도 설명할 수 없다.
			this.valueJson = null;
		}
		this.status = status;
		this.updatedAt = now;
	}

	// 🔴 DB 의 CHECK 와 같은 것을 여기서 먼저 막는다. DB 까지 가면 트랜잭션이 통째로
	//    되돌려지고 원인은 제약 이름만 남는다 — StoryView.create 가 같은 이유로 같은 모양이다.
	private static void requireValue(String valueJson) {
		if (valueJson == null || valueJson.isBlank()) {
			throw new IllegalArgumentException("「저장하고 시작」은 값이 있어야 한다");
		}
	}

	private static void requireNotSaved(TravelConstraintStatus status) {
		if (status == TravelConstraintStatus.SAVED) {
			throw new IllegalArgumentException("「저장하고 시작」은 값 없는 자리로 만들 수 없다");
		}
	}

	public UUID getUserId()                    { return userId; }
	public TravelConstraintStatus getStatus()  { return status; }
	public String getValueJson()               { return valueJson; }
	public OffsetDateTime getCreatedAt()       { return createdAt; }
	public OffsetDateTime getUpdatedAt()       { return updatedAt; }
}
