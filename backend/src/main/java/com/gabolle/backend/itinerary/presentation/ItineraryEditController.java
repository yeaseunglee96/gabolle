package com.gabolle.backend.itinerary.presentation;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.itinerary.application.ItineraryEditService;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.dto.LockItemRequest;

import jakarta.validation.Valid;

/**
 * 일정 편집 — API 명세 ITN-03.
 *
 * <p>🔴 <b>경로와 응답 코드를 발명하지 않았다.</b> 명세 3.5 가 이미 정의하고 있다.
 * 처음에 {@code POST /itineraries/{id}/edits} 를 만들려 했는데, 명세를 확인하니
 * ITN-01~09 가 이미 있었다. 서버가 임의로 경로를 정하면 FE·APP 이 못 붙인다.
 *
 * <p>ITN-03 을 먼저 만든 이유 — 응답이 <b>{@code 201 새 ItineraryVersion}</b> 으로
 * 동기다. ITN-06·07·08 은 {@code 202 JobDto} 라 비동기여서 409 를 즉시
 * 보여주기 어렵다. M1 완료 조건 ②("409 충돌이 실제로 재현된다")를 시연하려면
 * 동기 응답이 필요하다.
 *
 * <h2>🔴 2026-09-03 — 성공 응답을 손으로 만든 봉투에서 팀 공용 {@link ApiResponse} 로</h2>
 * {@code data}·{@code error}·{@code meta} 를 매번 {@code Map.of} 로 새로 짜고 있었다.
 * {@code ApiResponse} 가 이미 그 모양을 강제하므로 그것을 쓴다.
 *
 * <p>🔴 <b>409 충돌 응답({@link ItineraryExceptionHandler})은 그대로 뒀다.</b>
 * {@code ItineraryConflictResponse} 는 {@code details}(itineraryId·attemptedBaseVersion·
 * latestVersion)라는 <b>구조화된 객체</b>를 담아야 하는데, 팀 공용
 * {@code common.api.ApiError} 는 {@code fields: List<String>} 만 가진다 —
 * 문자열 목록으로 욱여넣으면 FE 가 {@code latestVersion} 을 파싱해서 써야 한다.
 * {@code ApiError} 자체를 확장하는 것은 그 계약을 만든 auth 팀(박재현, S15P21E201-312)
 * 소관이라 이번 범위에서 손대지 않았다. 이미 있는 "이 일정은 못 찾는다" 는 잘못된
 * 오류가 아니라 <b>당장은 두 표현이 공존한다</b> 는 사실이다.
 */
@RestController
@RequestMapping("/api/v1/itineraries/{itineraryId}")
public class ItineraryEditController {

    private final ItineraryEditService service;

    public ItineraryEditController(ItineraryEditService service) {
        this.service = service;
    }

    /**
     * 항목을 고정한다 (ITN-03).
     *
     * <p>🔴 {@code baseVersion} 이 최신이 아니면 {@code 409 ITINERARY_VERSION_CONFLICT} 다.
     * 그 변환은 {@link ItineraryExceptionHandler} 가 한다 — 도메인은 HTTP 를 모른다.
     *
     * <p>🔴 <b>아직 없는 것</b> — 객체 권한 검증(OWNER/EDITOR)이 빠져 있다.
     * 명세 2.1 이 "모든 itineraryId 에 대해 실제 소유·참여 관계를 다시 검증한다" 를
     * 요구하고 FR-SEC-01 도 같다. {@code trip_members} 표가 아직 없어서 못 한다.
     * 그전까지는 인증된 사용자면 통과한다 — <b>MR 에 이 사실을 적는다.</b>
     */
    @PostMapping("/items/{itemId}/lock")
    public ResponseEntity<ApiResponse<Map<String, Object>>> lockItem(
            @PathVariable String itineraryId,
            @PathVariable String itemId,
            @Valid @RequestBody LockItemRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        // 🔴 M1 임시 — 인증이 아직 없어서 헤더로 받는다. auth 패키지가 서면 교체한다.
        String editor = userId != null ? userId : "usr_unknown";

        ItineraryVersion saved = service.edit(
                itineraryId,
                request.baseVersion(),
                ItineraryVersion.Operation.LOCK_ITEM,
                editor,
                // 🔴 사용자 편집은 추천 요청에서 나온 것이 아니라 requestId 가 없다.
                //    그래도 API-07 이 연결을 요구하므로 편집마다 새로 만든다.
                "req_edit_" + java.util.UUID.randomUUID(),
                placeholderVersions());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dataOf(saved, itemId), saved.requestId()));
    }

    /**
     * 🔴 M1 임시 — 재계산을 아직 안 붙였으므로 버전 값이 없다.
     *
     * <p>{@code Versions.isComplete()} 가 false 인 값을 넣는다. 지어낸 값을 넣으면
     * 나중에 "이 일정은 어느 판으로 만들었나" 에 거짓으로 답하게 된다 —
     * S15P21E201-542 3장이 "버전 값을 얻지 못하면 임의의 기본값으로 처리하지 않는다" 고
     * 못 박았다.
     */
    private ItineraryVersion.Versions placeholderVersions() {
        return new ItineraryVersion.Versions(null, null, null, null, null);
    }

    /**
     * 응답 본문 — API 명세 2.1 공통 envelope 의 {@code data} 부분.
     *
     * <p>🔴 {@code createdAt} 을 {@code data} 안에 둔다. 예전 코드는 이 값을
     * {@code meta.timestamp} 에 뒀는데, 팀 공용 {@code ApiMeta} 는
     * {@code requestId} 한 칸뿐이다({@code common/api/ApiMeta.java}). 칸이 없다고
     * 정보를 버리지 않고 {@code data} 로 옮겼다 — {@code Trip}·{@code Auth} 응답도
     * 같은 이유로 시각을 {@code data} 안에 둔다.
     */
    private Map<String, Object> dataOf(ItineraryVersion v, String itemId) {
        return Map.of(
                "itineraryId", v.itineraryId(),
                "version", v.version(),
                "baseVersion", v.baseVersion(),
                "createdBy", v.createdBy(),
                "operation", v.operation().name(),
                "requestId", v.requestId(),
                "lockedItemId", itemId,
                "timezone", "Asia/Seoul",
                "createdAt", v.createdAt().toString(),
                "warnings", List.of());
    }
}
