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
 * 여행 생성·조회 — S15P21E201-461 · TRIP-01.
 *
 * <p>🔴 경로와 응답 코드를 발명하지 않았다. API 명세 3.4 가 이미 정의한다 —
 * {@code POST /trips} → {@code 201 TripDto + preferenceSnapshot}.
 *
 * <p>🔴 <b>생성 요청은 일정을 계산하지 않는다.</b> 조건만 저장하고 즉시 응답한다.
 * 계산은 별도 호출({@code REC-01})이 {@code 202 JobDto} 로 시작한다.
 * 티켓 본문이 두 가지를 한 문장으로 써서 하나로 착각하기 쉬운 자리다.
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
     * 여행 조건을 저장한다.
     *
     * <p>🔴 {@code Idempotency-Key} 헤더를 받는다(API-09). 같은 키에 같은 본문이면
     * <b>기존 여행을 그대로 돌려준다</b> — 지하철에서 응답이 끊긴 앱은 반드시
     * 재시도하고, 그때 여행이 두 개 생기면 안 된다.
     *
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TripDto>> create(
            @Valid @RequestBody CreateTripRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {

        String requestId = "req_" + UUID.randomUUID();
        // 🔴 S15P21E201-317 — 여기만 익명 세션을 허용한다. 만든 사람이 곧 소유자가 되는
        //    자리라 "익명이면 못 만든다" 를 없애도 회원 전용 자원이 뚫리지 않는다 — 아래
        //    다른 메서드(list·get·delete)는 여전히 requireId 라 회원만 접근한다.
        AuthenticatedUsers.Owner owner = AuthenticatedUsers.requireOwner(authentication);
        String creator = owner.id().toString();
        Trip.OwnerType ownerType = owner.anonymous() ? Trip.OwnerType.ANONYMOUS : Trip.OwnerType.USER;

        TripCreationService.Result result =
                creationService.create(toCommand(request, creator, ownerType), idempotencyKey);

        // 🔴 재시도였으면 200, 새로 만들었으면 201. 둘 다 성공이다 —
        //    재시도에 4xx 를 주면 사용자 화면에 오류가 뜬다.
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status)
                .body(ApiResponse.success(TripDto.of(result.trip(), result.snapshot()), requestId));
    }

    /**
     * 내 여행 목록 — S15P21E201-738.
     *
     * <p>사용자가 "여행 만들기는 되는데 내 여행으로 안 들어가진다" 고 제보해서 만들었다.
     * 원인은 화면이 아니라 이 자리가 비어 있던 것이다 — 목록 기능이 없으니 앱이 목록을
     * 기기에 따로 적어 두고 있었고, 앱을 지웠다 깔거나 다른 기기로 로그인하면 여행이
     * 하나도 없었다.
     *
     * <p>🔴 <b>내가 만든 것과 초대받은 것을 함께 준다.</b> 소유자만 주면 초대받은 사람은
     * 그 여행에 들어갈 경로가 아예 없다 — 초대 링크를 다시 받는 것 말고는 방법이 없어진다.
     *
     * <p>🔴 <b>여기서 일정을 함께 읽지 않는다.</b> 목록 한 줄마다 그 여행의 최신 일정을
     * 붙이면 여행 모듈이 일정 표를 알게 된다. 일정 식별자는 사용자가 여행 하나를 눌렀을 때
     * {@code GET /api/v1/itineraries?tripId=...} 로 그 모듈에 묻는다 — 목록을 그릴 때가
     * 아니라 열 때 한 번이므로 요청 수도 늘지 않는다.
     */
    @GetMapping
    public ApiResponse<List<TripSummaryResponse>> list(Authentication authentication) {
        String requester = AuthenticatedUsers.requireId(authentication).toString();

        List<TripSummaryResponse> trips = queryService.list(requester, TripQueryService.MAX_LIST_SIZE).stream()
                .map(TripSummaryResponse::of)
                .toList();

        return ApiResponse.success(trips, "req_" + UUID.randomUUID());
    }

    /**
     * 여행 한 건을 조회한다 — 완료 기준
     * <b>"그 식별자로 조회하면 보낸 조건이 그대로 나온다"</b>.
     *
     * <p>🔴 없는 여행이거나 요청자가 그 여행의 회원이 아니면 <b>둘 다 404</b> 다.
     * 회원이 아닌 사람에게 "있는데 너는 못 본다"(403)를 알려주면 존재 자체가 샌다.
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
     * 여행을 지운다 — S15P21E201-746.
     *
     * <p>앱에 "내 여행에서 삭제" 버튼이 없던 이유가 이 자리였다. 표에는 삭제 칸이
     * 처음부터 있었고 목록도 그 칸을 이미 보고 있었는데, 채우는 경로만 없었다.
     *
     * <p>🔴 <b>행을 지우지 않는다.</b> 일정·기록·공유 링크가 이 여행을 가리키고 있어서
     * 실제로 지우면 그것들이 가리킬 곳을 잃는다. 대신 지운 시각을 찍고, 그 순간부터
     * 조회·편집·초대·공유가 전부 "없는 여행"(404)으로 답한다.
     *
     * <p>🔴 <b>같은 요청을 두 번 보내도 두 번째가 오류가 아니다.</b> 이미 지워져 있으면
     * 그대로 204 다 — 지하철에서 응답이 끊겨 다시 누르거나 오래된 목록에서 누르는 것은
     * 정상적으로 일어나는 일이라, 그때 오류를 띄우면 사용자는 지워지지 않았다고 믿는다.
     *
     * <p>성공은 본문이 없는 {@code 204} 다. 지워진 것을 응답 본문으로 돌려주면 화면이
     * 그것을 그릴 수 있게 되는데, 방금 지운 것을 그릴 자리는 없다.
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
     * DTO → 명령 번역은 {@link CreateTripRequestMapper} 로 옮겼다(S15P21E201-338) — 공유 일정 복제가
     * 같은 본문을 받아 같은 규칙(차원 이름 정규화 -665 포함)으로 여행을 만들어야 해서다. 두 곳에
     * 복사해 두면 정규화 규칙이 바뀐 날 한쪽만 고쳐진다.
     */
    private TripCreationService.Command toCommand(CreateTripRequest r, String userId) {
        return CreateTripRequestMapper.toCommand(r, userId);
    }

    private TripCreationService.Command toCommand(CreateTripRequest r, String userId, Trip.OwnerType ownerType) {
        return CreateTripRequestMapper.toCommand(r, userId, ownerType);
    }
}
