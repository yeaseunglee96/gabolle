package com.gabolle.backend.itinerary.infra;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 메모리 저장소 — {@code no-db} 전용.
 *
 * <p>🔴 S15P21E201-313 이 {@code itineraries}·{@code itinerary_versions} 를
 * {@link JpaItineraryRepository} 로 옮기면서 {@code db}·{@code dev} 프로필에는 이
 * 빈을 만들지 않는다 — trip 패키지의 {@code InMemoryTripRepository} 와 같은 이유다.
 * 둘 다 살아 있으면 {@code ItineraryRepository} 빈이 둘이 되어 애플리케이션이 못 뜬다.
 *
 * <p>서버를 끄면 사라진다. {@code no-db} 프로필 전용이다.
 */
@Repository
@Profile("!db & !dev")
public class InMemoryItineraryRepository implements ItineraryRepository {

    private final Map<String, Itinerary> itineraries = new ConcurrentHashMap<>();

    /** 열쇠는 "일정id#판번호". DB 의 UNIQUE (itinerary_id, version) 에 해당한다. */
    private final Map<String, ItineraryVersion> versions = new ConcurrentHashMap<>();

    /** 열쇠는 itineraryVersionId. 판마다 항목·구간을 따로 들고 있다(판은 덮어쓰지 않는다). */
    private final Map<String, List<ItineraryItem>> items = new ConcurrentHashMap<>();
    private final Map<String, List<ItineraryLeg>> legs = new ConcurrentHashMap<>();

    @Override
    public Optional<Itinerary> findById(String itineraryId) {
        return Optional.ofNullable(itineraries.get(itineraryId));
    }

    @Override
    public Itinerary create(Itinerary itinerary, ItineraryVersion firstVersion) {
        // 🔴 DB 구현과 같은 보장 — 같은 itineraryId 로 두 번 create 하면 뒤엣것이 이긴다.
        //    실제로는 새 UUID 를 매번 만들어 부르므로 이 경로에서 충돌은 생기지 않는다.
        itineraries.put(itinerary.itineraryId(), itinerary);
        versions.put(key(firstVersion.itineraryId(), firstVersion.version()), firstVersion);
        return itinerary;
    }

    @Override
    public void saveContent(String itineraryVersionId, List<ItineraryItem> newItems, List<ItineraryLeg> newLegs) {
        items.put(itineraryVersionId, List.copyOf(newItems));
        legs.put(itineraryVersionId, List.copyOf(newLegs));
    }

    @Override
    public Optional<ItineraryContent> findContent(String itineraryId, int version) {
        return findVersion(itineraryId, version)
                .map(v -> new ItineraryContent(v,
                        items.getOrDefault(v.itineraryVersionId(), List.of()),
                        legs.getOrDefault(v.itineraryVersionId(), List.of())));
    }

    @Override
    public ItineraryVersion append(ItineraryVersion version) {
        String key = key(version.itineraryId(), version.version());

        // 🔴 putIfAbsent 는 "없을 때만 넣는다" 를 원자적으로 한다.
        //    DB 의 UNIQUE 제약과 같은 보장이다 — 두 요청이 동시에 와도 하나만 성공한다.
        //    이것이 마지막 방어선이다. 응용 계층의 사전 확인만으로는
        //    확인과 저장 사이에 다른 요청이 끼어들 수 있다(경쟁 조건).
        ItineraryVersion existing = versions.putIfAbsent(key, version);
        if (existing != null) {
            Itinerary it = itineraries.get(version.itineraryId());
            int latest = it != null ? it.latestVersion() : version.version();
            throw new StaleItineraryVersionException(
                    version.itineraryId(), version.baseVersion(), latest);
        }
        return version;
    }

    @Override
    public Optional<ItineraryVersion> findVersion(String itineraryId, int version) {
        return Optional.ofNullable(versions.get(key(itineraryId, version)));
    }

    /** 시연·테스트용 — 일정 하나를 심어 둔다. */
    public Itinerary seed(String itineraryId, String tripId, int latestVersion) {
        Itinerary it = new Itinerary(itineraryId, tripId, latestVersion);
        itineraries.put(itineraryId, it);
        return it;
    }

    private static String key(String itineraryId, int version) {
        return itineraryId + "#" + version;
    }
}
