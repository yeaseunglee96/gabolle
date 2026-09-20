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
 * 이벤트 사전 조회. 기대값을 하나씩 나열하지 않고 {@link EventType} 과 직접 대조한다 —
 * 나열하면 새 종류가 늘어도 이 테스트가 통과해서 사전이 오래된 것을 아무도 모른다.
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
            assertThat(entry.acceptedProducers())
                    .containsExactlyInAnyOrderElementsOf(
                            type.acceptedProducers().stream().map(Enum::name).toList());
            assertThat(entry.requiredForM1()).isEqualTo(type.requiredForM1());
            assertThat(entry.versionRequirement()).isEqualTo(type.versionRequirement().name());
            assertThat(entry.hasAggregateAxis()).isEqualTo(type.hasAggregateAxis());
        }
    }

    @Test
    void 저장_제외_방문은_클라이언트도_보낼_수_있다고_사전이_말한다() {
        // producer 만 보면 SERVER 라 "앱은 보낼 수 없다" 로 읽히지만 실제로는 둘 다 받는다.
        // 사전이 그 사실을 말하지 않으면 계측하는 쪽이 안 보낸다.
        for (EventType type : List.of(EventType.PLACE_LIKE, EventType.PLACE_DISLIKE, EventType.PLACE_VISIT)) {
            assertThat(EventCatalogEntry.from(type).acceptedProducers())
                    .as(type.name())
                    .containsExactlyInAnyOrder("CLIENT", "SERVER");
        }
    }

    @Test
    void 나머지_종류는_만들어야_하는_쪽만_보낼_수_있다() {
        assertThat(EventCatalogEntry.from(EventType.TRIP_CREATED).acceptedProducers())
                .containsExactly("SERVER");
        assertThat(EventCatalogEntry.from(EventType.RECOMMENDATION_IMPRESSION).acceptedProducers())
                .containsExactly("CLIENT");
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
