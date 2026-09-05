package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.GeoDistance;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftPort;
import com.gabolle.backend.recommendation.application.port.ItineraryHandle;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 추천이 순위 매긴 장소를 실제 일정(항목·구간)으로 조립하고 저장한다 — S15P21E201-604.
 *
 * <p>🔴 클래스에 {@code @Transactional} 을 달지 않는다. {@link #persist} 는
 * {@code RecommendationRecorder.recordWithItinerary} 의 트랜잭션 안에서 불려야 하고,
 * 여기서 새 트랜잭션을 열면(또는 프록시가 트랜잭션 없이 부르면) 일정 저장과 Job·후보
 * 저장이 나뉘어 "일정만 생기고 Job 은 실패로 남는" 상태가 생길 수 있다.
 */
@Service
@Profile({ "db", "dev" })
public class ItineraryDraftService implements ItineraryDraftPort {

    private final TripRepository tripRepository;

    private final PlaceRepository placeRepository;

    private final ItineraryRepository itineraryRepository;

    private final Clock clock;

    /** 하루에 배정할 최대 항목 수. 프리셋·설정이 없으면 4 — 이 값 자체가 제품 결정은 아니다. */
    private final int maxItemsPerDay;

    public ItineraryDraftService(TripRepository tripRepository, PlaceRepository placeRepository,
            ItineraryRepository itineraryRepository, Clock clock,
            @Value("${gabolle.itinerary.max-items-per-day:4}") int maxItemsPerDay) {
        this.tripRepository = tripRepository;
        this.placeRepository = placeRepository;
        this.itineraryRepository = itineraryRepository;
        this.clock = clock;
        this.maxItemsPerDay = maxItemsPerDay;
    }

    /**
     * 🔴 순수 계산 + 읽기만 — DB 에 아무것도 쓰지 않는다. {@code RecommendationService
     * .continueJob} 이 트랜잭션 밖에서 부른다.
     */
    @Override
    public ItineraryDraft assemble(ItineraryDraftCommand command) {
        if (command.places().isEmpty()) {
            // 🔴 RecommendationService 가 returnedCount == 0 인 경우를 이미 NO_FEASIBLE_RESULT 로
            //    걸러 준다 — 여기 오면 그 방어선이 뚫린 것이다.
            throw new IllegalStateException("일정으로 조립할 장소가 없다: requestId=" + command.requestId());
        }

        Trip trip = this.tripRepository.findById(command.tripId())
                .orElseThrow(() -> new IllegalStateException("여행을 찾을 수 없다: " + command.tripId()));

        int days = trip.days();
        List<List<ItineraryDraftCommand.PlannedPlace>> byDay = distributeByDay(command.places(), days);

        List<ItineraryDraft.DraftItem> items = new ArrayList<>();
        // 구간을 만들 때 필요한, 날짜별 "그 날 다녀올 장소" 원본 순서.
        List<List<UUID>> placeIdsByDay = new ArrayList<>();

        for (int dayIndex = 0; dayIndex < byDay.size(); dayIndex++) {
            List<ItineraryDraftCommand.PlannedPlace> dayPlaces = byDay.get(dayIndex);
            LocalDate visitDate = trip.startDate().plusDays(dayIndex);
            List<UUID> placeIdsToday = new ArrayList<>(dayPlaces.size());

            for (int i = 0; i < dayPlaces.size(); i++) {
                ItineraryDraftCommand.PlannedPlace place = dayPlaces.get(i);
                placeIdsToday.add(place.placeId());

                // 🔴 시각은 여행이 실제 시각을 들고 있을 때만 배정한다. 프리셋
                //    ("MORNING_TO_EVENING")을 시각으로 바꾸는 규칙은 아직 확정되지 않았고
                //    (V20260904010000 마이그레이션 주석), 없는 규칙을 여기서 지어내면
                //    그 값이 계약이 된다. 없으면 시각을 비우고 UNKNOWN 으로 표시한다.
                Slot slot = slotFor(trip, i, dayPlaces.size());
                items.add(new ItineraryDraft.DraftItem(
                        dayIndex, visitDate, i + 1, place.placeId(),
                        UUID.randomUUID(), slot.start(), slot.end(), slot.stayMinutes(),
                        slot.dataStatus(), place.reasonCodes(), place.warningCodes()));
            }
            placeIdsByDay.add(placeIdsToday);
        }

        List<ItineraryDraft.DraftLeg> legs = buildLegs(trip, placeIdsByDay);

        return new ItineraryDraft(command.tripId(), command.userId(), command.requestId(),
                command.modelVersion(), command.featureVersion(), command.ontologyVersion(),
                command.policyVersion(), command.datasetVersion(), items, legs);
    }

    /**
     * 순위대로 날짜에 배분한다. 하루가 {@link #maxItemsPerDay} 를 채우면 다음 날로 넘긴다 —
     * 남는 후보를 버리지 않는다. 여행 마지막 날까지 다 찬 뒤에는(총 후보가 날짜 수 ×
     * 하루 최대치보다 많을 때) 더 넘길 날이 없으므로 그 이후는 전부 마지막 날에 쌓인다 —
     * 실제로는 topK 가 이 상황을 사실상 막는다.
     */
    private List<List<ItineraryDraftCommand.PlannedPlace>> distributeByDay(
            List<ItineraryDraftCommand.PlannedPlace> places, int days) {

        List<List<ItineraryDraftCommand.PlannedPlace>> byDay = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            byDay.add(new ArrayList<>());
        }

        int day = 0;
        for (ItineraryDraftCommand.PlannedPlace place : places) {
            while (day < days - 1 && byDay.get(day).size() >= this.maxItemsPerDay) {
                day++;
            }
            byDay.get(day).add(place);
        }
        return byDay;
    }

    /**
     * 한 항목이 차지할 시간 칸. 여행이 활동 시간대를 안 정했으면 세 값이 전부 비어 있고
     * {@code dataStatus} 가 {@code UNKNOWN} 이다.
     */
    private record Slot(LocalTime start, LocalTime end, Integer stayMinutes, String dataStatus) {

        static Slot unknown() {
            return new Slot(null, null, null, "UNKNOWN");
        }
    }

    /**
     * 하루의 활동 시간대를 그 날 항목 수로 균등하게 나눈다.
     *
     * <p>🔴 {@code ESTIMATED} 다. {@code VERIFIED} 가 아니다 — 이 시각은 장소의 영업시간이나
     * 실제 이동 소요를 본 것이 아니라 사용자가 정한 활동 시간대를 항목 수로 나눈 것뿐이다.
     * 확인한 사실과 추정을 같은 등급으로 적으면 화면이 둘을 구분해 보여줄 수 없다.
     *
     * <p>칸이 1분도 안 나올 만큼 항목이 많으면 시각을 배정하지 않는다. 시작과 끝이 같은
     * 칸은 {@code ck_itinerary_item_time_order}(끝이 시작보다 뒤여야 한다)에 걸린다.
     */
    private static Slot slotFor(Trip trip, int index, int countToday) {
        LocalTime windowStart = trip.timeWindowStart();
        LocalTime windowEnd = trip.timeWindowEnd();
        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart) || countToday <= 0) {
            return Slot.unknown();
        }
        long windowMinutes = Duration.between(windowStart, windowEnd).toMinutes();
        long slotMinutes = windowMinutes / countToday;
        if (slotMinutes < 1) {
            return Slot.unknown();
        }
        LocalTime start = windowStart.plusMinutes(slotMinutes * index);
        LocalTime end = start.plusMinutes(slotMinutes);
        return new Slot(start, end, (int) slotMinutes, "ESTIMATED");
    }

    /**
     * 날짜별 구간 — 연속한 두 항목 사이. 각 날의 첫 구간은 여행 출발지에서 출발한다
     * ({@code Trip.originLat/Lng} — "매일 여기서 일정이 시작된다").
     */
    private List<ItineraryDraft.DraftLeg> buildLegs(Trip trip, List<List<UUID>> placeIdsByDay) {
        // 여행이 고른 이동수단의 첫 값을 쓴다. 아직 안 고른 여행이면 WALK 로 떨어진다 —
        // 지어낸 값이 아니라 "정보가 없을 때의 기본값" 이고, 그 선택이 걷기 거리 계산에
        // 그대로 이어진다({@link #walkingMetersFor}).
        String[] modes = trip.travelModes();
        String travelMode = (modes == null || modes.length == 0) ? "WALK" : modes[0];

        Map<UUID, Place> placesById = lookupPlaces(placeIdsByDay);

        List<ItineraryDraft.DraftLeg> legs = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < placeIdsByDay.size(); dayIndex++) {
            List<UUID> dayPlaceIds = placeIdsByDay.get(dayIndex);

            for (int i = 0; i < dayPlaceIds.size(); i++) {
                UUID toPlaceId = dayPlaceIds.get(i);
                UUID fromPlaceId = (i == 0) ? null : dayPlaceIds.get(i - 1);

                Double fromLat;
                Double fromLng;
                if (fromPlaceId == null) {
                    fromLat = trip.originLat();
                    fromLng = trip.originLng();
                }
                else {
                    Place from = placesById.get(fromPlaceId);
                    fromLat = (from != null && from.hasCoordinates()) ? from.getLat() : null;
                    fromLng = (from != null && from.hasCoordinates()) ? from.getLng() : null;
                }

                Place to = placesById.get(toPlaceId);
                Double toLat = (to != null && to.hasCoordinates()) ? to.getLat() : null;
                Double toLng = (to != null && to.hasCoordinates()) ? to.getLng() : null;

                Integer distanceM = null;
                if (fromLat != null && fromLng != null && toLat != null && toLng != null) {
                    distanceM = (int) Math.round(GeoDistance.meters(fromLat, fromLng, toLat, toLng));
                }
                Integer walkingMeters = walkingMetersFor(travelMode, distanceM);

                legs.add(new ItineraryDraft.DraftLeg(dayIndex, i + 1,
                        fromPlaceId, toPlaceId, travelMode, distanceM, null, walkingMeters));
            }
        }
        return legs;
    }

    /**
     * 🔴 대중교통 구간에 직선거리를 "걸은 거리"로 적지 않는다 — 모드가 {@code WALK} 일 때만
     * 채운다. 지하철 구간에 직선거리를 넣으면 "지하철로 이만큼 걸었다"처럼 읽혀 틀린 답이
     * 된다.
     *
     * <p>지금은 {@link #buildLegs} 가 {@code travelMode} 로 항상 {@code "WALK"} 만
     * 넘긴다(Trip 도메인이 아직 이동수단을 노출하지 않는다) — 그래서 이 규칙의 대중교통
     * 갈래는 지금 실제 호출 경로로는 확인할 수 없다. {@code ItineraryDraftServiceTest} 가
     * 이 메서드를 직접 불러 그 갈래를 확인한다({@code itinerary} 패키지에 있어 {@code public}
     * 이어야 닿는다).
     */
    public static Integer walkingMetersFor(String travelMode, Integer distanceM) {
        return "WALK".equals(travelMode) ? distanceM : null;
    }

    private Map<UUID, Place> lookupPlaces(List<List<UUID>> placeIdsByDay) {
        List<UUID> all = new ArrayList<>();
        for (List<UUID> dayPlaceIds : placeIdsByDay) {
            all.addAll(dayPlaceIds);
        }
        Map<UUID, Place> byId = new HashMap<>();
        for (Place place : this.placeRepository.findByPlaceIdIn(all)) {
            byId.put(place.getPlaceId(), place);
        }
        return byId;
    }

    /**
     * 🔴 바깥 트랜잭션 안에서 불린다 — {@code RecommendationRecorder.recordWithItinerary}.
     * 여기서 순서를 뒤집지 않는다: 일정(itineraries·itinerary_versions·항목·구간)을 먼저
     * 만들어야, 그 뒤에 Job 에 {@code itinerary_id} 를 붙이는 것(attachItinerary)이
     * 의미가 있다.
     */
    @Override
    public ItineraryHandle persist(ItineraryDraft draft) {
        String itineraryId = UUID.randomUUID().toString();
        String itineraryVersionId = UUID.randomUUID().toString();
        Instant now = this.clock.instant();
        String requestIdString = draft.requestId().toString();

        Itinerary itinerary = new Itinerary(itineraryId, draft.tripId(), 1);
        ItineraryVersion.Versions versions = new ItineraryVersion.Versions(
                draft.modelVersion(), draft.featureVersion(), draft.ontologyVersion(),
                draft.policyVersion(), draft.datasetVersion());
        ItineraryVersion firstVersion = new ItineraryVersion(itineraryVersionId, itineraryId, 1, null,
                ItineraryVersion.Operation.CREATE, draft.userId(), requestIdString, versions, now,
                requestIdString);

        this.itineraryRepository.create(itinerary, firstVersion);

        List<ItineraryItem> items = new ArrayList<>(draft.items().size());
        for (ItineraryDraft.DraftItem draftItem : draft.items()) {
            items.add(new ItineraryItem(
                    UUID.randomUUID().toString(), itineraryVersionId, draftItem.itemKey().toString(),
                    draftItem.dayIndex(), draftItem.visitDate(), draftItem.sequence(),
                    draftItem.placeId().toString(), draftItem.startTime(), draftItem.endTime(),
                    draftItem.stayMinutes(), false, null,
                    ItineraryItem.DataStatus.valueOf(draftItem.dataStatus()),
                    draftItem.reasonCodes(), draftItem.warningCodes(), requestIdString, now));
        }

        List<ItineraryLeg> legs = new ArrayList<>(draft.legs().size());
        for (ItineraryDraft.DraftLeg draftLeg : draft.legs()) {
            legs.add(new ItineraryLeg(
                    UUID.randomUUID().toString(), itineraryVersionId, draftLeg.dayIndex(), draftLeg.sequence(),
                    draftLeg.fromPlaceId() != null ? draftLeg.fromPlaceId().toString() : null,
                    draftLeg.toPlaceId().toString(), draftLeg.travelMode(), draftLeg.distanceM(),
                    draftLeg.durationMin(), draftLeg.walkingMeters(), null, null, now));
        }

        this.itineraryRepository.saveContent(itineraryVersionId, items, legs);

        return new ItineraryHandle(itineraryId, 1);
    }
}
