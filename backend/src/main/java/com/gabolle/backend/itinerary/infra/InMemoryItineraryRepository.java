package com.gabolle.backend.itinerary.infra;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * 메모리 저장소 — M1 용. PostgreSQL 없이 409 동작을 재현할 수 있게 한다.
 *
 * <p>왜 이걸 먼저 만드나: 지금 {@code application.properties} 가 DataSource·JPA·Flyway 를
 * 전부 꺼 두었고(뼈대 상태), M1 완료 조건 ② 는 <b>"409 충돌이 실제로 재현된다"</b> 다.
 * DB 를 붙이기 전에 규칙이 도는지 보여줄 수 있어야 한다.
 *
 * <p>🔴 이것이 4계층으로 나눈 값이다. {@code domain} 은 이 클래스의 존재를 모르고,
 * 나중에 JPA 구현으로 갈아 끼울 때 {@code domain}·{@code application} 은 한 줄도 안 고친다.
 *
 * <p>서버를 끄면 사라진다. 시연·테스트 전용이다.
 */
@Repository
public class InMemoryItineraryRepository implements ItineraryRepository {

    private final Map<String, Itinerary> itineraries = new ConcurrentHashMap<>();

    /** 열쇠는 "일정id#판번호". DB 의 UNIQUE (itinerary_id, version) 에 해당한다. */
    private final Map<String, ItineraryVersion> versions = new ConcurrentHashMap<>();

    @Override
    public Optional<Itinerary> findById(String itineraryId) {
        return Optional.ofNullable(itineraries.get(itineraryId));
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
