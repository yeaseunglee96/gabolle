package com.gabolle.backend.tools.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * 같은 문장을 다시 번역하지 않게 답을 들고 있는 자리 — S15P21E201-343.
 *
 * <p>🔴 열쇠는 원문 자체가 아니라 {@link TranslationHash} 다 — 구현체(DB)가 원문을 평문으로
 * 저장하면 안 된다는 뜻이다. 저장하는 것은 번역 결과({@code translatedText})뿐이다.
 *
 * <p>7일 보관은 배치로 지우지 않는다 — {@link #findFreshTranslation} 이 조회 시점에
 * {@code expiresAt} 이 지난 값을 미스로 취급하는 것으로 충분하다. 별도 삭제 배치는
 * 과설계다.
 */
public interface TranslationCacheRepository {

	/** @return 만료되지 않은 캐시. 없거나 지났으면 빈 값(미스) */
	Optional<String> findFreshTranslation(String sourceHash, Instant now);

	/** 이미 같은 열쇠가 있으면(레이스로 두 번 번역됐다) 먼저 담긴 값을 그대로 둔다. */
	void save(String sourceHash, String translatedText, Instant now, Instant expiresAt);
}
