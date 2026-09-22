package com.gabolle.backend.tools.infra;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code translation_cache} 표 매핑. 원문은 여기 없다 — {@code sourceHash} 는 되돌릴 수 없는 값이고,
 * 저장하는 것은 번역 결과뿐이다.
 */
@Entity
@Table(name = "translation_cache")
public class TranslationCacheJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID cacheId;

	@Column(name = "source_hash", nullable = false, unique = true, length = 64, updatable = false)
	private String sourceHash;

	@Column(name = "target_text", nullable = false, columnDefinition = "text", updatable = false)
	private String targetText;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	protected TranslationCacheJpaEntity() {
		// JPA 전용
	}

	private TranslationCacheJpaEntity(String sourceHash, String targetText, Instant createdAt, Instant expiresAt) {
		this.sourceHash = sourceHash;
		this.targetText = targetText;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public static TranslationCacheJpaEntity of(String sourceHash, String targetText, Instant createdAt,
			Instant expiresAt) {
		return new TranslationCacheJpaEntity(sourceHash, targetText, createdAt, expiresAt);
	}

	public UUID getCacheId() { return this.cacheId; }
	public String getSourceHash() { return this.sourceHash; }
	public String getTargetText() { return this.targetText; }
	public Instant getCreatedAt() { return this.createdAt; }
	public Instant getExpiresAt() { return this.expiresAt; }
}
