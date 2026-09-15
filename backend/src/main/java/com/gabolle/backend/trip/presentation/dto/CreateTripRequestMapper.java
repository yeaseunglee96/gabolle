package com.gabolle.backend.trip.presentation.dto;

import java.util.ArrayList;
import java.util.List;

import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.domain.PreferenceDimensions;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;

/**
 * {@link CreateTripRequest}(앱이 보내는 모양) → {@link TripCreationService.Command}(도메인 어휘) 번역.
 *
 * <p>원래 {@code TripController} 의 private 메서드였다. S15P21E201-338(공유 일정 복제)이 같은 본문을
 * 받아 같은 규칙으로 여행을 만들어야 해서 여기로 옮겼다 — 특히 -665 의 차원 이름 정규화가 두 경로에서
 * 똑같이 돌아야 한다. 복사해 두면 규칙이 바뀐 날 한쪽만 고쳐진다.
 *
 * <p>🔴 이 클래스는 업무 규칙을 갖지 않는다. 문자열을 enum 으로, DTO 를 명령으로 바꾸는 것이 전부다.
 * 여행의 규칙(종료일이 시작일보다 뒤 등)은 {@code Trip} 생성자가, 저장 규칙은 서비스가 본다.
 */
public final class CreateTripRequestMapper {

    private CreateTripRequestMapper() {
    }

    public static TripCreationService.Command toCommand(CreateTripRequest r, String userId) {
        return toCommand(r, userId, Trip.OwnerType.USER);
    }

    /**
     * S15P21E201-317 — 익명 세션이 여행을 만드는 경로가 쓴다. {@code ownerType} 만 더할 뿐,
     * 그 외 번역 규칙(차원 이름 정규화 등)은 위 오버로드와 똑같다.
     */
    public static TripCreationService.Command toCommand(CreateTripRequest r, String userId,
            Trip.OwnerType ownerType) {
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

        List<PreferenceSnapshot.PreferenceAnswer> preferences = new ArrayList<>(
                r.preferences() == null ? List.of()
                        : r.preferences().stream()
                                .map(p -> new PreferenceSnapshot.PreferenceAnswer(
                                        // 🔴 S15P21E201-665 — 앱은 차원 이름을 소문자 camelCase
                                        //    (category, touristPreference …)로 보내고, preference_answer 의
                                        //    CHECK 는 대문자(CATEGORY, TOURIST_PREFERENCE …)만 받는다.
                                        //    그대로 넘겼더니 취향 한 줄이 CHECK 에 걸려 여행 트랜잭션
                                        //    전체가 롤백됐다 — 실제 앱은 여행을 만들 수 없었다. DTO 문자열을
                                        //    도메인 어휘로 바꾸는 것은 이 번역 계층의 일이다.
                                        PreferenceDimensions.normalize(p.dimension()),
                                        p.value(), parsePreferenceAnswerStatus(p.answerStatus())))
                                .toList());

        // 🔴 S15P21E201-709 — spendProfile 은 별도 칸으로 받지만, 여기서부터는 다른 여덟
        //    차원과 똑같은 PreferenceAnswer 로 합쳐서 계정 기본값 겹치기 등 나머지 흐름을
        //    그대로 탄다. dimension 이 고정값이라 PreferenceDimensions.normalize 를 거칠
        //    필요가 없다 — 그 차원은 이미 CHECK 어휘 그대로다.
        if (r.spendProfile() != null) {
            preferences.add(new PreferenceSnapshot.PreferenceAnswer(
                    "SPEND_PROFILE", r.spendProfile().value(),
                    parsePreferenceAnswerStatus(r.spendProfile().answerStatus())));
        }

        return new TripCreationService.Command(
                userId, r.startDate(), r.finishDate(),
                r.originLat(), r.originLng(),
                r.budgetKrw(), r.partySize(),
                r.timeWindow(), r.timezone(),
                preferences,
                constraints,
                ownerType,
                r.accommodationPlaceId(),
                r.englishMenuRequiredOrDefault(),
                r.foreignCardRequiredOrDefault(),
                r.soloFriendlyPriorityOrDefault(),
                r.maxTransitTransfers(),
                r.mustVisitPlaceIdsOrEmpty());
    }

    private static TripConstraint.Severity parseSeverity(String raw) {
        try {
            return TripConstraint.Severity.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("severity 는 HARD 또는 SOFT 여야 한다: " + raw);
        }
    }

    private static TripConstraint.AnswerStatus parseConstraintAnswerStatus(String raw) {
        try {
            return TripConstraint.AnswerStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "제약의 answerStatus 는 SELECTED · NONE · UNKNOWN 중 하나여야 한다: " + raw);
        }
    }

    private static PreferenceSnapshot.AnswerStatus parsePreferenceAnswerStatus(String raw) {
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
    private static TripConstraint.DietRequirement parseDietRequirement(String raw) {
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
