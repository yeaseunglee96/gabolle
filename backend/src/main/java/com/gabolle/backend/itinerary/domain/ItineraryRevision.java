package com.gabolle.backend.itinerary.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * 판 하나의 내용을 다음 판으로 옮기는 규칙.
 * 일정은 덮어쓰지 않는 판(version) 체인이고 {@link ItineraryItem}·{@link ItineraryLeg} 의
 * 부모가 {@code itineraryVersionId} 다. 그래서 편집은 기존 행을 고치는 것이 아니라 바탕 판의
 * 내용을 새 판으로 복사하면서 그 복사본에 편집을 적용하는 것이고, 그 복사 규칙이 이 클래스
 * 하나에만 있다. 서비스마다 자기 복사 코드를 들면 어느 하나가 칸 하나를 안 물려주는 날 그
 * 판만 조용히 값을 잃는다.
 * JPA·Spring 을 import 하지 않는다 — 순수 함수라 DB 없이 검증할 수 있다.
 * 무엇을 물려주고 무엇을 새로 만드는가:
 * - {@code itemKey}·{@code legId} 를 뺀 나머지 내용은 전부 그대로 물려준다.
 * - {@code itineraryItemId}·{@code itineraryLegId}(PK)는 판마다 새 행이므로 새로 만든다.
 * - {@code itemKey} 는 반드시 물려준다. 판을 건너 같은 항목을 가리키는 값이고 프론트의
 *   {@code item.id} 가 이 값이다. 여기서 새 키를 만들면 방금 고정한 항목을 다음 요청에서
 *   못 찾는데, {@code uq_itinerary_item_key} 는 같은 판 안의 중복만 막으므로 DB 가 못 잡는다.
 * - 항목·구간의 {@code createdAt} 은 새 시각이다. 행이 새로 난 시각이 맞고, 원본 시각을
 *   물려주면 행만 보고는 어느 판 것인지 알 수 없게 된다.
 * - {@link ItineraryExclusion 제외 항목}도 판마다 복사된다. PK 는 새로 만들고
 *   {@code itineraryVersionId} 는 새 판을 가리키게 바꾸지만, {@code createdAt} 은 항목·구간과
 *   달리 원본 그대로다 — 제외의 {@code createdAt} 은 "언제 제외했나" 를 묻지, "이 행이 언제
 *   생겼나" 를 묻지 않는다.
 */
public final class ItineraryRevision {

    private ItineraryRevision() {
    }

    /** 새 판의 항목·구간·제외 목록. {@link ItineraryRepository#appendVersion} 에 그대로 넘긴다. */
    public record Draft(List<ItineraryItem> items, List<ItineraryLeg> legs,
            List<ItineraryExclusion> exclusions) {

        public Draft {
            items = items == null ? List.of() : List.copyOf(items);
            legs = legs == null ? List.of() : List.copyOf(legs);
            exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
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
     * 되돌리기와, 편집이 항목 배치를 안 건드리는 경우가 이것을 쓴다.
     */
    public static Draft copyOf(ItineraryContent base, String newVersionId, Instant now) {
        return new Draft(copyItems(base.items(), newVersionId, now, null, false),
                copyLegs(base.legs(), newVersionId, now),
                copyExclusions(base.exclusions(), newVersionId));
    }

    /**
     * 항목 하나의 고정 여부만 바꾼 복사본을 만든다.
     * 고정은 "재계산이 이 장소를 빼지 마라" 는 표시다. 순서·시각은 그대로 두므로 구간
     * ({@link ItineraryLeg})도 손대지 않는다.
     * 이미 같은 값이어도 새 판을 만든다. 모든 편집이 새 판을 만들어야 하고, 클라이언트가
     * {@code baseVersion} 을 실어 보내는 이상 "아무 일도 없었다" 로 답하면 그쪽 화면의 판 번호가
     * 서버와 어긋난 채 남는다.
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
                copyLegs(base.legs(), newVersionId, now),
                copyExclusions(base.exclusions(), newVersionId));
    }

    /**
     * 항목 하나를 그 날의 마지막에 더한 복사본을 만든다. 나머지 항목은 하나도 바뀌지 않는다.
     * 더한 항목을 고정(locked)해서 넣는다. 이 편집 뒤에는 그 날짜 재계산 Job 이 따라오는데,
     * 재계산은 그 날을 다시 채우면서 항목을 뺄 수 있다. 사용자가 방금 명시적으로 고른 장소가
     * 그때 사라지면 "넣었는데 없어졌다" 가 된다. 사용자가 나중에 직접 빼는 것은 된다.
     * 시각을 지어내지 않는다 — {@code startTime}·{@code endTime}·{@code stayMinutes} 를
     * {@code null} 로 두고 {@code dataStatus} 를 {@code UNKNOWN} 으로 넣는다. 앞 항목의 끝에
     * 이어 붙이면 이동 시간을 모르는 채 만든 시각이 화면에 확정된 값처럼 보인다.
     * 이동 구간({@link ItineraryLeg})도 같은 이유로 이 자리에서 만들지 않는다.
     *
     * @param visitDate 그 날의 날짜. 그 날에 항목이 하나도 없을 수 있어(빈 날에 축제를 넣는
     *     경우) 바탕 판에서 유도할 수 없다. 호출자가 여행 시작일과 {@code dayIndex} 로 계산해 준다
     * @throws DayIndexOutOfRangeException {@code dayIndex} 가 음수다
     */
    public static Draft withAddedItem(ItineraryContent base, String newVersionId, String placeId,
                                      int dayIndex, LocalDate visitDate, Instant now) {

        if (dayIndex < 0) {
            throw new DayIndexOutOfRangeException(dayIndex);
        }
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("더할 장소가 없습니다: placeId=" + placeId);
        }

        List<ItineraryItem> copied = copyItems(base.items(), newVersionId, now, null, false);

        // sequence 는 그 날 안에서만 1부터 센다(ck_itinerary_item_sequence). 다른 날의 항목을 세면
        //    새 항목이 엉뚱한 순번을 갖고, UNIQUE 제약이 있는 날에는 충돌한다.
        int nextSequence = copied.stream()
                .filter((item) -> item.dayIndex() == dayIndex)
                .mapToInt(ItineraryItem::sequence)
                .max()
                .orElse(0) + 1;

        List<ItineraryItem> withAdded = new ArrayList<>(copied);
        withAdded.add(new ItineraryItem(
                UUID.randomUUID().toString(),
                newVersionId,
                // 새 항목이므로 item_key 도 새로 만든다. 이 값이 프론트가 판을 건너 이 항목을 가리키는 이름이 된다
                UUID.randomUUID().toString(),
                dayIndex,
                visitDate,
                nextSequence,
                placeId,
                null,
                null,
                null,
                true,
                null,
                ItineraryItem.DataStatus.UNKNOWN,
                List.of("USER_ADDED"),
                List.of(),
                null,
                now));

        return new Draft(withAdded, copyLegs(base.legs(), newVersionId, now),
                copyExclusions(base.exclusions(), newVersionId));
    }

    /**
     * 하루 안의 방문 순서를 통째로 바꾼다.
     * 시각은 다시 계산하지 않고 자리만 바꿔 앉힌다. 그날의 시각표(시작·종료·머무는 시간)를
     * 순서대로 뽑아 두고 새 순서의 항목들에 그대로 다시 나눠 준다 — 하루의 시간표 모양은
     * 그대로이고 누가 어느 자리에 앉는지만 바뀐다.
     * 다시 나눠 계산하지 않는 이유가 둘이다. 항목 수가 안 변하므로 다시 계산해도 같은 값이
     * 나오고(생성기가 그날 시간대를 항목 수로 나눠 쓴다), 그 규칙을 여기 옮겨 적으면 같은
     * 규칙이 두 곳에 살아 나중에 한쪽만 바뀌면 "순서만 바꿨는데 시각이 달라졌다" 가 된다.
     * 시각이 없는 항목({@code null})은 없는 채로 옮겨진다.
     * 그날의 구간은 버린다. 구간은 "A 에서 B 로 갈 때 몇 미터" 라는 주장이고 순서가 바뀌면 그
     * 주장이 참이 아니다 — 그대로 두면 화면이 엉뚱한 항목에 남의 거리를 붙여 그린다. 다시
     * 계산하려면 좌표로 경로를 물어야 하고 그것은 이 자리가 아니다. 다른 날 구간은 안 건드린다.
     *
     * @param dayOrder 그날 항목의 {@code itemKey} 를 원하는 순서대로 전부
     * @throws DayOrderMismatchException 목록이 그날의 항목 전부와 정확히 일치하지 않는다
     * @throws LockedItemMovedException 고정된 항목의 자리가 바뀐다
     */
    public static Draft withReorderedDay(ItineraryContent base, String newVersionId, int dayIndex,
                                         List<String> dayOrder, Instant now) {

        if (dayIndex < 0) {
            throw new DayIndexOutOfRangeException(dayIndex);
        }

        List<ItineraryItem> dayItems = base.items().stream()
                .filter((item) -> item.dayIndex() == dayIndex)
                .sorted(Comparator.comparingInt(ItineraryItem::sequence))
                .toList();

        requireSameSet(dayItems, dayOrder, dayIndex);

        Map<String, ItineraryItem> byKey = dayItems.stream()
                .collect(Collectors.toMap(ItineraryItem::itemKey, (item) -> item));

        // 고정된 항목은 자리가 바뀌면 안 된다. 사용자가 "여기 그대로 두라" 고 못 박은 것을
        //    끌어 옮기기 한 번으로 조용히 옮기면, 고정이라는 약속이 무의미해진다.
        for (int i = 0; i < dayOrder.size(); i++) {
            ItineraryItem moved = byKey.get(dayOrder.get(i));
            if (moved.locked() && dayItems.get(i) != moved) {
                throw new LockedItemMovedException(moved.itemKey(), dayIndex);
            }
        }

        List<ItineraryItem> reordered = new ArrayList<>();
        for (int i = 0; i < dayOrder.size(); i++) {
            ItineraryItem moved = byKey.get(dayOrder.get(i));
            // 그 자리에 원래 앉아 있던 항목의 시각을 그대로 물려받는다.
            ItineraryItem seat = dayItems.get(i);
            reordered.add(new ItineraryItem(
                    UUID.randomUUID().toString(),
                    newVersionId,
                    moved.itemKey(),
                    moved.dayIndex(),
                    moved.visitDate(),
                    i + 1,
                    moved.placeId(),
                    seat.startTime(),
                    seat.endTime(),
                    seat.stayMinutes(),
                    moved.locked(),
                    moved.estimatedCostKrw(),
                    seat.dataStatus(),
                    moved.reasonCodes(),
                    moved.warningCodes(),
                    moved.sourceRequestId(),
                    now));
        }

        List<ItineraryItem> others = copyItems(base.items(), newVersionId, now, null, false).stream()
                .filter((item) -> item.dayIndex() != dayIndex)
                .toList();

        List<ItineraryItem> all = new ArrayList<>(others);
        all.addAll(reordered);

        List<ItineraryLeg> keptLegs = copyLegs(base.legs(), newVersionId, now).stream()
                .filter((leg) -> leg.dayIndex() != dayIndex)
                .toList();

        return new Draft(all, keptLegs, copyExclusions(base.exclusions(), newVersionId));
    }

    /**
     * 남은 하루를 다시 계획한다.
     * {@link #withReorderedDay} 와 짝을 이루지만 구간 처리가 정반대다. 재계획은 자리를 하나도
     * 안 바꾸므로 구간이 여전히 참이다 — 시간표만 밀렸다고 두 장소 사이의 거리가 달라지지
     * 않는다. 그래서 {@code base.legs()} 를 {@link #copyLegs} 로 그대로 옮긴다.
     * {@code newStartTimeByItemKey} 에 없는 항목은 시각을 포함해 그대로 복사된다 — 다른 날의
     * 항목과, 같은 날이라도 이미 지나간 방문지가 그렇다. 순번·장소·{@code itemKey}·고정 여부는
     * 지도에 있는 항목이라도 손대지 않는다. 재계획은 시각만 다시 매기는 일이다.
     *
     * @param newStartTimeByItemKey 새로 매길 시작 시각. 지도에 없으면 기존 값을 그대로 둔다
     * @param newEndTimeByItemKey 새로 매길 종료 시각. 위와 같다
     * @throws DayIndexOutOfRangeException {@code dayIndex} 가 음수다
     */
    public static Draft withReplannedDay(ItineraryContent base, String newVersionId, int dayIndex,
            Map<String, LocalTime> newStartTimeByItemKey, Map<String, LocalTime> newEndTimeByItemKey,
            Instant now) {

        if (dayIndex < 0) {
            throw new DayIndexOutOfRangeException(dayIndex);
        }

        List<ItineraryItem> copied = copyItems(base.items(), newVersionId, now, null, false);

        List<ItineraryItem> replanned = new ArrayList<>(copied.size());
        for (ItineraryItem item : copied) {
            if (item.dayIndex() != dayIndex || !newStartTimeByItemKey.containsKey(item.itemKey())) {
                replanned.add(item);
                continue;
            }

            LocalTime newStart = newStartTimeByItemKey.get(item.itemKey());
            LocalTime newEnd = newEndTimeByItemKey.get(item.itemKey());
            // 시작·종료로 머무는 시간을 다시 잰다 — 별도의 stayMinutes 지도를 받지 않는다.
            Integer newStayMinutes = (newStart != null && newEnd != null)
                    ? (int) Duration.between(newStart, newEnd).toMinutes()
                    : item.stayMinutes();

            replanned.add(new ItineraryItem(
                    item.itineraryItemId(),
                    item.itineraryVersionId(),
                    item.itemKey(),
                    item.dayIndex(),
                    item.visitDate(),
                    item.sequence(),
                    item.placeId(),
                    newStart,
                    newEnd,
                    newStayMinutes,
                    item.locked(),
                    item.estimatedCostKrw(),
                    item.dataStatus(),
                    item.reasonCodes(),
                    item.warningCodes(),
                    item.sourceRequestId(),
                    now));
        }

        return new Draft(replanned, copyLegs(base.legs(), newVersionId, now),
                copyExclusions(base.exclusions(), newVersionId));
    }

    private static void requireSameSet(List<ItineraryItem> dayItems, List<String> dayOrder, int dayIndex) {
        Set<String> existing = dayItems.stream().map(ItineraryItem::itemKey).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> requested = new LinkedHashSet<>(dayOrder);

        if (requested.size() != dayOrder.size()) {
            throw new DayOrderMismatchException(dayIndex, "같은 항목이 두 번 들어 있습니다.");
        }
        if (!existing.equals(requested)) {
            // 무엇이 어긋났는지 알려 준다. "잘못된 요청" 만 돌려주면 화면이 자기 목록이 낡은 것인지
            //    항목 하나를 빠뜨린 것인지 구분할 수 없다.
            Set<String> missing = new LinkedHashSet<>(existing);
            missing.removeAll(requested);
            Set<String> unknown = new LinkedHashSet<>(requested);
            unknown.removeAll(existing);
            throw new DayOrderMismatchException(dayIndex,
                    "빠진 항목 " + missing.size() + "개, 모르는 항목 " + unknown.size() + "개");
        }
    }

    /** 보낸 순서가 그날의 항목 전부와 일치하지 않는다 — 400. */
    public static class DayOrderMismatchException extends RuntimeException {

        private final int dayIndex;

        public DayOrderMismatchException(int dayIndex, String detail) {
            super("그날의 항목 전부를 순서대로 보내야 합니다: dayIndex=" + dayIndex + ", " + detail);
            this.dayIndex = dayIndex;
        }

        public int dayIndex() {
            return this.dayIndex;
        }
    }

    /** 고정된 항목의 자리가 바뀌려 한다 — 409. */
    public static class LockedItemMovedException extends RuntimeException {

        private final String itemKey;

        private final int dayIndex;

        public LockedItemMovedException(String itemKey, int dayIndex) {
            super("고정된 방문지는 자리를 옮길 수 없습니다: itemKey=" + itemKey + ", dayIndex=" + dayIndex);
            this.itemKey = itemKey;
            this.dayIndex = dayIndex;
        }

        public String itemKey() { return this.itemKey; }
        public int dayIndex()   { return this.dayIndex; }
    }

    /** {@code dayIndex} 가 음수다 — 400. */
    public static class DayIndexOutOfRangeException extends RuntimeException {

        private final int dayIndex;

        public DayIndexOutOfRangeException(int dayIndex) {
            super("일정의 몇째 날인지가 올바르지 않습니다: dayIndex=" + dayIndex);
            this.dayIndex = dayIndex;
        }

        public int dayIndex() {
            return this.dayIndex;
        }
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
                    // 물려준다. 이 한 줄이 프론트의 item.id 가 판을 건너 살아남는 이유다.
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

    /**
     * {@code createdAt} 은 원본 그대로 물려준다. {@link #copyItems}·{@link #copyLegs} 와 달리
     * {@code now} 를 받지 않는 이유가 그것이다 — "언제 그 장소를 뺐는가" 는 판을 복사한 지금
     * 다시 일어난 사건이 아니라 사실 그 자체다. 새로 만드는 것은 PK 와 이 제외가 속한 판
     * ({@code itineraryVersionId})뿐이다.
     */
    private static List<ItineraryExclusion> copyExclusions(List<ItineraryExclusion> exclusions,
            String newVersionId) {
        List<ItineraryExclusion> copied = new ArrayList<>(exclusions.size());
        for (ItineraryExclusion exclusion : exclusions) {
            copied.add(new ItineraryExclusion(
                    UUID.randomUUID().toString(),
                    newVersionId,
                    exclusion.placeId(),
                    exclusion.itemKey(),
                    exclusion.excludedBy(),
                    exclusion.reasonCode(),
                    exclusion.operationalReason(),
                    exclusion.createdAt()));
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
                    // dataStatus 를 반드시 넘긴다. 이 칸이 빠지면 구간이 하나도 안 바뀐 편집(고정·해제·장소
                    // 추가·되돌리기)에서도 "이 값이 잰 것인가 어림한 것인가" 가 조용히 지워지고, 화면이
                    // 어림값을 잰 값처럼 그린다. 복사는 값을 옮기는 일이지 지우는 일이 아니다.
                    leg.dataStatus(),
                    // 요금·선형도 넘긴다 (S15P21E201-1706). 전에는 빠져서 고정·장소 추가·남은 하루 재계획·되돌리기
                    // 뒤의 판은 같은 구간인데도 지도 길 선이 점선이 되고 교통비 줄이 사라졌다. 여기는 구간 하나를 같은
                    // 출발 곳·도착 곳·수단 그대로 옮기는 자리라 그 길과 요금이 여전히 참이다. 쌍이 바뀌는 편집(빼기·
                    // 하루 다시 짜기·그날 순서 바꾸기)은 이 복사를 안 쓰고 구간을 새로 잰다 — 다른 쌍의 길을 옮길 일이 없다.
                    leg.fareKrw(), leg.path(), leg.pieces(),
                    // 보정 전 이동 분은 반드시 넘긴다 — 흘리면 보정 계산이 고친 값을 어림으로 읽어 배율이 겹쳐
                    // 곱해진다 (S15P21E201-1700).
                    leg.uncalibratedDurationMin(),
                    now));
        }
        return copied;
    }
}
