package com.gabolle.backend.itinerary.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 판 하나의 내용을 다음 판으로 옮기는 규칙 — S15P21E201-662.
 *
 * <p>일정은 덮어쓰지 않는 판(version) 체인이고, {@link ItineraryItem}·{@link ItineraryLeg}
 * 의 부모가 {@code itineraryId} 가 아니라 {@code itineraryVersionId} 다. 그래서 편집은
 * "기존 행을 고치는 것" 이 아니라 <b>바탕 판의 내용을 새 판으로 복사하면서 그 복사본에
 * 편집을 적용하는 것</b>이다. 그 복사 규칙이 이 클래스 하나에만 있다.
 *
 * <p>왜 한 곳에 모으는가: 고정·해제·제외·순서 변경·되돌리기가 전부 이 위에 선다. 각
 * 서비스가 자기 복사 코드를 들고 있으면 어느 하나가 칸 하나를 안 물려주는 날 그 판만
 * 조용히 값을 잃는다. 여기서 안 물려주면 모든 편집이 함께 잃으므로 테스트가 잡는다.
 *
 * <p>🔴 이 클래스는 JPA·Spring 을 import 하지 않는다 — {@link ItineraryRepository} 와 같은
 * 이유다. 순수 함수라 DB 없이 검증할 수 있다.
 *
 * <h2>🔴 무엇을 물려주고 무엇을 새로 만드는가</h2>
 * <ul>
 *   <li>{@code itemKey}·{@code legId} 를 뺀 나머지 <b>내용은 전부 그대로</b> — 장소, 날짜,
 *       순번, 시각, 고정 여부, 비용, 등급, 사유·경고 코드, 출처 요청</li>
 *   <li>{@code itineraryItemId}·{@code itineraryLegId}(PK)는 <b>새로 만든다.</b> 판마다
 *       새 행이므로 PK 도 새 값이다</li>
 *   <li>{@code itemKey} 는 <b>반드시 물려준다.</b> 판을 건너 같은 항목을 가리키는 값이고,
 *       프론트가 화면에서 쓰는 {@code item.id} 가 이 값이다. 여기서 새 키를 만들면 사용자가
 *       방금 고정한 항목을 다음 요청에서 못 찾는다 — 그런데 {@code uq_itinerary_item_key}
 *       는 <b>같은 판 안의 중복만</b> 막으므로 이 실수를 DB 가 못 잡는다. 이 자리를 지키는
 *       것은 테스트뿐이다</li>
 *   <li>{@code createdAt} 은 <b>새 시각</b>이다. 행이 새로 난 시각이 맞고, "이 항목이 원래
 *       언제 생겼나" 는 옛 판의 행에 그대로 남아 있으므로 잃는 정보가 없다. 원본 시각을
 *       물려주면 {@code created_at} 이 판의 나이와 어긋나 행만 보고는 어느 판 것인지
 *       알 수 없게 된다</li>
 * </ul>
 */
public final class ItineraryRevision {

    private ItineraryRevision() {
    }

    /** 새 판의 항목·구간. {@link ItineraryRepository#appendVersion} 에 그대로 넘긴다. */
    public record Draft(List<ItineraryItem> items, List<ItineraryLeg> legs) {

        public Draft {
            items = items == null ? List.of() : List.copyOf(items);
            legs = legs == null ? List.of() : List.copyOf(legs);
        }
    }

    /** 편집 대상 {@code itemKey} 가 바탕 판에 없다 — 404 로 답할 자리다. */
    public static class ItemNotFoundException extends RuntimeException {

        private final String itemKey;

        public ItemNotFoundException(String itemKey) {
            super("이 판에 그 항목이 없습니다: itemKey=" + itemKey);
            this.itemKey = itemKey;
        }

        public String itemKey() {
            return itemKey;
        }
    }

    /**
     * 바탕 판을 통째로 다음 판으로 복사한다. 내용은 하나도 바뀌지 않는다.
     *
     * <p>되돌리기와, 편집이 항목 배치를 안 건드리는 경우가 이것을 쓴다.
     */
    public static Draft copyOf(ItineraryContent base, String newVersionId, Instant now) {
        return new Draft(copyItems(base.items(), newVersionId, now, null, false),
                copyLegs(base.legs(), newVersionId, now));
    }

    /**
     * 항목 하나의 고정 여부만 바꾼 복사본을 만든다 — 명세 ITN-03·ITN-04.
     *
     * <p>🔴 고정은 "재계산이 이 장소를 빼지 마라" 는 표시다. 순서·시각은 그대로 두므로
     * 구간({@link ItineraryLeg})도 손대지 않는다.
     *
     * <p>이미 같은 값이어도 새 판을 만든다. FR-ITN-08 이 "모든 편집이 새 판을 만든다" 를
     * 요구하고, 클라이언트가 {@code baseVersion} 을 실어 보내는 이상 "아무 일도 없었다"
     * 로 답하면 그쪽 화면의 판 번호가 서버와 어긋난 채 남는다.
     *
     * @throws ItemNotFoundException 바탕 판에 그 {@code itemKey} 가 없다
     */
    public static Draft setLocked(ItineraryContent base, String newVersionId,
                                  String itemKey, boolean locked, Instant now) {

        if (itemKey == null || itemKey.isBlank()) {
            throw new ItemNotFoundException(itemKey);
        }
        boolean found = base.items().stream().anyMatch((item) -> itemKey.equals(item.itemKey()));
        if (!found) {
            throw new ItemNotFoundException(itemKey);
        }
        return new Draft(copyItems(base.items(), newVersionId, now, itemKey, locked),
                copyLegs(base.legs(), newVersionId, now));
    }

    /**
     * @param lockedItemKey {@code null} 이면 고정 여부를 그대로 물려준다. 값이 있으면 그
     *     항목만 {@code locked} 로 바꾼다
     */
    private static List<ItineraryItem> copyItems(List<ItineraryItem> items, String newVersionId,
                                                 Instant now, String lockedItemKey, boolean locked) {

        List<ItineraryItem> copied = new ArrayList<>(items.size());
        for (ItineraryItem item : items) {
            boolean nextLocked = item.itemKey().equals(lockedItemKey) ? locked : item.locked();
            copied.add(new ItineraryItem(
                    UUID.randomUUID().toString(),
                    newVersionId,
                    // 🔴 물려준다. 이 한 줄이 프론트의 item.id 가 판을 건너 살아남는 이유다.
                    item.itemKey(),
                    item.dayIndex(),
                    item.visitDate(),
                    item.sequence(),
                    item.placeId(),
                    item.startTime(),
                    item.endTime(),
                    item.stayMinutes(),
                    nextLocked,
                    item.estimatedCostKrw(),
                    item.dataStatus(),
                    item.reasonCodes(),
                    item.warningCodes(),
                    item.sourceRequestId(),
                    now));
        }
        return copied;
    }

    private static List<ItineraryLeg> copyLegs(List<ItineraryLeg> legs, String newVersionId, Instant now) {
        List<ItineraryLeg> copied = new ArrayList<>(legs.size());
        for (ItineraryLeg leg : legs) {
            copied.add(new ItineraryLeg(
                    UUID.randomUUID().toString(),
                    newVersionId,
                    leg.dayIndex(),
                    leg.sequence(),
                    leg.fromPlaceId(),
                    leg.toPlaceId(),
                    leg.travelMode(),
                    leg.distanceM(),
                    leg.durationMin(),
                    leg.walkingMeters(),
                    leg.ascentM(),
                    leg.stairSteps(),
                    now));
        }
        return copied;
    }
}
