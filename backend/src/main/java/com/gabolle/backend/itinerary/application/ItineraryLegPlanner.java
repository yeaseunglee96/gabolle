package com.gabolle.backend.itinerary.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.application.port.TravelTimePort;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.GeoDistance;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.trip.domain.Trip;

/**
 * 일정의 구간(leg)을 계산한다.
 * 생성과 편집(순서 바꾸기) 두 경로가 같은 규칙으로 구간을 만들어야 하는데, 각자 사본을 갖고
 * 있으면 그 사본이 갈라진다.
 */
@Component
@Profile({ "db", "dev" })
public class ItineraryLegPlanner {

    private final PlaceRepository placeRepository;

    /**
     * 구간의 이동시간을 실제로 물어보는 문. {@code ObjectProvider} 로 받아 없어도 뜨게 한다 —
     * 이 클래스는 경로 계층을 안 스캔하는 슬라이스 컨텍스트에서도 만들어지고, 필수 의존성으로
     * 두면 그 컨텍스트가 통째로 안 뜬다. 없으면 직선거리로만 채우고 그 사실을 구간에 적는다.
     */
    private final ObjectProvider<TravelTimePort> travelTime;

    public ItineraryLegPlanner(PlaceRepository placeRepository, ObjectProvider<TravelTimePort> travelTime) {
        this.placeRepository = placeRepository;
        this.travelTime = travelTime;
    }

    /**
     * 날짜별 구간 — 연속한 두 항목 사이. 각 날의 첫 구간은 여행 출발지에서 출발한다
     * ({@code Trip.originLat/Lng} — "매일 여기서 일정이 시작된다").
     */
    public List<ItineraryDraft.DraftLeg> buildLegs(Trip trip, List<List<UUID>> placeIdsByDay) {
        // 여행이 고른 이동수단의 첫 값을 쓴다. 아직 안 고른 여행이면 WALK 로 떨어진다 —
        // 지어낸 값이 아니라 "정보가 없을 때의 기본값" 이고, 그 선택이 걷기 거리 계산에
        // 그대로 이어진다.
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

                // 실제 경로를 물어본다. 못 받으면 그쪽이 직선거리로 어림잡아 돌려주고 그 사실을
                //    함께 알려 준다. 여기서 예외를 잡을 일이 없다 — 그 문은 실패를 예외로 알리지 않는다.
                TravelTime measured = measure(fromLat, fromLng, toLat, toLng, travelMode);

                Integer distanceM = measured.distanceM();
                if (distanceM == null && fromLat != null && fromLng != null && toLat != null && toLng != null) {
                    // 경로 계층이 아예 없는 컨텍스트다. 예전처럼 직선거리라도 적는다.
                    distanceM = (int) Math.round(GeoDistance.meters(fromLat, fromLng, toLat, toLng));
                }
                Integer walkingMeters = walkingMetersFor(travelMode, distanceM);

                legs.add(new ItineraryDraft.DraftLeg(dayIndex, i + 1,
                        fromPlaceId, toPlaceId, travelMode, distanceM, measured.durationMin(),
                        walkingMeters, measured.dataStatus(), measured.fareKrw(),
                        // 선형은 실제 길찾기 응답을 받았을 때만 들어온다. 위에서 직선거리로
                        // 메운 경우에는 null 이고, 그 구분이 지도에서 실선과 점선을 가른다.
                        measured.path()));
            }
        }
        return legs;
    }

    /**
     * 하루치 구간만 다시 만든다.
     * {@link #buildLegs} 를 그대로 쓴다 — 다른 날은 빈 목록으로 채워 넘기므로 구간이 하나도 안
     * 나오고, 돌아오는 것은 {@code dayIndex} 하루치뿐이다. 계산 규칙을 여기서 다시 쓰지 않는
     * 것이 요점이다.
     * 돌려주는 것을 바로 저장할 수 있게 {@link ItineraryLeg} 로 바꿔서 준다. 그 변환이 생성
     * 경로와 편집 경로 두 곳에 흩어지면 언젠가 한쪽만 고쳐진다.
     *
     * @param dayPlaceIds 그날 방문지를 바뀐 순서 그대로. 첫 방문지의 출발지는 여행의 출발
     *                    좌표다 — 생성 경로와 같은 규칙이다
     */
    public List<ItineraryLeg> legsForDay(Trip trip, int dayIndex, List<UUID> dayPlaceIds,
            String itineraryVersionId, Instant now) {

        List<List<UUID>> byDay = new ArrayList<>(dayIndex + 1);
        for (int i = 0; i < dayIndex; i++) {
            byDay.add(List.of());
        }
        byDay.add(dayPlaceIds);

        List<ItineraryDraft.DraftLeg> planned = buildLegs(trip, byDay);

        List<ItineraryLeg> legs = new ArrayList<>(planned.size());
        for (ItineraryDraft.DraftLeg leg : planned) {
            legs.add(toLeg(leg, itineraryVersionId, now));
        }
        return legs;
    }

    /**
     * 계획한 구간 하나를 저장할 모양으로 바꾼다.
     * 오르막과 계단 수는 아직 아무도 채우지 않아 비워 둔다. 지어낸 값을 넣으면 화면이 그것을
     * 잰 값처럼 보여준다.
     */
    public static ItineraryLeg toLeg(ItineraryDraft.DraftLeg leg, String itineraryVersionId, Instant now) {
        return new ItineraryLeg(
                UUID.randomUUID().toString(), itineraryVersionId, leg.dayIndex(), leg.sequence(),
                leg.fromPlaceId() != null ? leg.fromPlaceId().toString() : null,
                leg.toPlaceId().toString(), leg.travelMode(), leg.distanceM(),
                leg.durationMin(), leg.walkingMeters(), null, null,
                leg.dataStatus(), leg.fareKrw(), leg.path(), now);
    }

    /**
     * 구간 하나의 실제 이동 거리·시간. 경로 계층이 없는 컨텍스트에서는 잴 수 없음으로 답한다.
     * 여기서 예외를 삼키지 않는다. 포트가 실패를 예외로 알리지 않기로 약속했고, 그 약속이 깨지면
     * 조용히 넘기는 대신 시끄럽게 실패하는 편이 낫다 — 조용히 넘기면 모든 구간이 이유 없이 비어
     * 나가고 아무도 이유를 못 찾는다.
     */
    private TravelTime measure(Double fromLat, Double fromLng, Double toLat, Double toLng, String travelMode) {
        TravelTimePort port = this.travelTime.getIfAvailable();
        if (port == null) {
            return TravelTime.unknown();
        }
        return port.between(fromLat, fromLng, toLat, toLng, travelMode);
    }

    /**
     * 대중교통 구간에 직선거리를 "걸은 거리" 로 적지 않는다 — 모드가 {@code WALK} 일 때만 채운다.
     * 지하철 구간에 직선거리를 넣으면 "지하철로 이만큼 걸었다" 처럼 읽혀 틀린 답이 된다.
     * 지금은 {@link #buildLegs} 가 {@code travelMode} 로 항상 {@code "WALK"} 만 넘겨서 이 규칙의
     * 대중교통 갈래는 실제 호출 경로로는 확인할 수 없다. 테스트가 이 메서드를 직접 불러 그 갈래를
     * 확인하므로 {@code public} 이어야 한다.
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
}
