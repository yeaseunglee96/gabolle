package com.gabolle.backend.itinerary.domain;

import java.time.Instant;
import java.util.List;

/**
 * 일정의 한 판(version) — 덮어쓰지 않는 스냅샷.
 * 모든 편집은 기존 판을 고치는 것이 아니라 새 판을 만든다. 동행자 전원이 EDITOR 라 두 사람이
 * 같은 일정을 동시에 고치는데, 덮어쓰기를 허용하면 나중에 저장한 쪽이 앞사람의 변경을 조용히
 * 지우고 여행 당일에 "가기로 했던 곳이 사라졌다" 로 드러난다.
 */
public class ItineraryVersion {

    private final String itineraryVersionId;
    private final String itineraryId;

    /** 이 판의 번호. 1부터 시작해 편집마다 1씩 오른다. */
    private final int version;

    /**
     * 무엇을 고쳐서 만든 판인가. 최초 생성은 {@code null}.
     * 클라이언트가 보낸 {@code baseVersion} 이 최신과 다르면 그 사이에 누가 고쳤다는 뜻이므로
     * 409 로 거절한다.
     */
    private final Integer baseVersion;

    private final Operation operation;
    private final String createdBy;

    /** 어느 추천 요청에서 나온 판인가. 노출과 행동을 잇는 축이다. */
    private final String requestId;

    /** 무엇으로 만들었나 — 이게 없으면 결과를 재현할 수 없다. */
    private final Versions versions;

    private final Instant createdAt;

    /**
     * 이 판을 만든 진짜 추천 요청. {@code requestId}(사용자 편집의 {@code req_edit_<uuid>} 도
     * 담는다)와 다르고, 값이 있으면 UUID 형식 그대로다.
     * 추천이 일정을 처음 만들 때만(operation=CREATE) 채워진다. 사용자 편집은 진짜 추천 요청에서
     * 나온 것이 아니라 {@code null} 이다. {@code itinerary_versions.source_request_id}(UNIQUE)로
     * 저장되어 같은 추천 요청이 두 번 실행돼도 판이 하나만 생기는 것을 DB 가 보장한다.
     */
    private final String sourceRequestId;

    /**
     * 이 판 전체에 대한 경고 코드(예: {@code RECALC_NO_CANDIDATE}).
     * 항목이 아예 없는 시간대에 대한 경고는 {@link ItineraryItem#warningCodes()} 에 적을 자리가
     * 없다 — 항목이 있어야만 존재하는 칸이다. 그래서 판 전체에 적는다.
     */
    private final List<String> warningCodes;

    /**
     * 되돌리기(operation=REVERT)가 내용을 복사해 온 옛 판. REVERT 가 아니면 {@code null}.
     * {@code base_version}(되돌리기를 누를 때 보고 있던 최신 판)과는 다른 칸이다 — 5번 판을
     * 보다가 2번으로 되돌리면 {@code baseVersion=5}, {@code revertedFromVersion=2} 다.
     */
    private final Integer revertedFromVersion;

    public ItineraryVersion(String itineraryVersionId, String itineraryId, int version,
                            Integer baseVersion, Operation operation, String createdBy,
                            String requestId, Versions versions, Instant createdAt) {
        this(itineraryVersionId, itineraryId, version, baseVersion, operation, createdBy,
                requestId, versions, createdAt, null, List.of(), null);
    }

    /**
     * 추천이 실제로 판을 만든 경로가 쓰는 생성자다. 기존 9-인자 생성자는 이 값을 {@code null} 로
     * 넘기는 것과 같다.
     */
    public ItineraryVersion(String itineraryVersionId, String itineraryId, int version,
                            Integer baseVersion, Operation operation, String createdBy,
                            String requestId, Versions versions, Instant createdAt,
                            String sourceRequestId) {
        this(itineraryVersionId, itineraryId, version, baseVersion, operation, createdBy,
                requestId, versions, createdAt, sourceRequestId, List.of(), null);
    }

    /**
     * {@code warningCodes} 를 받는 생성자. 기존 9-인자·10-인자 생성자는 이 값을 빈 목록으로
     * 넘기는 것과 같다.
     */
    public ItineraryVersion(String itineraryVersionId, String itineraryId, int version,
                            Integer baseVersion, Operation operation, String createdBy,
                            String requestId, Versions versions, Instant createdAt,
                            String sourceRequestId, List<String> warningCodes) {
        this(itineraryVersionId, itineraryId, version, baseVersion, operation, createdBy,
                requestId, versions, createdAt, sourceRequestId, warningCodes, null);
    }

    /**
     * 되돌리기가 쓰는 생성자. 기존 9·10·11-인자 생성자는 이 값을 {@code null} 로 넘기는 것과 같다.
     * DB 의 {@code ck_itinerary_version_reverted_from} 과 같은 규칙을 자바 쪽에도 두는 이유는,
     * DB 제약 위반은 예외 스택이 JDBC 드라이버 안에서 끊겨 어느 자바 코드가 잘못된 값을 만들었는지
     * 가리키지 못하기 때문이다. 여기서 먼저 막으면 {@link IllegalArgumentException} 이 호출부를
     * 그대로 가리킨다.
     */
    public ItineraryVersion(String itineraryVersionId, String itineraryId, int version,
                            Integer baseVersion, Operation operation, String createdBy,
                            String requestId, Versions versions, Instant createdAt,
                            String sourceRequestId, List<String> warningCodes,
                            Integer revertedFromVersion) {
        if (version < 1) {
            throw new IllegalArgumentException("판 번호는 1 이상이어야 한다: " + version);
        }
        if (baseVersion != null && baseVersion >= version) {
            // 새 판은 반드시 바탕이 된 판보다 뒤여야 한다.
            throw new IllegalArgumentException(
                    "baseVersion(" + baseVersion + ") 이 version(" + version + ") 보다 앞이어야 한다");
        }
        if (operation == Operation.REVERT) {
            if (revertedFromVersion == null) {
                throw new IllegalArgumentException("operation=REVERT 인데 revertedFromVersion 이 없다");
            }
            if (revertedFromVersion >= version) {
                throw new IllegalArgumentException(
                        "revertedFromVersion(" + revertedFromVersion + ") 이 version(" + version + ") 보다 앞이어야 한다");
            }
        }
        else if (revertedFromVersion != null) {
            throw new IllegalArgumentException(
                    "operation=" + operation + " 인데 revertedFromVersion 이 있다: " + revertedFromVersion);
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
        this.warningCodes = warningCodes == null ? List.of() : List.copyOf(warningCodes);
        this.revertedFromVersion = revertedFromVersion;
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
        REORDER,
        /** 되돌리기. {@code revertedFromVersion} 이 가리키는 옛 판의 내용을 새 판으로 복사한다.
                 * 엔진을 돌리지 않으므로 {@link Versions} 다섯 칸이 전부 비어 들어온다. */
        REVERT,
        /** 사용자가 고른 장소를 그 날의 마지막에 더한다. 더한 항목은 고정된 상태로 들어가고
                 * (재계산이 그것을 빼면 안 되므로) 시각은 뒤따르는 재계산이 정한다. */
        ADD_ITEM,
        /** 남은 하루 재계획. 장소·순서·구간은 그대로 두고 아직 지나지 않은 방문지의 시각만
                 * 다시 매긴다 — {@link ItineraryRevision#withReplannedDay} 가 규칙을 정한다. */
        REPLAN_DAY;

        /** 최초 생성만 baseVersion 이 없다. */
        public boolean requiresBaseVersion() {
            return this != CREATE;
        }
    }

    /**
     * 이 판을 만든 계산의 판들.
     * 다섯 개가 모두 필요하다. 하나만 없어도 재현이 깨진다 — 같은 취향 값이라도 모델 판이 다르면
     * 다른 일정이 나오고, 같은 모델이라도 장소 데이터 판이 다르면 또 다른 일정이 나온다.
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
    public List<String> warningCodes() { return warningCodes; }
    public Integer revertedFromVersion() { return revertedFromVersion; }
}
