package com.gabolle.backend.itinerary.domain;

import java.time.Instant;
import java.util.List;

/**
 * 일정의 한 판(version) — 덮어쓰지 않는 스냅샷.
 *
 * <p>🔴 이 클래스가 공동 편집 전체의 핵심이다. 모든 편집은 기존 판을 고치는 것이 아니라
 * <b>새 판을 만든다.</b> 온톨로지 명세가 {@code ItineraryVersion} 을
 * "덮어쓰지 않는 일정 스냅샷" 으로 정의한 그대로다.
 *
 * <p>왜 덮어쓰지 않는가: 동행자 전원이 EDITOR 라서 두 사람이 같은 일정을 동시에 고친다.
 * 덮어쓰기를 허용하면 나중에 저장한 쪽이 앞사람의 변경을 <b>조용히</b> 지우고,
 * 아무도 모른 채 여행 당일에 "가기로 했던 곳이 사라졌다" 로 드러난다.
 *
 * <p>개발계획서 4.2 — "M1 에 넣는다. 나중에 넣으면 편집 로직을 다시 짠다."
 */
public class ItineraryVersion {

    private final String itineraryVersionId;
    private final String itineraryId;

    /** 이 판의 번호. 1부터 시작해 편집마다 1씩 오른다. */
    private final int version;

    /**
     * 무엇을 고쳐서 만든 판인가.
     *
     * <p>최초 생성은 {@code null}. 클라이언트가 보낸 {@code baseVersion} 이
     * 최신과 다르면 그 사이에 누가 고쳤다는 뜻이므로 409 로 거절한다.
     */
    private final Integer baseVersion;

    private final Operation operation;
    private final String createdBy;

    /** 🔴 어느 추천 요청에서 나온 판인가 (API-07). 노출·행동을 잇는 축. */
    private final String requestId;

    /** 🔴 무엇으로 만들었나 — 이게 없으면 결과를 재현할 수 없다 (NFR-08). */
    private final Versions versions;

    private final Instant createdAt;

    /**
     * 🔴 이 판을 만든 진짜 추천 요청 — S15P21E201-604. {@code requestId}(VARCHAR, 사용자
     * 편집의 {@code req_edit_<uuid>} 도 담는다)와 다르다. 이 값이 있으면 UUID 형식 그대로다.
     *
     * <p>추천이 일정을 처음 만들 때만(operation=CREATE) 채워진다. 사용자 편집(LOCK_ITEM 등)은
     * 진짜 추천 요청에서 나온 것이 아니므로 {@code null} 이다. {@code itinerary_versions
     * .source_request_id}(UNIQUE, V20260905120000)로 저장되어 같은 추천 요청이 두 번
     * 실행돼도 판이 하나만 생기는 것을 DB 가 보장한다.
     */
    private final String sourceRequestId;

    public ItineraryVersion(String itineraryVersionId, String itineraryId, int version,
                            Integer baseVersion, Operation operation, String createdBy,
                            String requestId, Versions versions, Instant createdAt) {
        this(itineraryVersionId, itineraryId, version, baseVersion, operation, createdBy,
                requestId, versions, createdAt, null);
    }

    /**
     * 🔴 추천이 실제로 판을 만든 경로(ItineraryDraftService.persist)가 쓰는 생성자다.
     * 기존 9-인자 생성자는 이 값을 {@code null} 로 넘기는 것과 같다 — 사용자 편집은
     * 그대로 그 생성자를 쓴다.
     */
    public ItineraryVersion(String itineraryVersionId, String itineraryId, int version,
                            Integer baseVersion, Operation operation, String createdBy,
                            String requestId, Versions versions, Instant createdAt,
                            String sourceRequestId) {
        if (version < 1) {
            throw new IllegalArgumentException("판 번호는 1 이상이어야 한다: " + version);
        }
        if (baseVersion != null && baseVersion >= version) {
            // 새 판은 반드시 바탕이 된 판보다 뒤여야 한다.
            throw new IllegalArgumentException(
                    "baseVersion(" + baseVersion + ") 이 version(" + version + ") 보다 앞이어야 한다");
        }
        this.itineraryVersionId = itineraryVersionId;
        this.itineraryId = itineraryId;
        this.version = version;
        this.baseVersion = baseVersion;
        this.operation = operation;
        this.createdBy = createdBy;
        this.requestId = requestId;
        this.versions = versions;
        this.createdAt = createdAt;
        this.sourceRequestId = sourceRequestId;
    }

    /** 어떤 편집으로 이 판이 생겼는가. */
    public enum Operation {
        /** 최초 생성 */
        CREATE,
        /** 전체 재생성 */
        REGENERATE,
        /** 하루만 다시 계산 */
        REGENERATE_DAY,
        /** 장소 교체 */
        REPLACE_ITEM,
        /** 장소 제거 */
        REMOVE_ITEM,
        /** 장소 고정 */
        LOCK_ITEM,
        /** 순서 변경 */
        REORDER;

        /** 최초 생성만 baseVersion 이 없다. */
        public boolean requiresBaseVersion() {
            return this != CREATE;
        }
    }

    /**
     * 이 판을 만든 계산의 판들.
     *
     * <p>🔴 다섯 개가 모두 필요하다. 하나만 없어도 재현이 깨진다 —
     * 같은 취향 값이라도 모델 판이 다르면 다른 일정이 나오고,
     * 같은 모델이라도 장소 데이터 판이 다르면 또 다른 일정이 나온다.
     * S15P21E201-542 데이터 수집 명세 3장이 dataset 까지 요구한다.
     */
    public record Versions(
            String modelVersion,
            String featureVersion,
            String ontologyVersion,
            String policyVersion,
            String datasetVersion) {

        public boolean isComplete() {
            return notBlank(modelVersion) && notBlank(featureVersion)
                    && notBlank(ontologyVersion) && notBlank(policyVersion)
                    && notBlank(datasetVersion);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    public String itineraryVersionId() { return itineraryVersionId; }
    public String itineraryId()        { return itineraryId; }
    public int version()               { return version; }
    public Integer baseVersion()       { return baseVersion; }
    public Operation operation()       { return operation; }
    public String createdBy()          { return createdBy; }
    public String requestId()          { return requestId; }
    public Versions versions()         { return versions; }
    public Instant createdAt()         { return createdAt; }
    public String sourceRequestId()    { return sourceRequestId; }
}
