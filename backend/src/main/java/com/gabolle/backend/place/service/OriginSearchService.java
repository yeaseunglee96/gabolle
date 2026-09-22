package com.gabolle.backend.place.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.place.adapter.OriginCandidate;
import com.gabolle.backend.place.adapter.OriginSearchPort;
import com.gabolle.backend.place.adapter.OriginSearchProperties;
import com.gabolle.backend.place.api.OriginSearchResponse;

/**
 * 출발지 검색 — 카카오 로컬 검색과 {@code place} 표 대체 목록을 가른다.
 *
 * <p>길이 검사가 어댑터 호출보다 먼저여야 한다. 한 글자 검색어는 외부 호출이 아예 나가지 않는
 * 것이 계약이라, 먼저 부르고 응답만 걸러내면 지킨 것이 아니다.
 *
 * <p>키가 없거나 호출이 실패해도 오류가 아니다 — 둘 다 200 으로 대체 목록을 주고
 * {@code degraded=true} 와 이유({@link DegradedReason})를 실어 호출한 쪽이 구분하게 한다.
 * 대체 목록 조회까지 실패하면 빈 목록을 준다. 대체 경로가 원본보다 더 잘 죽으면 안 된다.
 */
@Service
@Profile({"db", "dev"})
public class OriginSearchService {

	private static final Logger log = LoggerFactory.getLogger(OriginSearchService.class);

	private static final int DEFAULT_LIMIT = 10;

	/** 응답의 {@code degradedReason} 자리에 이름 그대로 실린다. */
	public enum DegradedReason {
		PROVIDER_KEY_MISSING,
		/** 호출이 실패했거나 시간을 넘겼다. */
		PROVIDER_UNAVAILABLE,
		/** provider 를 {@code NONE} 으로 꺼 뒀다. */
		PROVIDER_DISABLED
	}

	private final OriginSearchPort kakaoPort;

	private final OriginSearchPort fallbackPort;

	private final OriginSearchProperties properties;

	public OriginSearchService(@Qualifier("kakaoLocalOriginSearchAdapter") OriginSearchPort kakaoPort,
			@Qualifier("internalOriginSearchFallback") OriginSearchPort fallbackPort,
			OriginSearchProperties properties) {
		this.kakaoPort = kakaoPort;
		this.fallbackPort = fallbackPort;
		this.properties = properties;
	}

	/**
	 * {@code rawQuery} 를 다듬은 길이가 2 미만이면 {@link PlaceRequestException}
	 * ({@code QUERY_TOO_SHORT})을 던진다 — 그 뒤에야 카카오 포트를 부른다.
	 */
	public OriginSearchResponse search(String rawQuery, int rawLimit) {
		String query = rawQuery == null ? "" : rawQuery.strip();
		if (query.length() < 2) {
			throw new PlaceRequestException("QUERY_TOO_SHORT", "두 글자 이상 입력해 주세요.", List.of("query"));
		}
		int limit = clampLimit(rawLimit);

		if ("NONE".equalsIgnoreCase(this.properties.getProvider())) {
			return degrade(query, limit, DegradedReason.PROVIDER_DISABLED);
		}
		String apiKey = this.properties.getKakaoRestApiKey();
		if (apiKey == null || apiKey.isBlank()) {
			return degrade(query, limit, DegradedReason.PROVIDER_KEY_MISSING);
		}
		try {
			List<OriginCandidate> candidates = this.kakaoPort.search(query, limit);
			return toResponse(candidates, false, null, limit);
		} catch (RuntimeException exception) {
			log.warn("출발지 검색 provider=KAKAO_LOCAL 호출 실패로 대체 목록으로 전환합니다.", exception);
			return degrade(query, limit, DegradedReason.PROVIDER_UNAVAILABLE);
		}
	}

	private OriginSearchResponse degrade(String query, int limit, DegradedReason reason) {
		List<OriginCandidate> candidates;
		try {
			candidates = this.fallbackPort.search(query, limit);
		} catch (RuntimeException exception) {
			log.warn("출발지 검색 대체 목록 조회에도 실패해 빈 목록으로 응답합니다.", exception);
			candidates = List.of();
		}
		return toResponse(candidates, true, reason.name(), limit);
	}

	private OriginSearchResponse toResponse(List<OriginCandidate> candidates, boolean degraded,
			String degradedReason, int limit) {
		List<OriginSearchResponse.Item> items = candidates.stream()
				.limit(limit)
				.map(OriginSearchResponse.Item::from)
				.toList();
		return new OriginSearchResponse(items, degraded, degradedReason, limit);
	}

	private int clampLimit(int rawLimit) {
		int effective = rawLimit <= 0 ? DEFAULT_LIMIT : rawLimit;
		return Math.min(effective, this.properties.getMaxLimit());
	}
}
