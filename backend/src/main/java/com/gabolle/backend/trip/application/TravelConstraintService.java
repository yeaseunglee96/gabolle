package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.trip.domain.TravelConstraintAnswer;
import com.gabolle.backend.trip.domain.TravelConstraintStatus;
import com.gabolle.backend.trip.infra.TravelConstraintJpaEntity;
import com.gabolle.backend.trip.infra.TravelConstraintJpaRepository;

/**
 * 여행 조건 모달의 답 — 읽기·쓰기.
 *
 * <p>씀씀이({@link SpendProfileService})와 달리 표가 따로라 스냅샷을 읽어 합칠 일이 없다.
 * 표를 나눈 근거는 마이그레이션 주석에 있다.
 *
 * <p>바꿀 때 행을 지웠다 새로 만들지 않는다 — {@code created_at} 이 「처음 답한 시각」으로
 * 남아야 한다.
 */
@Service
@Profile({ "db", "dev" })
public class TravelConstraintService {

	/** 이벤트에 적는 차원 이름. 씀씀이가 {@code SPEND_PROFILE} 을 쓰는 자리와 같다. */
	private static final String DIMENSION = "TRAVEL_CONSTRAINTS";

	private final TravelConstraintJpaRepository repository;

	private final EventIngestService eventIngestService;

	private final Clock clock;

	public TravelConstraintService(TravelConstraintJpaRepository repository,
			EventIngestService eventIngestService, Clock clock) {
		this.repository = repository;
		this.eventIngestService = eventIngestService;
		this.clock = clock;
	}

	/** 이 사람의 답. 비어 있으면 「한 번도 안 물어봤다」이지 오류가 아니다 — 부르는 쪽이 200 으로 옮긴다. */
	@Transactional(readOnly = true)
	public Optional<TravelConstraintAnswer> find(UUID userId) {
		// 엔티티를 그대로 내보내지 않는다 — LayeringArchitectureTest 가 도메인 타입 변환을 요구한다.
		return this.repository.findById(userId).map(TravelConstraintService::toAnswer);
	}

	/**
	 * 답을 저장한다. 이미 있으면 그 줄을 고친다.
	 *
	 * <p>{@code SAVED} 가 아닌데 값이 오면 거부하지 않고 값만 버린다({@link TravelConstraintJpaEntity#update}).
	 * 모달에 적다가 「나중에」를 누르는 것이 정상 흐름이라 400 을 주면 안 된다.
	 */
	@Transactional
	public TravelConstraintAnswer put(UUID userId, String value, String answerStatusRaw, UUID requestId) {
		TravelConstraintStatus status = parseStatus(answerStatusRaw);
		OffsetDateTime now = OffsetDateTime.ofInstant(this.clock.instant(), ZoneOffset.UTC);

		TravelConstraintJpaEntity saved = this.repository.findById(userId)
				.map(existing -> {
					existing.update(status, value, now);
					return existing;
				})
				.orElseGet(() -> this.repository.save(status == TravelConstraintStatus.SAVED
						? TravelConstraintJpaEntity.saved(userId, value, now)
						: TravelConstraintJpaEntity.withoutValue(userId, status, now)));

		this.eventIngestService.recordFromServer(UUID.randomUUID(), EventType.PREFERENCE_SET, 1,
				userId, null, requestId, Map.of("dimension", DIMENSION, "status", status.name()));

		return toAnswer(saved);
	}

	private static TravelConstraintAnswer toAnswer(TravelConstraintJpaEntity entity) {
		return new TravelConstraintAnswer(entity.getStatus(), entity.getValueJson());
	}

	private static TravelConstraintStatus parseStatus(String raw) {
		try {
			return TravelConstraintStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException | NullPointerException e) {
			throw new IllegalArgumentException(
					"answerStatus 는 SAVED · LATER · NEVER 중 하나여야 한다: " + raw);
		}
	}
}
