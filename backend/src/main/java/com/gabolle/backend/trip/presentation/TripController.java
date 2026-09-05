package com.gabolle.backend.trip.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequest;
import com.gabolle.backend.trip.presentation.dto.TripDetailResponse;
import com.gabolle.backend.trip.presentation.dto.TripDto;

import jakarta.validation.Valid;

/**
 * 여행 생성·조회 — S15P21E201-461 · TRIP-01.
 *
 * <p>🔴 경로와 응답 코드를 발명하지 않았다. API 명세 3.4 가 이미 정의한다 —
 * {@code POST /trips} → {@code 201 TripDto + preferenceSnapshot}.
 *
 * <p>🔴 <b>생성 요청은 일정을 계산하지 않는다.</b> 조건만 저장하고 즉시 응답한다.
 * 계산은 별도 호출({@code REC-01})이 {@code 202 JobDto} 로 시작한다.
 * 티켓 본문이 두 가지를 한 문장으로 써서 하나로 착각하기 쉬운 자리다.
 */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripCreationService creationService;
    private final TripQueryService queryService;

    public TripController(TripCreationService creationService, TripQueryService queryService) {
        this.creationService = creationService;
        this.queryService = queryService;
    }

    /**
     * 여행 조건을 저장한다.
     *
     * <p>🔴 {@code Idempotency-Key} 헤더를 받는다(API-09). 같은 키에 같은 본문이면
     * <b>기존 여행을 그대로 돌려준다</b> — 지하철에서 응답이 끊긴 앱은 반드시
     * 재시도하고, 그때 여행이 두 개 생기면 안 된다.
     *
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TripDto>> create(
            @Valid @RequestBody CreateTripRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {

        String requestId = "req_" + UUID.randomUUID();
        String creator = AuthenticatedUsers.requireId(authentication).toString();

        TripCreationService.Result result =
                creationService.create(toCommand(request, creator), idempotencyKey);

        // 🔴 재시도였으면 200, 새로 만들었으면 201. 둘 다 성공이다 —
        //    재시도에 4xx 를 주면 사용자 화면에 오류가 뜬다.
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status)
                .body(ApiResponse.success(TripDto.of(result.trip(), result.snapshot()), requestId));
    }

    /**
     * 여행 한 건을 조회한다 — 완료 기준
     * <b>"그 식별자로 조회하면 보낸 조건이 그대로 나온다"</b>.
     *
     * <p>🔴 없는 여행이거나 요청자가 그 여행의 회원이 아니면 <b>둘 다 404</b> 다.
     * 회원이 아닌 사람에게 "있는데 너는 못 본다"(403)를 알려주면 존재 자체가 샌다.
     */
    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> get(
            @PathVariable String tripId,
            Authentication authentication) {

        String requester = AuthenticatedUsers.requireId(authentication).toString();
        TripQueryService.View view = queryService.get(tripId, requester);

        return ApiResponse.success(
                TripDetailResponse.of(view.trip(), view.constraints(), view.snapshot()),
                "req_" + UUID.randomUUID());
    }

    private TripCreationService.Command toCommand(CreateTripRequest r, String userId) {
        List<TripCreationService.Command.ConstraintInput> constraints =
                r.constraints() == null ? List.of()
                        : r.constraints().stream().map(c ->
                        new TripCreationService.Command.ConstraintInput(
                                c.type(),
                                c.constraintKey(),
                                parseSeverity(c.severity()),
                                c.operator(),
                                c.value(),
                                c.threshold(),
                                // 사용자가 직접 넣은 값이므로 아직 검증되지 않았다 (NFR-09).
                                TripConstraint.EvidenceStatus.NEEDS_REVIEW,
                                parseConstraintAnswerStatus(c.answerStatus()),
                                parseDietRequirement(c.dietRequirement())))
                        .toList();

        List<PreferenceSnapshot.PreferenceAnswer> preferences =
                r.preferences() == null ? List.of()
                        : r.preferences().stream()
                                .map(p -> new PreferenceSnapshot.PreferenceAnswer(
                                        p.dimension(), p.value(), parsePreferenceAnswerStatus(p.answerStatus())))
                                .toList();

        return new TripCreationService.Command(
                userId, r.startDate(), r.finishDate(),
                r.originLat(), r.originLng(),
                r.budgetKrw(), r.partySize(),
                r.timeWindow(), r.timezone(),
                preferences,
                constraints);
    }

    private TripConstraint.Severity parseSeverity(String raw) {
        try {
            return TripConstraint.Severity.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("severity 는 HARD 또는 SOFT 여야 한다: " + raw);
        }
    }

    private TripConstraint.AnswerStatus parseConstraintAnswerStatus(String raw) {
        try {
            return TripConstraint.AnswerStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "제약의 answerStatus 는 SELECTED · NONE · UNKNOWN 중 하나여야 한다: " + raw);
        }
    }

    private PreferenceSnapshot.AnswerStatus parsePreferenceAnswerStatus(String raw) {
        try {
            return PreferenceSnapshot.AnswerStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "취향의 answerStatus 는 SELECTED · SKIPPED · UNKNOWN 중 하나여야 한다: " + raw);
        }
    }

    /**
     * 🔴 2026-09-04 추가. {@code null} 이면 그대로 {@code null} 을 돌려준다 —
     * {@code DIET} 가 아닌 제약은 이 값이 없는 것이 정상이다({@code TripConstraint}
     * 생성자가 그 경우를 검증한다).
     */
    private TripConstraint.DietRequirement parseDietRequirement(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return TripConstraint.DietRequirement.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "dietRequirement 는 REQUIRED · PREFERRED 중 하나여야 한다: " + raw);
        }
    }
}
