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
import com.gabolle.backend.trip.domain.TravelArea;
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
     * 날짜별 구간 — 연속한 두 항목 사이. 각 날의 첫 구간은 {@link #dayStart} 에서 출발한다 —
     * 첫날은 여행 출발지, 둘째 날부터는 숙소(있으면).
     */
    public List<ItineraryDraft.DraftLeg> buildLegs(Trip trip, List<List<UUID>> placeIdsByDay) {
        // 여행이 고른 이동수단의 첫 값을 쓴다. 아직 안 고른 여행이면 WALK 로 떨어진다 —
        // 지어낸 값이 아니라 "정보가 없을 때의 기본값" 이고, 그 선택이 걷기 거리 계산에
        // 그대로 이어진다.
        String[] modes = trip.travelModes();
        String travelMode = (modes == null || modes.length == 0) ? "WALK" : modes[0];

        Map<UUID, Place> placesById = lookupPlaces(placeIdsByDay);
        Anchor lodging = lodgingOf(trip);

        List<ItineraryDraft.DraftLeg> legs = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < placeIdsByDay.size(); dayIndex++) {
            List<UUID> dayPlaceIds = placeIdsByDay.get(dayIndex);

            for (int i = 0; i < dayPlaceIds.size(); i++) {
                UUID toPlaceId = dayPlaceIds.get(i);
                UUID fromPlaceId = (i == 0) ? null : dayPlaceIds.get(i - 1);

                Double fromLat;
                Double fromLng;
                if (fromPlaceId == null) {
                    Double[] start = dayStart(trip, dayIndex, lodging);
                    fromLat = start[0];
                    fromLng = start[1];
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

    /**
     * 하루를 열거나 닫는 자리 — 숙소 또는 여행 출발지. 방문지가 아니다.
     *
     * @param kind  {@code LODGING}(숙소) · {@code ORIGIN}(여행 출발지)
     * @param label 화면에 적을 이름. 우리 표의 숙소면 그 이름, 동네 숙소면 동네 이름, 출발지면 {@code null}
     */
    public record Anchor(double lat, double lng, String kind, String label) {

        public static final String LODGING = "LODGING";

        public static final String ORIGIN = "ORIGIN";
    }

    /**
     * 그날 일정이 시작하는 자리 — {@code [위도, 경도]}.
     *
     * <p>🔴 <b>둘째 날부터는 숙소에서 나선다</b> (2026-09-23, S15P21E201-1547). 전에는 매일 여행
     * 출발지(역·집)에서 시작했다. 사람은 숙소에서 자고 나오는데 일정은 매일 부산역에서 출발하는
     * 것처럼 이동 시간을 쟀고, 숙소를 입력해도 일정이 한 줄도 안 바뀌었다(숙소는 저장만 됐다).
     * 첫날은 그대로 출발지다 — 짐을 들고 도착하는 날이다.
     *
     * <p>숙소를 모르면 출발지로 둔다. 모르는 자리를 지어내지 않는다. 출발지도 모르는 옛 여행이면
     * 둘 다 {@code null} 이다(전과 같다).
     */
    public Double[] dayStart(Trip trip, int dayIndex, Anchor lodging) {
        Anchor start = startAnchor(trip, dayIndex, lodging);
        return (start == null) ? new Double[] { null, null } : new Double[] { start.lat(), start.lng() };
    }

    /**
     * {@link #dayStart} 의 자리를 이름·종류까지 담아 낸다 — 일정 응답의 「그날 어디서 출발하나」
     * (S15P21E201-1581). 규칙은 여기 한 곳이다. 첫날이거나 숙소를 모르면 출발지, 둘째 날부터 숙소.
     * 출발지도 모르면 {@code null}.
     */
    public Anchor startAnchor(Trip trip, int dayIndex, Anchor lodging) {
        if (dayIndex > 0 && lodging != null) {
            return lodging;
        }
        if (trip.originLat() == null || trip.originLng() == null) {
            return null;
        }
        return new Anchor(trip.originLat(), trip.originLng(), Anchor.ORIGIN, null);
    }

    /**
     * 그날 일정이 끝나고 돌아가는 자리 (S15P21E201-1565).
     *
     * <p>마지막 날이 아니면 <b>숙소</b>다 — 자러 간다. 마지막 날이면 <b>여행 출발지</b>다 — 역·공항·집으로
     * 돌아간다. 숙소를 모르는 밤, 출발지를 모르는 마지막 날은 {@code null} 이다. 모르는 자리로 돌아가라고
     * 지어내지 않는다.
     */
    public Anchor dayEnd(Trip trip, int dayIndex, Anchor lodging) {
        boolean lastDay = dayIndex >= trip.days() - 1;
        if (!lastDay) {
            return lodging;
        }
        if (trip.originLat() == null || trip.originLng() == null) {
            return null;
        }
        return new Anchor(trip.originLat(), trip.originLng(), Anchor.ORIGIN, null);
    }

    /**
     * 그날 마지막 방문지에서 {@link #dayEnd} 까지 가는 데 드는 이동 — 없으면 {@code null}.
     *
     * <p>🔴 저장하는 구간({@code itinerary_leg})에는 넣지 않는다. 그 표는 도착지가 방문지(장소 외래키)여야
     * 하고, 숙소 동네·출발지는 장소가 아니다. 대신 시간표를 깔 때 이 시간을 먼저 떼어 두고(마지막 방문지가
     * 그만큼 일찍 끝난다), 일정을 읽을 때 같은 규칙으로 다시 재서 화면에 싣는다.
     */
    public DayReturn returnFor(Trip trip, int dayIndex, Anchor lodging, UUID lastPlaceId) {
        Anchor end = dayEnd(trip, dayIndex, lodging);
        if (end == null || lastPlaceId == null) {
            return null;
        }
        Place last = this.placeRepository.findById(lastPlaceId).orElse(null);
        if (last == null || !last.hasCoordinates()) {
            return null;
        }
        String[] modes = trip.travelModes();
        String travelMode = (modes == null || modes.length == 0) ? "WALK" : modes[0];
        TravelTime measured = measure(last.getLat(), last.getLng(), end.lat(), end.lng(), travelMode);
        return new DayReturn(end, measured);
    }

    /** 하루 끝의 돌아가는 이동 — 어디로({@code to}) · 얼마나({@code travel}). */
    public record DayReturn(Anchor to, TravelTime travel) {
    }

    /**
     * 여행의 숙소 — 우리 표의 장소, 아니면 고른 동네의 중심. 둘 다 없으면 {@code null}.
     * 날마다 부르지 않게 부르는 쪽이 한 번 받아 둔다.
     *
     * <p>🔴 <b>동네도 숙소다</b> (S15P21E201-1565). 앱은 홈에서 「해운대」처럼 동네를 숙소로 고르게 하고, 서버는
     * 그 값을 {@code accommodation_area} 에 저장만 하고 아무도 안 읽었다 — 운영의 최근 여행 20개 중 숙소가
     * 쓰인 여행이 0개였다. 동네 중심 좌표는 {@link TravelArea} 가 가진 것을 그대로 쓴다(여행 범위와 같은 점).
     */
    public Anchor lodgingOf(Trip trip) {
        String id = trip.accommodationPlaceId();
        if (id != null && !id.isBlank()) {
            try {
                Place place = this.placeRepository.findById(UUID.fromString(id)).orElse(null);
                if (place != null && place.hasCoordinates()) {
                    return new Anchor(place.getLat(), place.getLng(), Anchor.LODGING, place.getNameKo());
                }
            }
            catch (IllegalArgumentException malformed) {
                // 아래 동네로 넘어간다
            }
        }
        return TravelArea.of(trip.accommodationArea())
                .map(area -> new Anchor(area.lat(), area.lng(), Anchor.LODGING, area.koreanName()))
                .orElse(null);
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
