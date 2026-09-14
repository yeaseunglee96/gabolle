package com.gabolle.backend.itinerary.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.PaceFactor;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryPaceResponse;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;

/**
 * 하루치 지연 경고 조회 — S15P21E201-304 · -96.
 *
 * <p>{@link PaceFactorService} 가 관리하는 속도 계수와 {@link ItineraryDelayProjector} 가 하는
 * "그래서 몇 시에 도착하나" 계산을 묶어서 화면이 바로 그릴 수 있는 모양으로 낸다. 계산 자체는
 * 두 클래스가 이미 하므로 이 서비스는 권한 확인과 두 클래스를 잇는 것, 그리고 응답을 만드는
 * 것만 한다.
 *
 * <p>조회할 때마다 {@link PaceFactorService#recompute} 를 다시 부른다. 방문 기록이 그 사이에
 * 늘었을 수 있고, 이 경로는 "지금 기준으로" 지연을 답해야 하기 때문이다 — 어제 계산해 둔
 * 계수를 그대로 읽으면 오늘 새로 적은 도착·출발 기록이 반영되지 않는다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryPaceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final ItineraryAccess itineraryAccess;

    private final ItineraryRepository itineraryRepository;

    private final ItineraryItemActualRepository actualRepository;

    private final PaceFactorService paceFactorService;

    public ItineraryPaceService(ItineraryAccess itineraryAccess, ItineraryRepository itineraryRepository,
            ItineraryItemActualRepository actualRepository, PaceFactorService paceFactorService) {
        this.itineraryAccess = itineraryAccess;
        this.itineraryRepository = itineraryRepository;
        this.actualRepository = actualRepository;
        this.paceFactorService = paceFactorService;
    }

    /**
     * 그 하루의 예상 시각표와 지연 위험.
     *
     * @throws ItineraryQueryController.ItineraryNotFoundException 일정이 없거나 요청자가 그
     *     일정이 속한 여행의 회원이 아니다 — 조회이므로 VIEWER 도 통과한다
     * @throws DayOutsideTripException 여행 기간 밖의 날짜다
     * @throws IllegalStateException 최신 판의 내용이 없다. {@link ItineraryQueryService#getDetail}
     *     과 같은 이유로 조용히 대체하지 않고 500 으로 실패한다
     */
    @Transactional
    public ItineraryPaceResponse pace(String itineraryId, int dayIndex, String requesterUserId) {
        ItineraryAccess.Access access = this.itineraryAccess.requireMember(itineraryId, requesterUserId);
        Itinerary itinerary = access.itinerary();
        requireDayInsideTrip(access.trip(), dayIndex);

        int latestVersion = itinerary.latestVersion();
        ItineraryContent content = this.itineraryRepository.findContent(itineraryId, latestVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "일정의 최신 판(version=" + latestVersion + ") 내용이 없다: itineraryId=" + itineraryId));

        List<ItineraryItemActual> actuals = this.actualRepository.findByItineraryId(itineraryId);

        Optional<PaceFactor> factorResult = this.paceFactorService.recompute(requesterUserId, content.items(),
                actuals);
        BigDecimal factor = factorResult.map(PaceFactor::factor).orElse(null);
        int sampleCount = factorResult.map(PaceFactor::sampleCount)
                .orElseGet(() -> PaceFactorCalculator.countSamples(content.items(), actuals));

        ItineraryDelayProjector.Projection projection = ItineraryDelayProjector.project(
                content.items(), content.legs(), actuals, dayIndex, factor, Instant.now());

        List<ItineraryPaceResponse.Item> items = projection.entries().stream()
                .map(ItineraryPaceService::toItem)
                .toList();
        List<String> atRiskItemIds = projection.atRisk().stream()
                .map(ItineraryDelayProjector.Entry::itemKey)
                .toList();

        // 계수를 못 구했다는 사실을 빈 목록으로 답하지 않는다. 화면이 "확인했고 문제 없음" 과
        // "볼 수 없었음" 을 구분해야 하기 때문이고, 이것은 영업시간 판정이 NOT_COLLECTED 를
        // 남기는 것과 같은 이유다(ItineraryOpeningHoursChecker).
        List<ItineraryPaceResponse.NotChecked> notChecked = factor == null
                ? List.of(new ItineraryPaceResponse.NotChecked(ItineraryDelayProjector.CHECK,
                        ItineraryDelayProjector.REASON_NOT_ENOUGH_RECORDS))
                : List.of();

        return new ItineraryPaceResponse(itineraryId, dayIndex, factor, sampleCount,
                PaceFactorCalculator.MIN_SAMPLES, seoulIso(projection.plannedDayEnd()), items,
                atRiskItemIds, notChecked);
    }

    private static ItineraryPaceResponse.Item toItem(ItineraryDelayProjector.Entry entry) {
        return new ItineraryPaceResponse.Item(entry.itemKey(), entry.visited(),
                seoulIso(entry.predictedArrival()), seoulIso(entry.predictedDeparture()),
                seoulIso(entry.plannedArrival()), entry.delayMinutes(), entry.overrunsDay());
    }

    /**
     * dayIndex 가 여행 기간 안에 있는가. {@code ItineraryEditController.requireDayInsideTrip} 과
     * 같은 판정이되, 음수 dayIndex 도 함께 막는다 — 여행 시작일보다 앞선 날짜는 존재하지 않는
     * 날이므로 "여행 마지막 날을 넘었다"만큼이나 벗어난 요청이다.
     */
    private static void requireDayInsideTrip(Trip trip, int dayIndex) {
        if (dayIndex < 0) {
            throw new DayOutsideTripException(dayIndex, trip.startDate(), trip.finishDate());
        }
        LocalDate visitDate = trip.startDate().plusDays(dayIndex);
        if (visitDate.isAfter(trip.finishDate())) {
            throw new DayOutsideTripException(dayIndex, trip.startDate(), trip.finishDate());
        }
    }

    /** 계획 시각과 같은 형식(Asia/Seoul, ISO_OFFSET_DATE_TIME)으로 낸다. {@code null} 은 그대로 {@code null}. */
    private static String seoulIso(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** {@code dayIndex} 가 여행 기간을 벗어났다 — 400. */
    public static class DayOutsideTripException extends RuntimeException {

        private final int dayIndex;

        public DayOutsideTripException(int dayIndex, LocalDate start, LocalDate finish) {
            super("여행 기간(" + start + "~" + finish + ") 밖의 날짜입니다: dayIndex=" + dayIndex);
            this.dayIndex = dayIndex;
        }

        public int dayIndex() {
            return this.dayIndex;
        }
    }
}
