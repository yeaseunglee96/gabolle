package com.gabolle.backend.itinerary.application;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정 편집 — 새 판을 만든다. 기존 판은 절대 고치지 않는다.
 *
 * <h2>🔴 2026-09-06 (S15P21E201-662) — 판만 만들고 내용을 안 옮기던 것을 고쳤다</h2>
 * 예전 {@code edit(...)} 은 {@code itinerary_versions} 행 하나만 더했다. 항목·구간의
 * 부모가 판이라 그 판에는 항목이 하나도 없었고, 조회({@code ItineraryQueryService})는
 * 최신 판을 읽으므로 <b>고정 한 번에 일정이 통째로 비어 보였다.</b> 지금은 바탕 판의
 * 내용을 {@link ItineraryRevision} 으로 복사해 함께 저장한다.
 *
 * <p>포인터 이동({@code Itinerary.moveTo})도 여기서 안 부른다 — 저장소가 판 INSERT 와 같은
 * 트랜잭션에서 조건부로 옮긴다({@link ItineraryRepository} javadoc).
 */
@Service
public class ItineraryEditService {

    private final ItineraryRepository repository;

    private final Clock clock;

    public ItineraryEditService(ItineraryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 항목 하나를 고정하거나 푼다 — 명세 ITN-03(POST) · ITN-04(DELETE).
     *
     * <p>순서가 곧 정확성이다.
     * <ol>
     *   <li>{@code baseVersion} 이 최신인지 본다. 아니면 그 자리에서 409 — 어차피 버려질
     *       복사를 하지 않는다</li>
     *   <li>바탕 판의 내용을 읽어 {@code locked} 만 바꾼 복사본을 만든다</li>
     *   <li>판과 내용을 한 트랜잭션으로 저장한다. 저장소가 판 번호 UNIQUE 와 포인터
     *       조건부 갱신으로 마지막 경쟁까지 막는다</li>
     * </ol>
     *
     * <p>🔴 ①의 사전 확인만으로는 부족하다. 확인과 저장 사이에 다른 요청이 끼어들 수
     * 있어서 ③의 두 장치가 마지막 방어선이다.
     *
     * @param itemKey {@code itinerary_item.item_key} — 판을 건너 같은 항목을 가리키는 값이다.
     *     PK 가 아니다
     * @throws StaleItineraryVersionException 그 사이 다른 편집이 있었다 (409)
     * @throws ItineraryRevision.ItemNotFoundException 바탕 판에 그 항목이 없다 (404)
     * @throws NoSuchElementException 그런 일정이 없다 (404)
     */
    @Transactional
    public ItineraryVersion setItemLocked(String itineraryId, String itemKey, boolean locked,
                                          int baseVersion, String editorUserId) {

        Itinerary itinerary = repository.findById(itineraryId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다: " + itineraryId));

        // ① 검증하고 다음 번호를 정한다. 번호는 latestVersion 이 아니라
        //    검증된 baseVersion 에서 나온다 — 저장소의 포인터 조건과 짝이 맞아야 한다.
        int next = itinerary.nextVersionFrom(baseVersion);

        // ② 바탕 판의 내용. 🔴 없으면 조용히 빈 판을 만들지 않고 시끄럽게 실패한다 —
        //    그것이 지금 고치고 있는 바로 그 결함의 모양이기 때문이다.
        ItineraryContent base = repository.findContent(itineraryId, baseVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "바탕 판의 내용이 없습니다: itineraryId=" + itineraryId + ", version=" + baseVersion));

        Instant now = clock.instant();
        String newVersionId = UUID.randomUUID().toString();
        ItineraryRevision.Draft draft =
                ItineraryRevision.setLocked(base, newVersionId, itemKey, locked, now);

        ItineraryVersion candidate = new ItineraryVersion(
                newVersionId,
                itineraryId,
                next,
                baseVersion,
                // 🔴 푸는 것도 LOCK_ITEM 이다. ck_itinerary_version_operation 에
                //    UNLOCK_ITEM 이 없고, "고정 상태를 바꿨다" 는 하나의 사건이며 결과는
                //    항목의 locked 값에 남는다. 값을 더하려면 마이그레이션이 필요한데
                //    그만한 값이 없다.
                ItineraryVersion.Operation.LOCK_ITEM,
                editorUserId,
                // 🔴 사용자 편집은 추천 요청에서 나온 것이 아니라 requestId 가 없다.
                //    그래도 API-07 이 연결을 요구하므로 편집마다 새로 만든다.
                "req_edit_" + UUID.randomUUID(),
                // 🔴 재계산을 안 했으므로 버전 값이 없다. Versions.isComplete() 가 false 인
                //    값을 넣는다 — 지어낸 값을 넣으면 "이 일정은 어느 판으로 만들었나" 에
                //    거짓으로 답하게 된다(S15P21E201-542 3장).
                new ItineraryVersion.Versions(null, null, null, null, null),
                now);

        // ③ 저장 + 포인터 이동. 둘 다 저장소가 한 트랜잭션 안에서 한다.
        // 🔴 S15P21E201-249 — appendVersion 이 exclusions 를 4번째로 받게 되어(제외 목록도
        //    판마다 복사해야 하므로 항목·구간과 같은 자리에 둔다) 이 호출부도 함께 고쳤다.
        //    draft.exclusions() 는 ItineraryRevision.setLocked 가 바탕 판의 제외 목록을
        //    그대로 물려준 것이다 — 고정·해제가 제외 목록을 지우지 않는다.
        return repository.appendVersion(candidate, draft.items(), draft.legs(), draft.exclusions());
    }

    /**
     * S15P21E201-284 — 되돌리기. 어느 판의 내용을 <b>새 판</b>으로 복사한다(operation {@code REVERT}).
     *
     * <h2>별도 스냅샷 표가 없는 이유</h2>
     * 판 체인이 이미 그것이다. 항목·구간·제외 목록이 판마다 복사되므로 "편집 직전 상태" 는
     * {@code baseVersion} 판에 그대로 남아 있다. 되돌리기는 그 판을 읽어 새 판으로 복사하는 것이고,
     * 고정과 같은 복사 규칙({@link ItineraryRevision#copyOf})을 쓴다 — {@code item_key} 가 유지되고
     * 제외 목록도 그 판의 것으로 돌아간다(뺀 장소가 돌아오면서 제외도 풀린다).
     *
     * <h2>어느 판으로 돌아가나</h2>
     * {@code toVersion} 을 안 주면 <b>최신 판을 만들 때 바탕이 됐던 판</b>({@code latest.baseVersion})이다.
     * 즉 "마지막 편집 직전으로". 되돌리기 판 자체도 {@code baseVersion} 을 가지므로 되돌리기를 한 번 더
     * 누르면 되돌리기 직전(= 다시 실행)으로 간다. {@code toVersion} 을 주면 그 판으로 간다 —
     * {@code GET /versions} 목록에서 고른 경우다.
     *
     * <p>덮어쓰지 않는다(FR-ITN-08). 중간 판은 전부 남고 {@code reverted_from_version} 에 어디로
     * 돌아갔는지 남는다. {@code base_version} 은 누를 때 보고 있던 최신 판이라 대개 다른 값이다.
     *
     * @throws NothingToRevertException 최초 판(CREATE) 위에서 {@code toVersion} 없이 눌렀다 — 되돌릴 편집이 없다
     * @throws RevertTargetException {@code toVersion} 이 1 미만이거나 현재 판 이상이거나 그 판이 없다
     * @throws StaleItineraryVersionException {@code baseVersion} 이 최신이 아니다 — 409
     */
    @Transactional
    public ItineraryVersion revert(String itineraryId, int baseVersion, Integer toVersion, String editorUserId) {
        Itinerary itinerary = repository.findById(itineraryId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다: " + itineraryId));
        int next = itinerary.nextVersionFrom(baseVersion);

        int target;
        if (toVersion == null) {
            ItineraryVersion latest = repository.findVersion(itineraryId, baseVersion)
                    .orElseThrow(() -> new IllegalStateException(
                            "최신 판 행이 없습니다: itineraryId=" + itineraryId + ", version=" + baseVersion));
            if (latest.baseVersion() == null) {
                // CREATE 판이다 — 그 앞에 아무 편집도 없다. 오류로 죽지 않고 그 사실을 돌려준다.
                throw new NothingToRevertException(itineraryId, baseVersion);
            }
            target = latest.baseVersion();
        }
        else {
            if (toVersion < 1 || toVersion >= baseVersion) {
                throw new RevertTargetException(itineraryId, toVersion, baseVersion,
                        "돌아갈 판은 1 이상, 현재 판(" + baseVersion + ") 미만이어야 합니다: " + toVersion);
            }
            target = toVersion;
        }

        ItineraryContent source = repository.findContent(itineraryId, target)
                .orElseThrow(() -> new RevertTargetException(itineraryId, target, baseVersion,
                        "돌아갈 판의 내용이 없습니다: version=" + target));

        Instant now = clock.instant();
        String newVersionId = UUID.randomUUID().toString();
        ItineraryRevision.Draft draft = ItineraryRevision.copyOf(source, newVersionId, now);

        // 모델·피처 버전 다섯 칸은 비운다 — 엔진을 돌리지 않았다. 어느 판의 내용인지는 revertedFromVersion 이 말한다.
        ItineraryVersion candidate = new ItineraryVersion(
                newVersionId,
                itineraryId,
                next,
                baseVersion,
                ItineraryVersion.Operation.REVERT,
                editorUserId,
                "req_edit_" + UUID.randomUUID(),
                new ItineraryVersion.Versions(null, null, null, null, null),
                now,
                null,
                List.of(),
                target);
        return repository.appendVersion(candidate, draft.items(), draft.legs(), draft.exclusions());
    }

    /** 최초 판 위에서 되돌리기를 눌렀다 — 되돌릴 편집이 없다. 422 로 답할 자리다. */
    public static class NothingToRevertException extends RuntimeException {

        private final String itineraryId;
        private final int latestVersion;

        public NothingToRevertException(String itineraryId, int latestVersion) {
            super("되돌릴 편집이 없습니다. 이 일정은 아직 편집된 적이 없습니다.");
            this.itineraryId = itineraryId;
            this.latestVersion = latestVersion;
        }

        public String itineraryId() { return itineraryId; }
        public int latestVersion()  { return latestVersion; }
    }

    /** 돌아갈 판 번호가 범위 밖이거나 그 판이 없다 — 400 으로 답할 자리다. */
    public static class RevertTargetException extends RuntimeException {

        private final String itineraryId;
        private final int toVersion;
        private final int latestVersion;

        public RevertTargetException(String itineraryId, int toVersion, int latestVersion, String message) {
            super(message);
            this.itineraryId = itineraryId;
            this.toVersion = toVersion;
            this.latestVersion = latestVersion;
        }

        public String itineraryId() { return itineraryId; }
        public int toVersion()      { return toVersion; }
        public int latestVersion()  { return latestVersion; }
    }
}
