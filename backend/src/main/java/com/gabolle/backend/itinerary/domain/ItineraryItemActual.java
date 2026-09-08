package com.gabolle.backend.itinerary.domain;

import java.time.Instant;

/**
 * 방문지에 <b>실제로</b> 도착·출발한 시각 — S15P21E201-293.
 *
 * <p>계획 시각({@link ItineraryItem#startTime()})과 다른 사실이다. 계획은 "이때 가려고 했다"
 * 이고 이것은 "이때 갔다" 다. 둘을 한 칸에 담으면 어느 쪽인지 구분할 수 없으므로 자리를
 * 따로 둔다.
 *
 * <h2>🔴 이 기록의 이름은 {@code (itineraryId, itemKey)} 다 — 항목 행이 아니다</h2>
 * 일정은 편집할 때마다 새 판을 만들고 항목을 전부 <b>복사</b>한다
 * ({@link ItineraryRevision#copyOf}). 그래서 실제 시각을 항목 행에 적으면 다음 편집에서
 * 그 판의 항목만 남고 <b>어제 찍은 도착 시각이 사라진다.</b> 판을 건너 살아남는 이름은
 * {@link ItineraryItem#itemKey()} 이므로(그 값이 유지되는 것이 {@link ItineraryRevision}
 * 주석에 명시돼 있다) 그 둘이 이 기록의 이름이다.
 *
 * <p>그래서 이 record 에는 표의 대리 키({@code itinerary_item_actual_id})가 <b>없다.</b>
 * 표에 PK 칸이 필요해서 있는 값이지 도메인이 이 기록을 가리키는 이름이 아니다 — 여기에
 * 실어 두면 "덮어쓴 뒤 그 값이 그대로인가" 를 아무도 답할 수 없는 칸이 하나 생긴다.
 *
 * <h2>부분 갱신이 아니다</h2>
 * 이 값은 그 방문지 기록의 <b>전체 상태</b>다. 도착만 담으면 출발은 {@code null} 이 되는 것이
 * 맞다 — 이 record 를 만드는 경로가 {@code PUT} 하나뿐인 이유이기도 하다.
 *
 * @param recordedBy 이 기록을 남긴 사람. 여행의 편집 권한자다
 * @param recordedAt 기록을 남긴 시각. 도착·출발 시각과 다르다 — 지나간 날짜의 방문지에
 *     뒤늦게 적을 수 있으므로({@code -293} 완료 기준) 이 값이 도착 시각보다 훨씬 늦을 수 있고,
 *     그것이 정상이다
 */
public record ItineraryItemActual(
        String itineraryId,
        String itemKey,
        Instant arrivedAt,
        Instant departedAt,
        String recordedBy,
        Instant recordedAt) {

    /**
     * 표의 CHECK 두 개와 <b>같은 판정을 여기서 먼저</b> 한다. DB 제약 위반은 500 으로 나가고
     * 어느 칸이 문제였는지 알려주지 못하는데, 이 자리에서 막으면 400 으로 칸 이름과 함께
     * 답할 수 있다({@code ck_itinerary_item_actual_has_value}·
     * {@code ck_itinerary_item_actual_order} 가 그 두 제약이다).
     *
     * <p>제약을 표에서 걷어내는 것이 아니다 — 표가 최종 방어선이고 이것은 진단 가능한
     * 첫 방어선이다.
     */
    public ItineraryItemActual {
        if (arrivedAt == null && departedAt == null) {
            throw new NoTimeGivenException();
        }
        if (arrivedAt != null && departedAt != null && departedAt.isBefore(arrivedAt)) {
            throw new DepartedBeforeArrivedException(arrivedAt, departedAt);
        }
    }

    /** 도착도 출발도 없다 — 아무 내용이 없는 기록은 만들지 않는다. */
    public static class NoTimeGivenException extends RuntimeException {

        public NoTimeGivenException() {
            super("도착 시각과 출발 시각 중 최소 하나는 보내야 합니다.");
        }
    }

    /** 출발이 도착보다 앞선다. 하나만 보내는 것은 허용하되 둘이 어긋나는 것은 막는다. */
    public static class DepartedBeforeArrivedException extends RuntimeException {

        private final Instant arrivedAt;

        private final Instant departedAt;

        public DepartedBeforeArrivedException(Instant arrivedAt, Instant departedAt) {
            super("출발 시각이 도착 시각보다 앞설 수 없습니다.");
            this.arrivedAt = arrivedAt;
            this.departedAt = departedAt;
        }

        public Instant arrivedAt() {
            return arrivedAt;
        }

        public Instant departedAt() {
            return departedAt;
        }
    }
}
