package com.gabolle.backend.tools.infra;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.gabolle.backend.tools.domain.TranslationCacheRepository;

/** {@link TranslationCacheRepository} 의 JPA 구현 — S15P21E201-343. */
@Repository
@Profile({ "db", "dev" })
public class JpaTranslationCacheRepository implements TranslationCacheRepository {

	private final TranslationCacheJpaRepository jpaRepository;

	public JpaTranslationCacheRepository(TranslationCacheJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Optional<String> findFreshTranslation(String sourceHash, Instant now) {
		return this.jpaRepository.findBySourceHash(sourceHash)
				.filter(entity -> entity.getExpiresAt().isAfter(now))
				.map(TranslationCacheJpaEntity::getTargetText);
	}

	@Override
	public void save(String sourceHash, String translatedText, Instant now, Instant expiresAt) {
		// 이미 같은 열쇠가 있으면(레이스로 두 번 번역됐다) 먼저 담긴 값을 그대로 둔다 — upsert 를
		// 대신하는 가장 단순한 방법이다. unique 제약이 있어 그냥 save 하면 중복 키 예외가 난다.
		if (this.jpaRepository.findBySourceHash(sourceHash).isPresent()) {
			return;
		}
		this.jpaRepository.save(TranslationCacheJpaEntity.of(sourceHash, translatedText, now, expiresAt));
	}
}
