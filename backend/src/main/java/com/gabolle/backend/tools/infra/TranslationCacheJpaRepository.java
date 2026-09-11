package com.gabolle.backend.tools.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data 창구 — {@link JpaTranslationCacheRepository} 가 감싸서 도메인 포트로 내보낸다. */
public interface TranslationCacheJpaRepository extends JpaRepository<TranslationCacheJpaEntity, UUID> {

	Optional<TranslationCacheJpaEntity> findBySourceHash(String sourceHash);
}
