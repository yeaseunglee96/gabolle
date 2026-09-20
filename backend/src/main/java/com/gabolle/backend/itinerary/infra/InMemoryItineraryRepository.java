package com.gabolle.backend.itinerary.infra;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryExclusion;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * DB 없이 도는 일정 저장소 — {@code no-db} 프로필과 도메인 단위 테스트가 쓴다.
 * 이 구현은 DB 구현과 같은 계약을 지켜야 한다. 여기서만 통과하는 코드는 실제 배포에서 깨진다.
 * 그래서 DB 가 제약으로 막는 것을 흉내낸다 — 판 번호 중복({@code uq_itinerary_version}),
 * 같은 판 안의 자리 중복({@code uq_itinerary_item_slot}), 그리고 최신 판 포인터 이동.
 * 포인터를 저장소가 옮기지 않으면 두 구현이 서로 다른 계약 위에서 돌게 된다 — 인메모리에서는
 * 응용 계층이 같은 객체를 직접 고쳐 "저절로" 반영되는 것처럼 보이지만, JPA 는 조회할 때마다
 * 새 도메인 객체를 만들어 그 트릭이 안 통한다.
 */
@Repository
@Profile("!db & !dev")
public class InMemoryItineraryRepository implements ItineraryRepository {

    private final Map<String, Itinerary> itineraries = new ConcurrentHashMap<>();

    private final Map<String, ItineraryVersion> versions = new ConcurrentHashMap<>();

    private final Map<String, List<ItineraryItem>> items = new ConcurrentHashMap<>();
    private final Map<String, List<ItineraryLeg>> legs = new ConcurrentHashMap<>();
    private final Map<String, List<ItineraryExclusion>> exclusions = new ConcurrentHashMap<>();

    @Override
    public Optional<Itinerary> findById(String itineraryId) {
        return Optional.ofNullable(itineraries.get(itineraryId));
    }

    @Override
    public Itinerary create(Itinerary itinerary, ItineraryVersion firstVersion,
                            List<ItineraryItem> newItems, List<ItineraryLeg> newLegs) {
        // DB 구현과 같은 보장 — 같은 itineraryId 로 두 번 create 하면 뒤엣것이 이긴다.
        //    실제로는 새 UUID 를 매번 만들어 부르므로 이 경로에서 충돌은 생기지 않는다.
        itineraries.put(itinerary.itineraryId(), itinerary);
        versions.put(key(firstVersion.itineraryId(), firstVersion.version()), firstVersion);
        putContent(firstVersion.itineraryVersionId(), newItems, newLegs, List.of());
        return itinerary;
    }

    @Override
    public Optional<ItineraryContent> findContent(String itineraryId, int version) {
        return findVersion(itineraryId, version)
                .map(v -> new ItineraryContent(v,
                        items.getOrDefault(v.itineraryVersionId(), List.of()),
                        legs.getOrDefault(v.itineraryVersionId(), List.of()),
                        exclusions.getOrDefault(v.itineraryVersionId(), List.of())));
    }

    @Override
    public ItineraryVersion appendVersion(ItineraryVersion version, List<ItineraryItem> newItems,
                                          List<ItineraryLeg> newLegs, List<ItineraryExclusion> newExclusions) {
        String key = key(version.itineraryId(), version.version());

        // putIfAbsent 는 "없을 때만 넣는다" 를 원자적으로 한다. DB 의 UNIQUE 제약과 같은 보장이다.
        //    이것이 마지막 방어선이다 — 응용 계층의 사전 확인만으로는 확인과 저장 사이에 다른
        //    요청이 끼어들 수 있다.
        ItineraryVersion existing = versions.putIfAbsent(key, version);
        if (existing != null) {
            throw stale(version);
        }

        putContent(version.itineraryVersionId(), newItems, newLegs, newExclusions);

        // 포인터를 여기서 옮긴다. DB 구현의 조건부 UPDATE 와 짝이 되는 자리다.
        //    Itinerary.moveTo 가 "한 칸씩만" 을 검사하므로 같은 불변식이 여기서도 선다.
        Itinerary itinerary = itineraries.get(version.itineraryId());
        if (itinerary != null) {
            synchronized (itinerary) {
                if (itinerary.latestVersion() != version.version() - 1) {
                    // 판 번호는 땄는데 그 사이 포인터가 움직였다 — DB 구현의 0행 반영과 같다.
                    throw stale(version);
                }
                itinerary.moveTo(version.version());
            }
        }

        return version;
    }

    @Override
    public Optional<ItineraryVersion> findVersion(String itineraryId, int version) {
        return Optional.ofNullable(versions.get(key(itineraryId, version)));
    }

    /**
     * 최신 판이 먼저(version DESC). DB 구현과 같은 계약이다.
     */
    @Override
    public VersionPage findVersions(String itineraryId, int page, int size) {
        List<ItineraryVersion> all = versions.values().stream()
                .filter(v -> v.itineraryId().equals(itineraryId))
                .sorted((a, b) -> Integer.compare(b.version(), a.version()))
                .toList();

        // long 으로 곱한다. page·size 는 요청에서 그대로 들어오는 값이라, int 로 곱하면 큰 page 에서
        //    값이 넘쳐 음수가 되고 subList 가 예외를 던져 500 이 된다.
        int from = (int) Math.min((long) page * size, all.size());
        int to = (int) Math.min((long) from + size, all.size());
        return new VersionPage(all.subList(from, to), to < all.size());
    }

    @Override
    public List<Itinerary> findByTripId(String tripId) {
        return itineraries.values().stream()
                .filter(i -> i.tripId().equals(tripId))
                .toList();
    }

    @Override
    public List<ItineraryVersion> findRecentVersions(Collection<String> itineraryIds, int limit) {
        return versions.values().stream()
                .filter(v -> itineraryIds.contains(v.itineraryId()))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .limit(Math.max(limit, 0))
                .toList();
    }

    /** 시연·테스트용 — 일정 하나를 심어 둔다. */
    public Itinerary seed(String itineraryId, String tripId, int latestVersion) {
        Itinerary it = new Itinerary(itineraryId, tripId, latestVersion);
        itineraries.put(itineraryId, it);
        return it;
    }

    /**
     * 시연·테스트용 — 판 하나와 그 내용(제외 목록은 빈 목록)을 검사 없이 그대로 넣는다.
     * {@link #appendVersion} 과 달리 판 번호 경쟁도 포인터도 건드리지 않는다. {@link #seed} 로
     * 만든 "이미 5번 판까지 와 있는 일정" 에 그 5번 판의 내용을 채워 넣는 용도다.
     * 제외 목록도 심어야 하는 테스트는 4-인자 오버로드를 쓴다. 이 3-인자는 제외 목록을 모르는
     * 기존 호출부가 컴파일이 안 깨지도록 남겨 뒀다.
     */
    public void seedVersion(ItineraryVersion version, List<ItineraryItem> newItems, List<ItineraryLeg> newLegs) {
        seedVersion(version, newItems, newLegs, List.of());
    }

    /** {@link #seedVersion(ItineraryVersion, List, List)} 에 제외 목록을 더한 것. */
    public void seedVersion(ItineraryVersion version, List<ItineraryItem> newItems, List<ItineraryLeg> newLegs,
            List<ItineraryExclusion> newExclusions) {
        versions.put(key(version.itineraryId(), version.version()), version);
        putContent(version.itineraryVersionId(), newItems, newLegs, newExclusions);
    }

    private void putContent(String itineraryVersionId, List<ItineraryItem> newItems, List<ItineraryLeg> newLegs,
            List<ItineraryExclusion> newExclusions) {
        assertNoDuplicateSlot(newItems, newLegs);
        items.put(itineraryVersionId, List.copyOf(newItems));
        legs.put(itineraryVersionId, List.copyOf(newLegs));
        exclusions.put(itineraryVersionId, List.copyOf(newExclusions));
    }

    /**
     * {@code uq_itinerary_item_slot}·{@code uq_itinerary_leg_slot} 을 흉내낸다.
     * 재계산이 항목 순번을 다시 매길 때 같은 자리를 두 번 쓰는 버그가 나면, 이 검사가 없으면
     * 인메모리에서는 통과하고 DB 에서만 터진다.
     */
    private static void assertNoDuplicateSlot(List<ItineraryItem> newItems, List<ItineraryLeg> newLegs) {
        Set<String> itemSlots = new HashSet<>();
        for (ItineraryItem item : newItems) {
            if (!itemSlots.add(item.dayIndex() + "#" + item.sequence())) {
                throw new IllegalArgumentException(
                        "같은 판에 같은 자리가 두 번 있습니다: dayIndex=" + item.dayIndex()
                                + ", sequence=" + item.sequence());
            }
        }
        Set<String> itemKeys = new HashSet<>();
        for (ItineraryItem item : newItems) {
            if (!itemKeys.add(item.itemKey())) {
                throw new IllegalArgumentException("같은 판에 같은 itemKey 가 두 번 있습니다: " + item.itemKey());
            }
        }
        Set<String> legSlots = new HashSet<>();
        for (ItineraryLeg leg : newLegs) {
            if (!legSlots.add(leg.dayIndex() + "#" + leg.sequence())) {
                throw new IllegalArgumentException(
                        "같은 판에 같은 구간 자리가 두 번 있습니다: dayIndex=" + leg.dayIndex()
                                + ", sequence=" + leg.sequence());
            }
        }
    }

    private StaleItineraryVersionException stale(ItineraryVersion version) {
        Itinerary it = itineraries.get(version.itineraryId());
        int latest = it != null ? it.latestVersion() : version.version();
        int attempted = version.baseVersion() != null ? version.baseVersion() : latest;
        return new StaleItineraryVersionException(version.itineraryId(), attempted, latest);
    }

    private static String key(String itineraryId, int version) {
        return itineraryId + "#" + version;
    }
}
