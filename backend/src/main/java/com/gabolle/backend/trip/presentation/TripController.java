package com.gabolle.backend.trip.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.application.TripDeletionService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequest;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequestMapper;
import com.gabolle.backend.trip.presentation.dto.TripDetailResponse;
import com.gabolle.backend.trip.presentation.dto.TripDto;
import com.gabolle.backend.trip.presentation.dto.TripSummaryResponse;

import jakarta.validation.Valid;

/**
 * 여행 생성·조회.
 *
 * <p>생성 요청은 일정을 계산하지 않는다. 조건만 저장하고 즉시 응답하며, 계산은 별도 호출이
 * {@code 202 JobDto} 로 시작한다.
 */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripCreationService creationService;
    private final TripQueryService queryService;
    private final TripDeletionService deletionService;

    public TripController(TripCreationService creationService, TripQueryService queryService,
            TripDeletionService deletionService) {
        this.creationService = creationService;
        this.queryService = queryService;
        this.deletionService = deletionService;
    }

    /**
     * 여행 조건을 저장한다. 같은 {@code Idempotency-Key} 에 같은 본문이면 기존 여행을 그대로
     * 돌려준다 — 응답이 끊긴 앱은 재시도하고, 그때 여행이 두 개 생기면 안 된다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TripDto>> create(
            @Valid @RequestBody CreateTripRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {

        String requestId = "req_" + UUID.randomUUID();
        // 여기만 익명 세션을 허용한다. 만든 사람이 곧 소유자가 되는 자리라 회원 전용
        // 자원이 뚫리지 않는다 — list·get·delete 는 여전히 requireId 다.
        AuthenticatedUsers.Owner owner = AuthenticatedUsers.requireOwner(authentication);
        String creator = owner.id().toString();
        Trip.OwnerType ownerType = owner.anonymous() ? Trip.OwnerType.ANONYMOUS : Trip.OwnerType.USER;

        TripCreationService.Result result =
                creationService.create(toCommand(request, creator, ownerType), idempotencyKey);

        // 재시도였으면 200, 새로 만들었으면 201. 둘 다 성공이다 — 재시도에 4xx 를
        // 주면 사용자 화면에 오류가 뜬다.
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status)
                .body(ApiResponse.success(TripDto.of(result.trip(), result.snapshot()), requestId));
    }

    /**
     * 내 여행 목록. 내가 만든 것과 초대받은 것을 함께 준다 — 소유자만 주면 초대받은 사람은
     * 초대 링크를 다시 받는 것 말고 그 여행에 들어갈 길이 없다.
     *
     * <p>일정 자체는 여기서 주지 않는다. 여행 하나를 열 때
     * {@code GET /api/v1/itineraries?tripId=...} 로 따로 묻는다.
     *
     * <p>🔴 <b>표지만 예외다 (S15P21E201-1370).</b> 여기에는 오래 「일정은 함께 읽지
     * 않는다 — 줄마다 최신 일정을 붙이면 여행 모듈이 일정 표를 알게 된다」고 적혀 있었다.
     * 막으려던 것 둘 중 <b>하나는 지금도 유효하고 하나는 해결됐다.</b>
     * <ul>
     * <li><b>여행이 일정 표를 알게 되는 것</b> — 여전히 막는다. 그래서 여행은 일정 표를
     *     모른 채 {@code TripCoverPort} 에 표지만 묻고, 그 표를 아는 구현은 일정 쪽에 있다</li>
     * <li><b>줄마다 부르는 것</b> — 포트가 여행 목록을 통째로 받아 <b>질의 한 번</b>으로
     *     답한다. 여행이 50개든 질의는 하나다</li>
     * </ul>
     * 표지를 줄마다 부르는 방식으로 되돌리는 변경은 옛 문장이 막으려던 바로 그것이다.
     */
    @GetMapping
    public ApiResponse<List<TripSummaryResponse>> list(Authentication authentication) {
        String requester = AuthenticatedUsers.requireId(authentication).toString();

        List<TripSummaryResponse> trips =
                queryService.listWithCovers(requester, TripQueryService.MAX_LIST_SIZE).stream()
                        .map((listing) -> TripSummaryResponse.of(listing.row(), listing.cover()))
                        .toList();

        return ApiResponse.success(trips, "req_" + UUID.randomUUID());
    }

    /**
     * 여행 한 건을 조회한다. 없는 여행이거나 요청자가 그 여행의 회원이 아니면 둘 다 404 다 —
     * 403 으로 답하면 여행의 존재 자체가 샌다.
     */
    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> get(
            @PathVariable String tripId,
            Authentication authentication) {

        String requester = AuthenticatedUsers.requireId(authentication).toString();
        TripQueryService.View view = queryService.get(tripId, requester);

        return ApiResponse.success(
                TripDetailResponse.of(view.trip(), view.constraints(), view.snapshot()),
                "req_" + UUID.randomUUID());
    }

    /**
     * 여행을 지운다. 행은 지우지 않고 지운 시각만 찍는다 — 일정·기록·공유 링크가 이 여행을
     * 가리키고 있다. 그 순간부터 조회·편집·초대·공유가 전부 404 로 답한다.
     *
     * <p>이미 지워져 있어도 204 다. 두 번 누르는 것은 정상적으로 일어나는 일이라, 그때
     * 오류를 띄우면 사용자는 지워지지 않았다고 믿는다.
     */
    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> delete(
            @PathVariable String tripId,
            Authentication authentication) {

        String requester = AuthenticatedUsers.requireId(authentication).toString();
        deletionService.delete(tripId, requester);

        return ResponseEntity.noContent().build();
    }

    /**
     * DTO → 명령 번역은 {@link CreateTripRequestMapper} 에 있다 — 공유 일정 복제가 같은 본문을
     * 받아 같은 규칙(차원 이름 정규화 포함)으로 여행을 만들어야 해서 한 자리에 둔다.
     */
    private TripCreationService.Command toCommand(CreateTripRequest r, String userId) {
        return CreateTripRequestMapper.toCommand(r, userId);
    }

    private TripCreationService.Command toCommand(CreateTripRequest r, String userId, Trip.OwnerType ownerType) {
        return CreateTripRequestMapper.toCommand(r, userId, ownerType);
    }
}
