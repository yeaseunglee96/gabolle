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
 * 출발지 검색의 핵심 로직 (S15P21E201-434). 카카오 로컬 검색과 {@code place} 표 대체 목록을 가른다.
 *
 * <h2>🔴 길이 검사가 어댑터 호출보다 먼저다</h2>
 *
 * <p>다듬은 질의어 길이가 2 미만이면 카카오 포트를 부르기 전에 예외를 던진다. 완료 기준
 * "한 글자 검색어는 외부 호출이 나가지 않는다" 는 결과가 아니라 <b>순서</b>다 — 먼저 부르고
 * 응답만 걸러내면 이 기준을 지킨 것이 아니다. {@code OriginSearchServiceTest} 가 호출 횟수 0 을
 * 실측한다.
 *
 * <h2>키가 없거나 호출이 실패해도 오류가 아니다</h2>
 *
 * <p>둘 다 200 으로 대체 목록을 준다. 응답에 {@code degraded=true} 와 이유
 * ({@link DegradedReason})를 실어서, 호출한 쪽이 "카카오가 잠깐 아픈 것" 과 "설정을 안 한 것" 을
 * 구분할 수 있게 한다. 대체 목록 조회 자체가 실패해도(예: DB 문제) 여기서 예외를 삼키고 빈 목록을
 * 준다 — 대체 경로가 원본 경로보다 더 잘 죽으면 안 된다.
 */
@Service
@Profile({"db", "dev"})
public class OriginSearchService {

	private static final Logger log = LoggerFactory.getLogger(OriginSearchService.class);

	private static final int DEFAULT_LIMIT = 10;

	/** 응답의 {@code degradedReason} 자리에 그대로 실리는 이유. */
	public enum DegradedReason {
		/** 설정에 카카오 키가 없다. */
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
	 * 출발지 후보를 찾는다. {@code rawQuery} 를 다듬은 길이가 2 미만이면
	 * {@link PlaceRequestException}({@code QUERY_TOO_SHORT})을 던진다 — 이 시점 이후로만
	 * 카카오 포트를 부른다.
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
