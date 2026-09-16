package com.gabolle.backend.itinerary.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 일정 저장소 — <b>인터페이스만</b> 도메인 계층에 둔다.
 *
 * <p>🔴 구현(JPA)은 {@code infra} 계층에 있다. 이 파일은 JPA·Spring 을 import 하지 않는다.
 * 그래서 도메인 규칙을 DB 없이 테스트할 수 있다 — 가짜 구현을 끼우면 된다.
 *
 * <p>의존성 역전: {@code infra} 가 {@code domain} 을 향한다. 그 반대가 아니다.
 *
 * <h2>🔴 2026-09-06 (S15P21E201-662) — 판과 내용을 따로 저장하는 길을 없앴다</h2>
 * 예전에는 {@code append(version)} 과 {@code saveContent(versionId, items, legs)} 가
 * 따로 있었다. 항목·구간의 부모가 {@code itineraryId} 가 아니라
 * {@code itineraryVersionId} 이므로 판을 하나 더할 때마다 내용을 그 판으로 복사해야
 * 하는데, 두 메서드가 갈라져 있으니 <b>판만 만들고 내용을 안 넣는 것이 가능했다.</b>
 * 실제로 고정({@code ItineraryEditService})이 그 상태였고, 그 결과 항목 0개짜리 판이
 * 최신이 되어 조회가 빈 일정을 돌려줬다.
 *
 * <p>그래서 둘을 합쳤다. 고를 수 없게 만드는 것이 기억해서 지키는 것보다 낫다 —
 * 시그니처를 바꾸면 컴파일러가 호출부를 전부 찾아 준다.
 *
 * <h2>🔴 2026-09-06 (S15P21E201-249) — exclusions 도 같은 이유로 appendVersion 안에 넣었다</h2>
 * 제외 목록도 항목·구간과 같은 이유로 판마다 복사돼야 한다({@link ItineraryExclusion}
 * 클래스 javadoc 참고). 저장 경로를 또 하나 따로 두면 "판은 만들었는데 제외 목록은
 * 안 옮긴" 판이 생길 수 있다 — 그래서 3-인자 {@code appendVersion} 을 남기지 않고
 * 4-인자 하나로만 저장한다.
 *
 * <h2>🔴 최신 판 포인터를 옮기는 주체는 <b>구현체</b>다</h2>
 * 예전에는 JPA 구현이 저장소 안에서 옮기고, 인메모리 구현은 안 옮기는 대신
 * {@code ItineraryEditService} 가 뒤이어 부르는 {@code Itinerary.moveTo} 만이 옮겼다.
 * JPA 에서는 그 도메인 객체가 분리된 복사본이라 무해해서 드러나지 않았지만, 두 구현이
 * 서로 다른 계약 위에서 돌고 있었다 — 인메모리에서 통과하는데 DB 에서만 깨지는(또는 그
 * 반대) 버그가 남을 자리다. 지금은 {@link #appendVersion} 과 {@link #create} 가
 * 포인터까지 책임진다. 응용 계층은 {@code moveTo} 를 부르지 않는다.
 */
public interface ItineraryRepository {

    Optional<Itinerary> findById(String itineraryId);

    /**
     * 판 하나와 <b>그 판의 내용</b>을 한 트랜잭션으로 더한다.
     *
     * <p>🔴 같은 {@code (itineraryId, version)} 이 이미 있으면
     * {@link StaleItineraryVersionException} 을 던져야 한다.
     * DB 의 UNIQUE 제약 위반을 그 예외로 바꾸는 것이 구현체의 책임이다.
     *
     * <p>🔴 최신 판 포인터를 {@code baseVersion} 에서만 움직인다. 그 사이 다른 게시가
     * 있었으면 역시 {@link StaleItineraryVersionException} 이다 — 판 번호 UNIQUE 는
     * <b>번호</b>를 지키는 제약이지 <b>포인터</b>를 지키는 제약이 아니라, 둘 다 필요하다.
     *
     * @param items 새 판의 항목 전부. 바뀌지 않은 것도 포함한다 — 판은 덮어쓰지 않는
     *     스냅샷이라 "이전 판을 참고한다" 는 개념이 없다
     * @param legs 새 판의 구간 전부. 같은 이유다
     * @param exclusions 새 판의 제외 목록 전부. 같은 이유다 — S15P21E201-249
     */
    ItineraryVersion appendVersion(ItineraryVersion version,
                                   List<ItineraryItem> items,
                                   List<ItineraryLeg> legs,
                                   List<ItineraryExclusion> exclusions);

    Optional<ItineraryVersion> findVersion(String itineraryId, int version);

    /**
     * 🔴 일정을 처음 만든다. version=1 · operation=CREATE.
     *
     * <p>V20260903150000 마이그레이션 주석이 "그 경로가 생기는 티켓이 최초 값을 명시적으로
     * 넣어야 한다"고 남긴 자리다 — S15P21E201-604 가 그 티켓이다. {@code itinerary}
     * 하나와 그 첫 판과 첫 판의 내용을 함께 만든다({@code itineraries}·
     * {@code itinerary_versions}·{@code itinerary_item}·{@code itinerary_leg} 가
     * 함께 채워져야 "판은 있는데 내용이 없는" 상태가 생기지 않는다).
     *
     * @param firstVersion {@code version() == 1}·{@code operation() == CREATE} 여야 한다
     */
    Itinerary create(Itinerary itinerary, ItineraryVersion firstVersion,
                     List<ItineraryItem> items, List<ItineraryLeg> legs);

    /** 판 하나의 전체 내용(판 + 항목 + 구간 + 제외 목록). 판이 없으면 비어 있다. */
    Optional<ItineraryContent> findContent(String itineraryId, int version);

    /**
     * 🔴 S15P21E201-284 — 한 일정의 판 목록, <b>최신 판이 먼저</b>(version DESC).
     * 되돌리기 화면이 "어느 판으로 돌아갈지" 고르는 목록이다.
     *
     * <p>{@code ix_itinerary_version_itinerary (itinerary_id, version DESC)}
     * (V20260903150000)가 이 정렬을 위해 있는 색인이다.
     *
     * <p>🔴 <b>쪽을 나눠 받는다</b> (S15P21E201-1011). 판은 <b>일정을 고칠 때마다 쌓인다</b> —
     * 끝이 없는 목록이라 상한 없이 전부 돌려주면 오래 쓴 일정일수록 이 조회만 무거워진다.
     * 정렬이 {@code version DESC} 로 완전히 정해져 있어서(같은 일정에 같은 판 번호가 둘일 수
     * 없다) 쪽을 나눠도 같은 판이 두 번 나오거나 조용히 건너뛰어지지 않는다.
     *
     * @param page 0부터
     * @param size 한 쪽에 실을 최대 판 수
     * @return 없는 일정이면 빈 쪽
     */
    VersionPage findVersions(String itineraryId, int page, int size);

    /**
     * 판 목록 한 쪽.
     *
     * @param hasMore 🔴 <b>더 있는데 안 보냈다.</b> 이 칸이 없으면 부르는 쪽이 "상한에 걸린 것"
     *     과 "마침 그만큼 있는 것" 을 구분할 수 없고, 구분 못 하면 화면이 목록을 <b>조용히
     *     자른다</b> — 사용자에게는 되돌릴 수 있던 판이 사라진 것으로 보인다
     */
    record VersionPage(List<ItineraryVersion> versions, boolean hasMore) {
    }

    /**
     * 한 여행의 일정 전부 — S15P21E201-330(공유 조회) · 협업 화면의 최근 변경이 쓴다.
     * 지금은 여행마다 일정이 하나지만 표는 여럿을 허용한다({@code ix_itinerary_trip}).
     */
    List<Itinerary> findByTripId(String tripId);

    /**
     * 여러 일정의 판을 <b>만든 시각이 늦은 것부터</b> {@code limit} 개 — 협업 화면의 "누가 언제
     * 무엇을 바꿨나"(최근 변경). 판 자체가 이력이라 따로 이력 표를 두지 않는다.
     */
    List<ItineraryVersion> findRecentVersions(Collection<String> itineraryIds, int limit);
}
