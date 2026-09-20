package com.gabolle.backend.tools.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.tools.config.TranslateProperties;
import com.gabolle.backend.tools.domain.TranslationCacheRepository;
import com.gabolle.backend.tools.domain.TranslationRequest;
import com.gabolle.backend.tools.domain.TranslationResult;

/**
 * 문장 하나를 번역해 답한다. 업체 호출이 실패하면 {@link TranslationVendorException} 이 그대로 위로
 * 올라간다 — 여기서 잡아 대체 문장으로 숨기지 않는다.
 *
 * <p>로거를 일부러 갖지 않는다. 로그를 찍을 자리가 없으면 실수로 원문을 찍을 자리도 없다.
 */
@Service
@Profile({ "db", "dev" })
public class TranslationService {

	private final TranslationVendorPort vendor;

	private final TranslationCacheRepository cacheRepository;

	private final TranslateProperties properties;

	private final Clock clock;

	public TranslationService(TranslationVendorPort vendor, TranslationCacheRepository cacheRepository,
			TranslateProperties properties, Clock clock) {
		this.vendor = vendor;
		this.cacheRepository = cacheRepository;
		this.properties = properties;
		this.clock = clock;
	}

	public TranslationResult translate(TranslationRequest request) {
		String sourceHash = request.sourceHash();
		Instant now = Instant.now(this.clock);

		Optional<String> cached = this.cacheRepository.findFreshTranslation(sourceHash, now);
		if (cached.isPresent()) {
			return new TranslationResult(cached.get(), true, null);
		}

		// 실패하면 TranslationVendorException 이 그대로 위로 올라간다.
		String translatedText = this.vendor.translate(request.sourceText(), request.direction());

		Instant expiresAt = now.plus(this.properties.getCacheTtl());
		this.cacheRepository.save(sourceHash, translatedText, now, expiresAt);
		return new TranslationResult(translatedText, false, this.vendor.providerName());
	}
}
