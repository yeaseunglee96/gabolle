package com.gabolle.backend.itinerary.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryItemActualRepository;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.PaceFactor;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryRhythmResponse;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;

/**
 * 여행 전체의 리듬 요약.
 * 하루 단위인 {@link ItineraryPaceService} 와 달리 여행 전체 판을 한 번에 훑어 하루 평균 항목
 * 수, 시간 중 이동이 차지하는 비율, 계획 대비 실제 세 가지를 낸다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryRhythmService {

    private final ItineraryAccess itineraryAccess;

    private final ItineraryRepository itineraryRepository;

    private final ItineraryItemActualRepository actualRepository;

    private final PaceFactorService paceFactorService;

    public ItineraryRhythmService(ItineraryAccess itineraryAccess, ItineraryRepository itineraryRepository,
            ItineraryItemActualRepository actualRepository, PaceFactorService paceFactorService) {
        this.itineraryAccess = itineraryAccess;
        this.itineraryRepository = itineraryRepository;
        this.actualRepository = actualRepository;
        this.paceFactorService = paceFactorService;
    }

    /**
     * @throws ItineraryQueryController.ItineraryNotFoundException 일정이 없거나 요청자가 그
     *     일정이 속한 여행의 회원이 아니다 — 조회이므로 VIEWER 도 통과한다
     * @throws IllegalStateException 최신 판의 내용이 없다. {@link ItineraryQueryService#getDetail}
     *     과 같은 이유로 조용히 대체하지 않고 500 으로 실패한다
     */
    @Transactional
    public ItineraryRhythmResponse rhythm(String itineraryId, String requesterUserId) {
        ItineraryAccess.Access access = this.itineraryAccess.requireMember(itineraryId, requesterUserId);
        Itinerary itinerary = access.itinerary();
        Trip trip = access.trip();

        int latestVersion = itinerary.latestVersion();
        ItineraryContent content = this.itineraryRepository.findContent(itineraryId, latestVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "일정의 최신 판(version=" + latestVersion + ") 내용이 없다: itineraryId=" + itineraryId));

        List<ItineraryItemActual> actuals = this.actualRepository.findByItineraryId(itineraryId);

        Optional<PaceFactor> factorResult = this.paceFactorService.recompute(requesterUserId, content.items(),
                actuals);
        BigDecimal plannedVsActual = factorResult.map(PaceFactor::factor).orElse(null);
        int sampleCount = factorResult.map(PaceFactor::sampleCount)
                .orElseGet(() -> PaceFactorCalculator.countSamples(content.items(), actuals));

        int dayCount = trip.days();
        double averageItemsPerDay = dayCount <= 0 ? 0.0 : round2(content.items().size() / (double) dayCount);

        Double travelShare = computeTravelShare(content.items(), content.legs());

        List<ItineraryRhythmResponse.NotChecked> notChecked = plannedVsActual == null
                ? List.of(new ItineraryRhythmResponse.NotChecked(ItineraryDelayProjector.CHECK,
                        ItineraryDelayProjector.REASON_NOT_ENOUGH_RECORDS))
                : List.of();

        return new ItineraryRhythmResponse(itineraryId, dayCount, averageItemsPerDay, travelShare, plannedVsActual,
                sampleCount, notChecked);
    }

    /**
     * 이동시간 / (이동시간 + 머문시간).
     * 구간의 {@code durationMin} 이 하나도 없으면(길찾기 응답을 아직 하나도 못 받은 여행) 0.0 이
     * 아니라 {@code null} 이다 — 0 은 "이동이 없었다" 는 관측이고 이 경우는 "이동 시간을 모른다" 는
     * 무지다.
     */
    private static Double computeTravelShare(List<ItineraryItem> items, List<ItineraryLeg> legs) {
        int travelSum = 0;
        boolean anyTravelMeasured = false;
        for (ItineraryLeg leg : legs) {
            if (leg.durationMin() != null) {
                travelSum += leg.durationMin();
                anyTravelMeasured = true;
            }
        }
        if (!anyTravelMeasured) {
            return null;
        }

        int staySum = 0;
        for (ItineraryItem item : items) {
            Integer planned = PaceFactorCalculator.plannedStayMinutes(item);
            if (planned != null && planned > 0) {
                staySum += planned;
            }
        }

        int denominator = travelSum + staySum;
        if (denominator <= 0) {
            // 이동 시간은 쟀는데 그 값이 전부 0이고 머문 시간도 없다 — 나눌 수 없다.
            return null;
        }
        return round2(travelSum / (double) denominator);
    }


    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
