package com.gabolle.backend.tools.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.tools.config.TranslateProperties;
import com.gabolle.backend.tools.domain.TranslationCacheRepository;
import com.gabolle.backend.tools.domain.TranslationDirection;
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

	/**
	 * 여러 문장을 한 번에 번역한다 — 장소 이름·설명, 축제, 다른 사람의 기록처럼 서버가 한국어로만 가진 글을
	 * 일본어·중국어 화면에 보여 주려고 쓴다(S15P21E201-1363).
	 *
	 * <p>응답마다 {@code Accept-Language} 를 보고 서버가 알아서 번역하는 방식으로 하지 않았다. 목록 한 번 부를 때
	 * 캐시에 없는 이름 수십 개가 줄줄이 업체를 불러 목록 자체가 느려지고, 무엇보다 앱은 {@code Accept-Language}
	 * 로 {@code ko}·{@code en} 만 보낸다. 화면이 목록을 받은 뒤 필요한 글만 모아 따로 부르는 편이 둘 다 피한다.
	 *
	 * <ul>
	 * <li>같은 요청 안의 같은 문장은 한 번만 옮긴다 — 목록에는 같은 지명이 되풀이된다</li>
	 * <li>캐시에 있으면 업체를 부르지 않는다. 단건 번역과 캐시를 나눠 쓴다(열쇠가 같다)</li>
	 * <li>🔴 업체가 한 번 실패하면 남은 미번역 문장은 부르지 않고 FAILED — 업체 장애는 대개 한꺼번에 오고,
	 *     그때 문장마다 제한 시간(5초)을 기다리면 요청 하나가 몇 분을 잡는다</li>
	 * <li>🔴 시간 예산을 넘기면 남은 문장은 SKIPPED — 앞단의 시간 제한에 걸려 통째로 잃는 것보다 낫다</li>
	 * <li>하나도 못 옮기고 업체 실패만 있으면 {@link TranslationVendorException} 을 그대로 올린다 — 200 에 빈손을 담지 않는다</li>
	 * </ul>
	 *
	 * @throws IllegalArgumentException 문장이 없거나 너무 많거나, 어느 한 문장이 비었거나 너무 길 때. 몇 번째인지 말한다
	 */
	public List<TranslationBatchItem> translateBatch(List<String> texts, TranslationDirection direction) {
		if (texts == null || texts.isEmpty()) {
			throw new IllegalArgumentException("texts 가 비어 있습니다.");
		}
		if (texts.size() > this.properties.getBatchMaxTexts()) {
			throw new IllegalArgumentException(
					"texts 가 너무 많습니다: " + texts.size() + "개 (최대 " + this.properties.getBatchMaxTexts() + "개)");
		}

		List<TranslationRequest> requests = new ArrayList<>(texts.size());
		for (int index = 0; index < texts.size(); index++) {
			try {
				requests.add(new TranslationRequest(texts.get(index), direction));
			}
			catch (IllegalArgumentException invalid) {
				throw new IllegalArgumentException("texts[" + index + "]: " + invalid.getMessage(), invalid);
			}
		}

		Instant deadline = Instant.now(this.clock).plus(this.properties.getBatchTimeBudget());
		Map<String, TranslationBatchItem> byHash = new HashMap<>();
		TranslationVendorException vendorFailure = null;

		for (TranslationRequest request : requests) {
			String sourceHash = request.sourceHash();
			if (byHash.containsKey(sourceHash)) {
				continue;
			}
			Instant now = Instant.now(this.clock);
			Optional<String> cached = this.cacheRepository.findFreshTranslation(sourceHash, now);
			if (cached.isPresent()) {
				byHash.put(sourceHash, TranslationBatchItem.translated(cached.get(), true));
				continue;
			}
			if (vendorFailure != null) {
				byHash.put(sourceHash, TranslationBatchItem.failed());
				continue;
			}
			if (now.isAfter(deadline)) {
				byHash.put(sourceHash, TranslationBatchItem.skipped());
				continue;
			}
			try {
				String translatedText = this.vendor.translate(request.sourceText(), request.direction());
				this.cacheRepository.save(sourceHash, translatedText, now, now.plus(this.properties.getCacheTtl()));
				byHash.put(sourceHash, TranslationBatchItem.translated(translatedText, false));
			}
			catch (TranslationVendorException failure) {
				vendorFailure = failure;
				byHash.put(sourceHash, TranslationBatchItem.failed());
			}
		}

		List<TranslationBatchItem> items = requests.stream().map((r) -> byHash.get(r.sourceHash())).toList();
		boolean anyTranslated = items.stream()
				.anyMatch((item) -> item.status() == TranslationBatchItem.Status.TRANSLATED);
		if (vendorFailure != null && !anyTranslated) {
			throw vendorFailure;
		}
		return items;
	}
}
