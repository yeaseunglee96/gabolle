package com.gabolle.backend.editorial.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Editor's Pick 발행본 한 판. 취향을 전부 건너뛴 계정과 추천 엔진이 죽은 요청에 돌려줄
 * 기준선이다.
 *
 * <p>읽기 전용이다. 이 서비스는 Pick 을 발행하지 않는다 — 편집자가 고른 목록을 데이터
 * 작업으로 넣고 우리는 읽기만 한다. 그래서 setter 도 공개 생성자도 없다.
 *
 * <p>같은 행을 고치지 않는다. 내용을 바꿀 때는 같은 {@code pickKey} 의 새
 * {@code contentVersion} 을 만들고 앞 판을 {@link EditorialPickStatus#RETIRED} 로 물린다 —
 * 이미 나간 추천 결과가 앞 판을 가리키고 있다.
 */
@Entity
@Table(name = "editorial_pick")
public class EditorialPick {

	@Id
	@Column(name = "pick_id", nullable = false, updatable = false)
	private UUID pickId;

	/** 사람이 부르는 이름. 판이 올라가도 그대로다 — 같은 코스의 3판은 여전히 같은 Pick 이다. */
	@Column(name = "pick_key", nullable = false, length = 100)
	private String pickKey;

	/** 1 부터. 같은 {@code pickKey} 안에서만 의미가 있다. */
	@Column(name = "content_version", nullable = false)
	private int contentVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "scope", nullable = false, length = 20)
	private EditorialPickScope scope;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private EditorialPickStatus status;

	/**
	 * 두 언어 다 필수다. 하나를 비워 두면 영어 사용자에게 한국어 제목이 그대로 나가거나
	 * 빈 칸이 나가는데, 둘 다 오류를 내지 않아 아무도 모른다. 번역이 없으면 발행하지
	 * 않는 것이 맞고, 그것이 {@link EditorialPickStatus#DRAFT} 다.
	 */
	@Column(name = "title_ko", nullable = false, length = 200)
	private String titleKo;

	/** {@link #getTitleKo()} 와 같은 이유로 필수다. */
	@Column(name = "title_en", nullable = false, length = 200)
	private String titleEn;

	/** 없어도 된다 — 제목만으로 성립하는 Pick 이 있다. */
	@Column(name = "description_ko")
	private String descriptionKo;

	/** 없어도 된다. */
	@Column(name = "description_en")
	private String descriptionEn;

	/** {@link EditorialPickScope#LOCAL} 이면 값이 있고 {@code GLOBAL} 이면 {@code null} 이다. */
	@Column(name = "locality_code", length = 50)
	private String localityCode;

	/** 여러 Pick 을 나란히 보여줄 때의 순서. 작은 값이 앞이다. */
	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	/**
	 * 발행 시점에 이미 아는 편집자의 경고만 들어 있다 — "계단이 있습니다" 같은 것.
	 *
	 * <p>사용자의 제약과 대조해 나오는 경고는 여기 없다. 그것은 요청마다
	 * {@code BaselineCandidateScorer} 가 계산해 {@code recommendation_candidate.warning_codes}
	 * 에 남는다. 둘을 섞으면 사용자마다 달라야 하는 값이 모두에게 같게 나가거나 편집자가 적어
	 * 둔 주의사항이 사용자에 따라 사라지고, 어느 쪽도 오류를 내지 않는다.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "warning_codes", nullable = false)
	private String[] warningCodes;

	/** {@code status = PUBLISHED} 일 때만 값이 있다 ({@code ck_editorial_pick_published_at}). */
	@Column(name = "published_at")
	private OffsetDateTime publishedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected EditorialPick() {
		// JPA 전용.
	}

	public UUID getPickId() {
		return this.pickId;
	}

	public String getPickKey() {
		return this.pickKey;
	}

	public int getContentVersion() {
		return this.contentVersion;
	}

	public EditorialPickScope getScope() {
		return this.scope;
	}

	public EditorialPickStatus getStatus() {
		return this.status;
	}

	public String getTitleKo() {
		return this.titleKo;
	}

	public String getTitleEn() {
		return this.titleEn;
	}

	public String getDescriptionKo() {
		return this.descriptionKo;
	}

	public String getDescriptionEn() {
		return this.descriptionEn;
	}

	public String getLocalityCode() {
		return this.localityCode;
	}

	public int getDisplayOrder() {
		return this.displayOrder;
	}

	/** 배열을 그대로 돌려주지 않는다 — 부르는 쪽이 저장된 값을 바꿀 수 있다. */
	public List<String> getWarningCodes() {
		return (this.warningCodes == null) ? List.of() : List.of(this.warningCodes);
	}

	public OffsetDateTime getPublishedAt() {
		return this.publishedAt;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	/**
	 * 이 판을 지금 내보낼 수 있는가.
	 *
	 * <p> 상태만 본다. 장소가 실제로 들어 있는지는 여기서 알 수 없다(다른 표다) — 그
	 * 확인은 Pick 을 읽는 쪽의 일이다. 여기서 "발행 가능" 이라고 답해 두고 장소가 0건이면
	 * 빈 기준선이 나가는데, 그건 기준선이 없는 것보다 나쁘다.
	 */
	public boolean isPublished() {
		return this.status == EditorialPickStatus.PUBLISHED;
	}
}
