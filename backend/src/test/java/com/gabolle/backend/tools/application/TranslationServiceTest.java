package com.gabolle.backend.tools.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
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

	// ── 일괄 번역 (S15P21E201-1363) ─────────────────────────────────────────

	private TranslationService serviceWith(Clock clock) {
		return new TranslationService(this.vendor, this.cacheRepository, new TranslateProperties(), clock);
	}

	@Test
	@DisplayName("받은 순서 그대로 돌려주고, 같은 문장은 업체를 한 번만 부른다")
	void batchKeepsOrderAndTranslatesDuplicatesOnce() {
		this.vendor.echo = true;

		List<TranslationBatchItem> items = this.service.translateBatch(
				List.of("해운대", "광안리", "해운대"), TranslationDirection.KO_TO_JA);

		assertThat(items).extracting(TranslationBatchItem::translatedText)
				.containsExactly("번역:해운대", "번역:광안리", "번역:해운대");
		assertThat(this.vendor.callCount).isEqualTo(2);
	}

	@Test
	@DisplayName("캐시에 있는 문장은 업체를 안 부른다 — 단건 번역과 캐시를 나눠 쓴다")
	void batchSharesTheCacheWithSingleTranslation() {
		this.vendor.echo = true;
		this.service.translate(new TranslationRequest("해운대", TranslationDirection.KO_TO_JA));
		int before = this.vendor.callCount;

		List<TranslationBatchItem> items = this.service.translateBatch(List.of("해운대"), TranslationDirection.KO_TO_JA);

		assertThat(this.vendor.callCount).isEqualTo(before);
		assertThat(items.get(0).cached()).isTrue();
		assertThat(items.get(0).status()).isEqualTo(TranslationBatchItem.Status.TRANSLATED);
	}

	@Test
	@DisplayName("🔴 업체가 한 번 실패하면 남은 미번역 문장은 부르지 않는다 — 장애 때 문장마다 제한 시간을 기다리지 않게")
	void batchStopsCallingTheVendorAfterTheFirstFailure() {
		this.vendor.echo = true;
		this.vendor.failFromCall = 2;
		this.service.translate(new TranslationRequest("캐시에 있음", TranslationDirection.KO_TO_JA)); // 1번째 호출 — 성공

		List<TranslationBatchItem> items = this.service.translateBatch(
				List.of("첫째", "캐시에 있음", "셋째", "넷째"), TranslationDirection.KO_TO_JA);

		assertThat(items).extracting(TranslationBatchItem::status).containsExactly(
				TranslationBatchItem.Status.FAILED, TranslationBatchItem.Status.TRANSLATED,
				TranslationBatchItem.Status.FAILED, TranslationBatchItem.Status.FAILED);
		// 「첫째」에서 한 번 실패한 뒤로는 부르지 않았다 — 셋째·넷째를 위해 업체를 두 번 더 부르지 않았다.
		assertThat(this.vendor.callCount).isEqualTo(2);
	}

	@Test
	@DisplayName("🔴 못 옮긴 문장은 번역문 자리가 비어 있다 — 원문을 번역인 척 넣지 않는다")
	void failedItemsCarryNoText() {
		this.vendor.echo = true;
		this.vendor.failFromCall = 2;

		List<TranslationBatchItem> items = this.service.translateBatch(List.of("첫째", "둘째"), TranslationDirection.KO_TO_JA);

		assertThat(items.get(0).translatedText()).isEqualTo("번역:첫째");
		assertThat(items.get(1).translatedText()).isNull();
		assertThat(this.cacheRepository.store).hasSize(1);
	}

	@Test
	@DisplayName("🔴 하나도 못 옮기고 업체 실패만 있으면 예외로 올린다 — 200 에 빈손을 담지 않는다")
	void batchThatTranslatedNothingFails() {
		this.vendor.shouldFail = true;

		assertThatThrownBy(() -> this.service.translateBatch(List.of("첫째", "둘째"), TranslationDirection.KO_TO_JA))
				.isInstanceOf(TranslationVendorException.class);
		assertThat(this.vendor.callCount).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 시간 예산을 넘기면 남은 문장은 부르지 않고 SKIPPED — 앞단 시간 제한에 통째로 잃지 않게")
	void batchStopsAtTheTimeBudget() {
		this.vendor.echo = true;
		// 시계를 볼 때마다 10초씩 흐른다. 기한을 잴 때 0초 → 기한 20초. 첫째(10초)·둘째(20초)는 부르고
		// 셋째(30초)부터는 안 부른다.
		TranslationService ticking = serviceWith(new SteppingClock(NOW, Duration.ofSeconds(10)));

		List<TranslationBatchItem> items = ticking.translateBatch(
				List.of("첫째", "둘째", "셋째", "넷째"), TranslationDirection.KO_TO_JA);

		assertThat(items).extracting(TranslationBatchItem::status).containsExactly(
				TranslationBatchItem.Status.TRANSLATED, TranslationBatchItem.Status.TRANSLATED,
				TranslationBatchItem.Status.SKIPPED, TranslationBatchItem.Status.SKIPPED);
		assertThat(this.vendor.callCount).isEqualTo(2);
	}

	@Test
	@DisplayName("예산을 넘겨도 캐시에 있는 문장은 돌려준다 — 부르지 않는 것은 업체지 캐시가 아니다")
	void budgetDoesNotHideCachedTranslations() {
		this.vendor.echo = true;
		this.service.translate(new TranslationRequest("캐시에 있음", TranslationDirection.KO_TO_JA));
		TranslationService ticking = serviceWith(new SteppingClock(NOW, Duration.ofSeconds(30)));

		List<TranslationBatchItem> items = ticking.translateBatch(
				List.of("첫째", "둘째", "캐시에 있음"), TranslationDirection.KO_TO_JA);

		assertThat(items.get(2).status()).isEqualTo(TranslationBatchItem.Status.TRANSLATED);
		assertThat(items.get(2).cached()).isTrue();
	}

	@Test
	@DisplayName("문장이 없거나 상한을 넘으면 거절한다")
	void batchSizeIsBounded() {
		assertThatThrownBy(() -> this.service.translateBatch(List.of(), TranslationDirection.KO_TO_JA))
				.isInstanceOf(IllegalArgumentException.class);
		List<String> tooMany = java.util.Collections.nCopies(new TranslateProperties().getBatchMaxTexts() + 1, "해운대");
		assertThatThrownBy(() -> this.service.translateBatch(tooMany, TranslationDirection.KO_TO_JA))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("최대");
		assertThat(this.vendor.callCount).isZero();
	}

	@Test
	@DisplayName("빈 문장이 섞이면 몇 번째인지 말하며 거절한다 — 업체는 한 번도 안 부른다")
	void batchRejectsABlankTextByIndex() {
		assertThatThrownBy(() -> this.service.translateBatch(List.of("해운대", " "), TranslationDirection.KO_TO_JA))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageStartingWith("texts[1]");
		assertThat(this.vendor.callCount).isZero();
	}

	/** 볼 때마다 정해진 만큼 흐르는 시계 — 업체가 느린 상황을 실제로 기다리지 않고 만든다. */
	private static final class SteppingClock extends Clock {

		private Instant current;
		private final Duration step;

		SteppingClock(Instant start, Duration step) {
			this.current = start;
			this.step = step;
		}

		@Override
		public Instant instant() {
			Instant now = this.current;
			this.current = this.current.plus(this.step);
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
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
		/** 참이면 「번역:원문」을 돌려준다 — 일괄 번역에서 어느 답이 어느 문장 것인지 가려 보려고. */
		boolean echo = false;
		/** 이 번째 호출(1부터)부터 실패한다. */
		int failFromCall = Integer.MAX_VALUE;

		@Override
		public String translate(String sourceText, TranslationDirection direction) {
			this.callCount++;
			if (this.shouldFail || this.callCount >= this.failFromCall) {
				throw new TranslationVendorException("TRANSLATE_VENDOR_UNAVAILABLE", "실패", HttpStatus.BAD_GATEWAY);
			}
			return this.echo ? "번역:" + sourceText : "hello";
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
	}
}
