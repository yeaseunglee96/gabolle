package com.gabolle.backend.trip.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 여행 — 사용자가 입력한 조건 묶음. 조건을 저장해 두지 않으면 계산이 끝난 뒤 무슨 조건으로 만든
 * 일정인지 되짚을 수 없고, 결과를 고치거나 다시 만들 수도 없다.
 */
public class Trip {

    private final String tripId;
    private final String createdBy;

    /**
     * {@code createdBy} 가 회원인지 익명 세션인지 구분한다. 가입 전 만든 여행은
     * {@code ANONYMOUS} 로 시작해 가입 시 {@code USER} 로 승계된다.
     */
    private final OwnerType ownerType;

    private final LocalDate startDate;
    private final LocalDate finishDate;

    /** 출발지. 매일 여기서 일정이 시작된다. */
    private final Double originLat;
    private final Double originLng;

    private final Integer budgetKrw;
    private final int partySize;

    /** 하루 활동 시간대. 예: {@code MORNING_TO_EVENING} */
    private final String timeWindow;

    /**
     * {@code timeWindow}(프리셋)과는 다른 칸이다. 프리셋 이름만으로는 몇 시부터 몇 시까지인지
     * 계산할 수 없어, 추천 엔진이 실제 활동 시각을 읽으려면 이 칸이 필요하다.
     */
    private final LocalTime timeWindowStart;
    private final LocalTime timeWindowEnd;

    /**
     * 여행 중 쓸 이동 수단. 값 목록은 마이그레이션 {@code ck_trip_travel_modes} 가 정본이고,
     * DB CHECK 가 먼저 터지면 어느 필드가 문제인지 응답에 안 남으므로 여기서도 검증한다.
     */
    private final String[] travelModes;

    /** 시간대를 공통 사전으로 고정한다. 안 맞추면 일정이 통째로 밀린다. */
    private final String timezone;

    /** 매일 여기서 시작하고 여기로 돌아온다. 없으면 일정의 시작점·끝점이 매일 달라진다. */
    private final String accommodationPlaceId;

    /** 영어 메뉴가 있는 곳을 우선한다 (선호, HARD 필터 아님). */
    private final boolean englishMenuRequired;

    /** 해외 카드를 받는 곳을 우선한다 (선호). */
    private final boolean foreignCardRequired;

    /** 혼밥하기 편한 곳을 우선한다 (선호). */
    private final boolean soloFriendlyPriority;

    /**
     * 대중교통 최대 환승 횟수. {@code null} 이면 제한 없음.
     *
     * <p>이동 수단에 {@code PRIVATE_CAR} 가 있으면 이 값은 뜻이 없고, 저장 시점에서 {@code null}
     * 로 만든다. 도메인은 그 규칙을 강제하지 않는다 — {@code travelModes} 가 나중에 바뀔 수 있어
     * 생성자에서 못박으면 순서에 따라 결과가 달라진다.
     */
    private final Integer maxTransitTransfers;

    /**
     * 사용자가 붙인 이름. {@code null} 이 「이름 없음」이고 빈 문자열은 쓰지 않는다 — 두 가지가
     * 같은 뜻을 말하면 화면이 둘 다 검사해야 하고 언젠가 한쪽을 빠뜨린다. 이름은 만들 때가 아니라
     * 나중에 붙는 값이라 생성자에 없다.
     */
    private String title;

    private Status status;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * {@code null} 이 아니면 지워진 것이다. "지워졌다" 를 말하는 자리는 이 칸 하나뿐이고
     * {@link Status} 에는 없다 — 두 자리에 같은 뜻을 담으면 어긋나는 날 어느 쪽이 맞는지 모른다.
     */
    private Instant deletedAt;

    /** 짧은 생성자들은 뒤에 붙은 칸을 기본값(null·false)으로 채워 가장 긴 생성자에 위임한다. */
    public Trip(String tripId, String createdBy,
                LocalDate startDate, LocalDate finishDate,
                Double originLat, Double originLng,
                Integer budgetKrw, int partySize,
                String timeWindow, String timezone,
                Instant createdAt) {
        this(tripId, createdBy, startDate, finishDate, originLat, originLng, budgetKrw, partySize,
                timeWindow, timezone, null, null, null, createdAt);
    }

    public Trip(String tripId, String createdBy,
                LocalDate startDate, LocalDate finishDate,
                Double originLat, Double originLng,
                Integer budgetKrw, int partySize,
                String timeWindow, String timezone,
                String[] travelModes, LocalTime timeWindowStart, LocalTime timeWindowEnd,
                Instant createdAt) {
        this(tripId, createdBy, startDate, finishDate, originLat, originLng, budgetKrw, partySize,
                timeWindow, timezone, travelModes, timeWindowStart, timeWindowEnd,
                null, false, false, false, null, createdAt);
    }

    public Trip(String tripId, String createdBy,
                LocalDate startDate, LocalDate finishDate,
                Double originLat, Double originLng,
                Integer budgetKrw, int partySize,
                String timeWindow, String timezone,
                String[] travelModes, LocalTime timeWindowStart, LocalTime timeWindowEnd,
                String accommodationPlaceId, boolean englishMenuRequired, boolean foreignCardRequired,
                boolean soloFriendlyPriority, Integer maxTransitTransfers,
                Instant createdAt) {
        this(tripId, createdBy, OwnerType.USER, startDate, finishDate, originLat, originLng, budgetKrw, partySize,
                timeWindow, timezone, travelModes, timeWindowStart, timeWindowEnd,
                accommodationPlaceId, englishMenuRequired, foreignCardRequired, soloFriendlyPriority,
                maxTransitTransfers, createdAt);
    }

    /** 위 생성자들은 {@code OwnerType.USER} 로 고정해 여기에 위임한다. */
    public Trip(String tripId, String createdBy, OwnerType ownerType,
                LocalDate startDate, LocalDate finishDate,
                Double originLat, Double originLng,
                Integer budgetKrw, int partySize,
                String timeWindow, String timezone,
                String[] travelModes, LocalTime timeWindowStart, LocalTime timeWindowEnd,
                String accommodationPlaceId, boolean englishMenuRequired, boolean foreignCardRequired,
                boolean soloFriendlyPriority, Integer maxTransitTransfers,
                Instant createdAt) {

        if (startDate == null || finishDate == null) {
            throw new IllegalArgumentException("여행 시작일과 종료일은 필수다");
        }
        if (finishDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "종료일(" + finishDate + ")이 시작일(" + startDate + ")보다 앞이다");
        }
        if (partySize < 1) {
            throw new IllegalArgumentException("인원은 1명 이상이어야 한다: " + partySize);
        }
        if (budgetKrw != null && budgetKrw < 0) {
            throw new IllegalArgumentException("예산은 음수일 수 없다: " + budgetKrw);
        }
        if (originLat != null && (originLat < -90 || originLat > 90)) {
            throw new IllegalArgumentException("위도 범위를 벗어났다: " + originLat);
        }
        if (originLng != null && (originLng < -180 || originLng > 180)) {
            throw new IllegalArgumentException("경도 범위를 벗어났다: " + originLng);
        }
        if (maxTransitTransfers != null && maxTransitTransfers < 0) {
            throw new IllegalArgumentException("최대 환승 횟수는 음수일 수 없다: " + maxTransitTransfers);
        }

        this.tripId = tripId;
        this.createdBy = createdBy;
        this.ownerType = ownerType != null ? ownerType : OwnerType.USER;
        this.startDate = startDate;
        this.finishDate = finishDate;
        this.originLat = originLat;
        this.originLng = originLng;
        this.budgetKrw = budgetKrw;
        this.partySize = partySize;
        this.timeWindow = timeWindow;
        this.timeWindowStart = timeWindowStart;
        this.timeWindowEnd = timeWindowEnd;
        this.travelModes = validateTravelModes(travelModes);
        this.accommodationPlaceId = accommodationPlaceId;
        this.englishMenuRequired = englishMenuRequired;
        this.foreignCardRequired = foreignCardRequired;
        this.soloFriendlyPriority = soloFriendlyPriority;
        this.maxTransitTransfers = maxTransitTransfers;
        this.timezone = timezone != null ? timezone : "Asia/Seoul";
        this.status = Status.PLANNING;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    private static final Set<String> ALLOWED_TRAVEL_MODES = Set.of(
            "WALK", "BUS", "SUBWAY", "TAXI", "PRIVATE_CAR", "RENTAL_CAR", "BICYCLE", "FERRY", "OTHER");

    private static String[] validateTravelModes(String[] travelModes) {
        if (travelModes == null) {
            return new String[0];
        }
        for (String mode : travelModes) {
            if (mode == null || !ALLOWED_TRAVEL_MODES.contains(mode)) {
                throw new IllegalArgumentException("허용되지 않는 travelMode 다: " + mode);
            }
        }
        return travelModes.clone();
    }

    /**
     * 저장소가 읽어온 값 그대로 되살린다 — 저장소 전용. 생성 이후 업무 규칙을 거치며 바뀐
     * {@code status}·{@code updatedAt}·{@code deletedAt} 을 그대로 받는다. 그 규칙들은 "한 번만
     * 반영한다" 같은 부작용이 있어 다시 태우면 값이 틀어지므로 재검증하지 않는다.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String tripId;
        private String createdBy;
        private OwnerType ownerType;
        private LocalDate startDate;
        private LocalDate finishDate;
        private Double originLat;
        private Double originLng;
        private Integer budgetKrw;
        private int partySize;
        private String timeWindow;
        private LocalTime timeWindowStart;
        private LocalTime timeWindowEnd;
        private String[] travelModes;
        private String accommodationPlaceId;
        private boolean englishMenuRequired;
        private boolean foreignCardRequired;
        private boolean soloFriendlyPriority;
        private Integer maxTransitTransfers;
        private String timezone;
        private String title;
        private Status status;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant deletedAt;

        private Builder() {
        }

        public Builder tripId(String tripId) { this.tripId = tripId; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }
        public Builder ownerType(OwnerType ownerType) { this.ownerType = ownerType; return this; }
        public Builder startDate(LocalDate startDate) { this.startDate = startDate; return this; }
        public Builder finishDate(LocalDate finishDate) { this.finishDate = finishDate; return this; }
        public Builder originLat(Double originLat) { this.originLat = originLat; return this; }
        public Builder originLng(Double originLng) { this.originLng = originLng; return this; }
        public Builder budgetKrw(Integer budgetKrw) { this.budgetKrw = budgetKrw; return this; }
        public Builder partySize(int partySize) { this.partySize = partySize; return this; }
        public Builder timeWindow(String timeWindow) { this.timeWindow = timeWindow; return this; }
        public Builder timeWindowStart(LocalTime timeWindowStart) { this.timeWindowStart = timeWindowStart; return this; }
        public Builder timeWindowEnd(LocalTime timeWindowEnd) { this.timeWindowEnd = timeWindowEnd; return this; }
        public Builder travelModes(String[] travelModes) { this.travelModes = travelModes; return this; }
        public Builder accommodationPlaceId(String accommodationPlaceId) { this.accommodationPlaceId = accommodationPlaceId; return this; }
        public Builder englishMenuRequired(boolean englishMenuRequired) { this.englishMenuRequired = englishMenuRequired; return this; }
        public Builder foreignCardRequired(boolean foreignCardRequired) { this.foreignCardRequired = foreignCardRequired; return this; }
        public Builder soloFriendlyPriority(boolean soloFriendlyPriority) { this.soloFriendlyPriority = soloFriendlyPriority; return this; }
        public Builder maxTransitTransfers(Integer maxTransitTransfers) { this.maxTransitTransfers = maxTransitTransfers; return this; }
        public Builder timezone(String timezone) { this.timezone = timezone; return this; }
        /** {@link Trip#rename} 을 태우지 않는다 — 이미 규칙을 통과해 저장된 값이다. */
        public Builder title(String title) { this.title = title; return this; }
        public Builder status(Status status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder deletedAt(Instant deletedAt) { this.deletedAt = deletedAt; return this; }

        /**
         * 생성 규칙(status=PLANNING·updatedAt=createdAt)을 먼저 태우고 저장소가 읽어온 실제 값이
         * 있으면 그 위에 덮는다 — 생성자만으로는 그 세 칸을 지정할 방법이 없다.
         */
        public Trip build() {
            Trip trip = new Trip(tripId, createdBy, ownerType, startDate, finishDate, originLat, originLng,
                    budgetKrw, partySize, timeWindow, timezone, travelModes, timeWindowStart, timeWindowEnd,
                    accommodationPlaceId, englishMenuRequired, foreignCardRequired, soloFriendlyPriority,
                    maxTransitTransfers, createdAt);
            trip.title = title;
            if (status != null) {
                trip.status = status;
            }
            if (updatedAt != null) {
                trip.updatedAt = updatedAt;
            }
            trip.deletedAt = deletedAt;
            return trip;
        }
    }

    /** 며칠짜리 여행인가. 당일치기는 1이다. */
    public int nights() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, finishDate);
    }

    public int days() {
        return nights() + 1;
    }

    /** {@code createdBy} 가 가리키는 표. */
    public enum OwnerType {
        /** {@code createdBy} 는 {@code app_user.user_id} 다. */
        USER,
        /** {@code createdBy} 는 {@code anonymous_session.session_id} 다. 가입하면 {@code USER} 로 승계된다. */
        ANONYMOUS
    }

    public enum Status {
        /** 조건만 저장된 상태. 아직 일정이 없다 */
        PLANNING,
        /** 일정이 만들어졌다 */
        READY,
        /** 여행 중 */
        IN_PROGRESS,
        COMPLETED;

        /** 삭제는 {@link Trip#isDeleted()} 로 따로 본다. 여기 안 넣는다. */
        public boolean isTerminal() {
            return this == COMPLETED;
        }
    }

    /**
     * 행을 지우지 않는다 — 일정·이벤트가 이 여행을 가리킨다. {@code status} 도 안 건드린다 —
     * 지워지기 전에 어느 단계였는지가 남아야 분석에서 구분된다.
     */
    public void markDeleted(Instant at) {
        if (this.deletedAt != null) {
            return;
        }
        this.deletedAt = at;
        this.updatedAt = at;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void markReady(Instant at) {
        if (isDeleted() || status.isTerminal()) {
            throw new IllegalStateException("끝난 여행은 상태를 바꿀 수 없다: " + status);
        }
        this.status = Status.READY;
        this.updatedAt = at;
    }

    /** 카드 한 줄에 들어가는 한계. 마이그레이션의 {@code trip.title varchar(60)} 과 같은 숫자다. */
    public static final int TITLE_MAX_LENGTH = 60;

    /**
     * 이름을 붙이거나 지운다. 비었거나 공백뿐이면 {@code null} 로 만든다 — 지우기 전용 경로가
     * 따로 없고, 빈 이름을 보내는 것이 지우는 것이다.
     *
     * <p>줄바꿈·제어문자는 거부한다. 이름이 한 줄이 아니면 카드가 밀려 목록 전체가 어긋난다.
     * 길이는 자바 길이가 아니라 글자 수로 센다 — 이모지 하나가 자바에서는 두 칸이지만
     * {@code varchar(60)} 은 한 글자로 세므로, 자바 길이로 재면 멀쩡한 이름이 거부된다.
     */
    public void rename(String title, Instant at) {
        String trimmed = (title == null) ? null : title.trim();
        if (trimmed != null && trimmed.isEmpty()) {
            trimmed = null;
        }
        if (trimmed != null) {
            if (trimmed.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("여행 이름에 줄바꿈이나 제어문자를 넣을 수 없다");
            }
            int length = trimmed.codePointCount(0, trimmed.length());
            if (length > TITLE_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "여행 이름은 " + TITLE_MAX_LENGTH + "자를 넘을 수 없다: " + length + "자");
            }
        }
        this.title = trimmed;
        this.updatedAt = at;
    }

    /**
     * 화면에 내보일 이름. 사용자가 붙인 이름이 있으면 그것, 없으면 기간이다.
     *
     * <p>여기에 한국어를 섞지 않는다. 서버는 말이 안 섞인 값만 주고, 「여행 일정」 같은 말이
     * 필요하면 화면이 자기 언어로 붙인다 — 여행 목록 화면({@code app/(tabs)/trips.tsx} 의
     * {@code dateLabel})이 그렇게 하고 있다. 부르는 쪽에서 날짜에 한국어를 이어 붙이면
     * 영어·일본어로 쓰는 사람의 여행 이름이 전부 한국어가 된다.
     *
     * <p>{@link #title()} 을 반드시 먼저 본다. 안 보면 {@link #rename} 이 일정 화면과 공유
     * 화면에서만 아무 일도 안 한 것이 된다.
     */
    public String displayTitle() {
        String named = title;
        if (named != null && !named.isBlank()) {
            return named;
        }
        return startDate + " ~ " + finishDate;
    }

    public String tripId()       { return tripId; }
    public String createdBy()    { return createdBy; }
    /** {@code null} 이면 아직 이름이 없다 — 화면이 날짜를 제목으로 쓴다. */
    public String title()        { return title; }
    public OwnerType ownerType() { return ownerType; }
    public LocalDate startDate() { return startDate; }
    public LocalDate finishDate(){ return finishDate; }
    public Double originLat()    { return originLat; }
    public Double originLng()    { return originLng; }
    public Integer budgetKrw()   { return budgetKrw; }
    public int partySize()       { return partySize; }
    public String timeWindow()   { return timeWindow; }
    public LocalTime timeWindowStart() { return timeWindowStart; }
    public LocalTime timeWindowEnd()   { return timeWindowEnd; }
    /** 방어적 복사본 — 밖에서 바꿔도 이 여행의 값은 안 바뀐다. */
    public String[] travelModes() { return travelModes.clone(); }
    public String accommodationPlaceId()     { return accommodationPlaceId; }
    public boolean englishMenuRequired()     { return englishMenuRequired; }
    public boolean foreignCardRequired()     { return foreignCardRequired; }
    public boolean soloFriendlyPriority()    { return soloFriendlyPriority; }
    public Integer maxTransitTransfers()     { return maxTransitTransfers; }
    public String timezone()     { return timezone; }
    public Status status()       { return status; }
    public Instant createdAt()   { return createdAt; }
    public Instant updatedAt()   { return updatedAt; }
    public Instant deletedAt()   { return deletedAt; }
}
