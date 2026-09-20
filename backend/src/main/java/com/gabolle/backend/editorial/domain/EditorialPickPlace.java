package com.gabolle.backend.editorial.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Pick 안의 장소 한 칸과 사람이 정한 순서.
 *
 * <p>{@code pickRank} 를 추천 엔진이 다시 매기지 않는다. Editor's Pick 은 좋은 곳 모음이
 * 아니라 코스라, 이 값이 그대로 추천 결과의 {@code final_rank} 가 된다.
 *
 * <p>{@link EditorialPick} 과 같은 이유로 읽기 전용이다.
 */
@Entity
@Table(name = "editorial_pick_place")
public class EditorialPickPlace {

	@EmbeddedId
	private EditorialPickPlaceId id;

	/** 1 부터. 그대로 {@code recommendation_candidate.final_rank} 가 된다. */
	@Column(name = "pick_rank", nullable = false)
	private int pickRank;

	/** 이 장소를 왜 골랐는지 편집자가 적는 한 줄. 없어도 된다. */
	@Column(name = "note_ko")
	private String noteKo;

	/** 없어도 된다. */
	@Column(name = "note_en")
	private String noteEn;

	protected EditorialPickPlace() {
		// JPA 전용.
	}

	public EditorialPickPlaceId getId() {
		return this.id;
	}

	public UUID getPickId() {
		return this.id.getPickId();
	}

	public UUID getPlaceId() {
		return this.id.getPlaceId();
	}

	public int getPickRank() {
		return this.pickRank;
	}

	public String getNoteKo() {
		return this.noteKo;
	}

	public String getNoteEn() {
		return this.noteEn;
	}
}
