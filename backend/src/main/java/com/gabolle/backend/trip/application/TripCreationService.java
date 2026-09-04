package com.gabolle.backend.trip.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행 생성 — S15P21E201-461 · TRIP-01.
 *
 * <p>이 계층이 하는 일은 <b>순서를 조율하고 트랜잭션 경계를 긋는 것</b>이다.
 * 업무 규칙은 도메인 생성자에 있다.
 */
@Service
public class TripCreationService {

    private final TripRepository repository;
    private final Clock clock;

    public TripCreationService(TripRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 여행을 만든다.
     *
     * <p>🔴 <b>넷이 하나의 트랜잭션이다.</b> 나뉘면 "여행은 생겼는데 소유자가 없는" 또는
     * "제약이 절반만 들어간" 상태가 생긴다. 그러면 만든 사람이 자기 여행을 못 보거나,
     * 알레르기 제약이 빠진 채로 추천이 돌아간다.
     *
     * <p>🔴 같은 {@code Idempotency-Key} 로 다시 오면 <b>기존 여행을 돌려준다</b>(API-09).
     * 지하철에서 응답이 끊긴 앱은 반드시 재시도하고, 그때 여행이 두 개 생기면 안 된다.
     *
     * @param idempotencyKey 없으면 중복 방지를 하지 않는다. 클라이언트가 붙여야 한다
     * @return 새로 만들었으면 {@code created=true}, 이미 있었으면 {@code false}
     */
    @Transactional
    public Result create(Command command, String idempotencyKey) {

        String fingerprint = fingerprintOf(command);

        Instant now = clock.instant();
        String tripId = UUID.randomUUID().toString();

        // ① 여행. 생성자가 조건을 검증한다 — 종료일이 시작일보다 앞이면 여기서 거부된다.
        Trip trip = new Trip(tripId, command.userId(),
                command.startDate(), command.finishDate(),
                command.originLat(), command.originLng(),
                command.budgetKrw(), command.partySize(),
                command.timeWindow(), command.timezone(), now);

        // ② 제약. 🔴 민감 종류(알레르기·필수 식단)에 값이 들어오면 생성자가 거부한다 —
        //    M1 에는 암호화 경로가 없고, 평문으로 한 번 저장하면 그 데이터가 남는다.
        //    scope 는 TRIP 으로 고정한다 — TRIP-01 이 만드는 제약은 항상 이번 여행
        //    전용이다(PersonalizationScope 문서 참고).
        List<TripConstraint> constraints = new ArrayList<>();
        List<String> constraintIds = new ArrayList<>();
        for (Command.ConstraintInput c : command.constraints()) {
            String id = UUID.randomUUID().toString();
            constraints.add(new TripConstraint(id, tripId, c.type(), c.severity(),
                    c.operator(), c.value(), c.threshold(), c.evidenceStatus(),
                    c.answerStatus(), PersonalizationScope.TRIP, c.dietRequirement()));
            constraintIds.add(id);
        }

        // ③ 🔴 만든 사람을 OWNER 로 넣는다. 안 넣으면 자기 여행을 못 본다.
        TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, command.userId(), now);

        // ④ 선호 스냅샷 v1. 계정 취향을 복사해 굳힌다 — 나중에 계정 취향이 바뀌어도
        //    이 여행이 무엇으로 만들어졌는지는 안 바뀐다 (NFR-08). scope 는 TRIP 고정 —
        //    위 제약과 같은 이유다.
        PreferenceSnapshot snapshot = new PreferenceSnapshot(
                UUID.randomUUID().toString(), tripId, 1,
                command.preferences(), PersonalizationScope.TRIP, constraintIds, now);

        // ⑤ 🔴 키 확보와 저장을 한 동작으로 한다.
        //    나누면 같은 키로 동시에 온 요청이 전부 여행을 만든다 — 테스트가 잡았다.
        //    여기서 만든 Trip 객체는 경쟁에서 지면 버려진다. 저장되지 않으므로 문제없다.
        TripRepository.SaveOutcome outcome = repository.saveWithIdempotency(
                command.userId(), idempotencyKey, fingerprint,
                trip, constraints, owner, snapshot);

        return new Result(outcome.trip(), outcome.snapshot(), outcome.created());
    }

    /**
     * 본문의 지문.
     *
     * <p>🔴 같은 키를 <b>다른 내용</b>으로 재사용하면 409 로 거부해야 한다(API-09).
     * 그러려면 "같은 내용인가" 를 비교할 것이 필요하다.
     */
    private String fingerprintOf(Command c) {
        String raw = String.join("|",
                c.userId(), String.valueOf(c.startDate()), String.valueOf(c.finishDate()),
                String.valueOf(c.originLat()), String.valueOf(c.originLng()),
                String.valueOf(c.budgetKrw()), String.valueOf(c.partySize()),
                String.valueOf(c.timeWindow()), String.valueOf(c.timezone()),
                String.valueOf(c.preferences()), String.valueOf(c.constraints()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 이 없다", e);
        }
    }

    /** 응용 계층 입력. 표현 계층 DTO 를 도메인까지 끌고 들어가지 않는다. */
    public record Command(
            String userId,
            LocalDate startDate,
            LocalDate finishDate,
            Double originLat,
            Double originLng,
            Integer budgetKrw,
            int partySize,
            String timeWindow,
            String timezone,
            List<PreferenceSnapshot.PreferenceAnswer> preferences,
            List<ConstraintInput> constraints) {

        public record ConstraintInput(
                String type,
                TripConstraint.Severity severity,
                String operator,
                String value,
                Double threshold,
                TripConstraint.EvidenceStatus evidenceStatus,
                TripConstraint.AnswerStatus answerStatus,
                TripConstraint.DietRequirement dietRequirement) {}
    }

    /** {@code created=false} 면 재시도였고 기존 여행을 돌려준 것이다. */
    public record Result(Trip trip, PreferenceSnapshot snapshot, boolean created) {}
}
