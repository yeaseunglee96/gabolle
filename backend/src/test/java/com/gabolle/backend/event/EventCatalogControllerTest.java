package com.gabolle.backend.event;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.presentation.EventCatalogController;
import com.gabolle.backend.event.presentation.dto.EventCatalogEntry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이벤트 사전 조회 — S15P21E201-352 작업 내용 4번의 마지막 조각.
 *
 * <p>🔴 이 조회가 {@link EventType} 과 따로 노는 값을 낼 수 없게, "코드가 아는 것과
 * 응답이 항상 같다" 를 직접 대조한다. 하나씩 나열하지 않는다 — 나열하면 새 종류가
 * 추가될 때 이 테스트를 안 고쳐도 통과해서, 사전이 오래돼도 아무도 모른다.
 */
class EventCatalogControllerTest {

    private final EventCatalogController controller = new EventCatalogController();

    @Test
    void 사전에_이벤트_종류가_하나도_안_빠진다() {
        ApiResponse<List<EventCatalogEntry>> response = this.controller.catalog(null);

        assertThat(response.data()).hasSize(EventType.values().length);
        assertThat(response.data().stream().map(EventCatalogEntry::eventType))
                .containsExactlyInAnyOrderElementsOf(
                        List.of(EventType.values()).stream().map(EventType::wireName).toList());
    }

    @Test
    void 각_항목이_EventType_이_아는_값과_정확히_같다() {
        Map<String, EventCatalogEntry> byName = this.controller.catalog(null).data().stream()
                .collect(java.util.stream.Collectors.toMap(EventCatalogEntry::eventType, e -> e));

        for (EventType type : EventType.values()) {
            EventCatalogEntry entry = byName.get(type.wireName());
            assertThat(entry).as(type.name()).isNotNull();
            assertThat(entry.producer()).isEqualTo(type.expectedProducer().name());
            assertThat(entry.requiredForM1()).isEqualTo(type.requiredForM1());
            assertThat(entry.versionRequirement()).isEqualTo(type.versionRequirement().name());
            assertThat(entry.hasAggregateAxis()).isEqualTo(type.hasAggregateAxis());
        }
    }

    @Test
    void 축이_안_정해진_종류는_aggregateType_이_null_이다() {
        EventCatalogEntry entry = EventCatalogEntry.from(EventType.EDITORIAL_PICK_PUBLISHED);

        assertThat(entry.hasAggregateAxis()).isFalse();
        assertThat(entry.aggregateType()).isNull();
    }

    @Test
    void 축이_정해진_종류는_aggregateType_을_그대로_담는다() {
        EventCatalogEntry entry = EventCatalogEntry.from(EventType.RECOMMENDATION_IMPRESSION);

        assertThat(entry.hasAggregateAxis()).isTrue();
        assertThat(entry.aggregateType()).isEqualTo("recommendation");
    }

    @Test
    void requestId_를_안_주면_새로_만든다() {
        ApiResponse<List<EventCatalogEntry>> response = this.controller.catalog(null);

        assertThat(response.meta().requestId()).startsWith("req_");
    }

    @Test
    void requestId_헤더를_주면_그대로_돌려준다() {
        ApiResponse<List<EventCatalogEntry>> response = this.controller.catalog("caller-supplied-id");

        assertThat(response.meta().requestId()).isEqualTo("caller-supplied-id");
    }
}
