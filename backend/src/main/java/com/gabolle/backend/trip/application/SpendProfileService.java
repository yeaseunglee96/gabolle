package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 계정 기본 씀씀이 성향 — 읽기·쓰기.
 *
 * <p>{@link PreferenceDefaultsService#replace}(전체 교체)를 그대로 쓰지 않는다. 같은 스냅샷에
 * 다른 차원의 답도 들어 있어, 전체 교체로 SPEND_PROFILE 만 바꾸면 나머지 차원이 사라진다.
 * 그래서 현재 판을 읽어 SPEND_PROFILE 만 갈아 끼우고 전부 다시 저장한다.
 */
@Service
@Profile({ "db", "dev" })
public class SpendProfileService {

	private static final String DIMENSION = "SPEND_PROFILE";

	private final TripRepository repository;

	private final EventIngestService eventIngestService;

	private final Clock clock;

	public SpendProfileService(TripRepository repository, EventIngestService eventIngestService, Clock clock) {
		this.repository = repository;
		this.eventIngestService = eventIngestService;
		this.clock = clock;
	}

	/** 계정 기본 SPEND_PROFILE. 저장한 적 없으면 비어 있다. */
	public Optional<PreferenceSnapshot.PreferenceAnswer> find(UUID userId) {
		return this.repository.findUserDefaults(userId.toString())
				.flatMap(snapshot -> snapshot.answers().stream()
						.filter(answer -> DIMENSION.equals(answer.dimension()))
						.findFirst());
	}

	/** SPEND_PROFILE 만 새 값으로 바꿔 새 판으로 저장한다. 다른 차원의 답은 그대로 옮긴다. */
	@Transactional
	public PreferenceSnapshot.PreferenceAnswer put(UUID userId, String value, String answerStatusRaw,
			UUID requestId) {

		PreferenceSnapshot.PreferenceAnswer newAnswer = new PreferenceSnapshot.PreferenceAnswer(
				DIMENSION, value, parseAnswerStatus(answerStatusRaw));

		List<PreferenceSnapshot.PreferenceAnswer> merged = new ArrayList<>();
		this.repository.findUserDefaults(userId.toString()).ifPresent(current -> {
			for (PreferenceSnapshot.PreferenceAnswer answer : current.answers()) {
				if (!DIMENSION.equals(answer.dimension())) {
					merged.add(answer);
				}
			}
		});
		merged.add(newAnswer);

		this.repository.saveUserDefaults(userId.toString(), merged, Instant.now(this.clock));

		this.eventIngestService.recordFromServer(UUID.randomUUID(), EventType.PREFERENCE_SET, 1,
				userId, null, requestId, Map.of("dimension", DIMENSION));

		return newAnswer;
	}

	private static PreferenceSnapshot.AnswerStatus parseAnswerStatus(String raw) {
		try {
			return PreferenceSnapshot.AnswerStatus.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException | NullPointerException e) {
			throw new IllegalArgumentException(
					"answerStatus 는 SELECTED · SKIPPED · UNKNOWN 중 하나여야 한다: " + raw);
		}
	}
}
