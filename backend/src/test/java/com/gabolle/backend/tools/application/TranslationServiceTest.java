package com.gabolle.backend.tools.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.gabolle.backend.tools.config.TranslateProperties;
import com.gabolle.backend.tools.domain.TranslationCacheRepository;
import com.gabolle.backend.tools.domain.TranslationDirection;
import com.gabolle.backend.tools.domain.TranslationRequest;
import com.gabolle.backend.tools.domain.TranslationResult;

/**
 * 캐시 히트에서 업체를 안 부르는 것과, 업체 실패가 그대로 위로 올라가는 것을 잰다. 로그에 원문이 안
 * 남는지는 {@code TranslationVendorAdapterTest} 가 잰다 — 이 클래스는 로거 자체가 없다.
 */
class TranslationServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

	private InMemoryCacheRepository cacheRepository;
	private CountingVendor vendor;
	private TranslationService service;

	@BeforeEach
	void setUp() {
		this.cacheRepository = new InMemoryCacheRepository();
		this.vendor = new CountingVendor();
		TranslateProperties properties = new TranslateProperties();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		this.service = new TranslationService(this.vendor, this.cacheRepository, properties, clock);
	}

	@Test
	@DisplayName("캐시가 비어 있으면 업체를 부르고 결과를 담아 둔다")
	void callsVendorOnCacheMissAndStoresResult() {
		TranslationResult result = this.service.translate(new TranslationRequest("안녕", TranslationDirection.KO_TO_EN));

		assertThat(result.translatedText()).isEqualTo("hello");
		assertThat(result.cached()).isFalse();
		assertThat(this.vendor.callCount).isEqualTo(1);
		assertThat(this.cacheRepository.store).hasSize(1);
	}

	@Test
	@DisplayName("🔴 같은 문장을 두 번 보내면 두 번째는 외부 호출이 없다 — 캐시 히트")
	void secondCallForSameSentenceIsACacheHit() {
		TranslationRequest request = new TranslationRequest("안녕", TranslationDirection.KO_TO_EN);

		TranslationResult first = this.service.translate(request);
		TranslationResult second = this.service.translate(request);

		assertThat(first.cached()).isFalse();
		assertThat(second.cached()).isTrue();
		assertThat(second.translatedText()).isEqualTo("hello");
		// 두 번째 호출에서 벤더가 다시 불리면 실패한다.
		assertThat(this.vendor.callCount).isEqualTo(1);
	}

	@Test
	@DisplayName("방향이 다르면 같은 문장이어도 캐시를 공유하지 않는다")
	void differentDirectionIsADifferentCacheEntry() {
		this.service.translate(new TranslationRequest("안녕", TranslationDirection.KO_TO_EN));
		this.service.translate(new TranslationRequest("안녕", TranslationDirection.EN_TO_KO));

		assertThat(this.vendor.callCount).isEqualTo(2);
	}

	@Test
	@DisplayName("만료된 캐시는 미스로 취급해 업체를 다시 부른다")
	void expiredCacheIsATreatedAsAMiss() {
		Instant past = NOW.minusSeconds(60);
		Instant alreadyExpired = NOW.minusSeconds(1);
		String hash = new TranslationRequest("안녕", TranslationDirection.KO_TO_EN).sourceHash();
		this.cacheRepository.save(hash, "old-cached-value", past, alreadyExpired);

		TranslationResult result = this.service.translate(new TranslationRequest("안녕", TranslationDirection.KO_TO_EN));

		assertThat(result.cached()).isFalse();
		assertThat(result.translatedText()).isEqualTo("hello");
		assertThat(this.vendor.callCount).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 업체 호출이 실패하면 응답에 실패가 담겨 온다 — 미리 정해 둔 문장으로 숨기지 않는다")
	void vendorFailurePropagatesInsteadOfBeingHidden() {
		this.vendor.shouldFail = true;

		assertThatThrownBy(() -> this.service.translate(new TranslationRequest("안녕", TranslationDirection.KO_TO_EN)))
				.isInstanceOf(TranslationVendorException.class);

		// 실패했으니 캐시에도 아무것도 안 남아야 한다 — 실패를 성공인 척 담아 두면 안 된다.
		assertThat(this.cacheRepository.store).isEmpty();
	}

	private static final class InMemoryCacheRepository implements TranslationCacheRepository {

		final Map<String, Entry> store = new HashMap<>();

		@Override
		public Optional<String> findFreshTranslation(String sourceHash, Instant now) {
			Entry entry = this.store.get(sourceHash);
			if (entry == null || !entry.expiresAt.isAfter(now)) {
				return Optional.empty();
			}
			return Optional.of(entry.translatedText);
		}

		@Override
		public void save(String sourceHash, String translatedText, Instant now, Instant expiresAt) {
			this.store.put(sourceHash, new Entry(translatedText, expiresAt));
		}

		private record Entry(String translatedText, Instant expiresAt) {
		}
	}

	private static final class CountingVendor implements TranslationVendorPort {

		int callCount = 0;
		boolean shouldFail = false;

		@Override
		public String translate(String sourceText, TranslationDirection direction) {
			this.callCount++;
			if (this.shouldFail) {
				throw new TranslationVendorException("TRANSLATE_VENDOR_UNAVAILABLE", "실패", HttpStatus.BAD_GATEWAY);
			}
			return "hello";
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
	}
}
