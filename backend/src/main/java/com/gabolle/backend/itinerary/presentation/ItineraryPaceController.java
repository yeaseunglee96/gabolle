package com.gabolle.backend.itinerary.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.ItineraryPaceService;
import com.gabolle.backend.itinerary.application.ItineraryRhythmService;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryPaceResponse;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryRhythmResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 지연 경고 · 여행 리듬 조회.
 * 둘 다 보기 전용이라 {@code ItineraryAccess.requireMember} 로 권한을 확인한다 — 일정을 볼 수
 * 있는 사람이면 VIEWER 도 이 두 경로를 볼 수 있다.
 * {@code @Profile({"db","dev"})} · {@code @ConditionalOnBean(TripQueryService.class)} 인 이유는
 * {@link ItineraryQueryController} 와 같다.
 */
@RestController
@RequestMapping("/api/v1/itineraries")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryPaceController {

    private final ItineraryPaceService paceService;

    private final ItineraryRhythmService rhythmService;

    public ItineraryPaceController(ItineraryPaceService paceService, ItineraryRhythmService rhythmService) {
        this.paceService = paceService;
        this.rhythmService = rhythmService;
    }

    /**
     * 하루치 지연 경고.
     * 경로를 {@code value} 로 준다. {@code path} 로 쓰면 인가 정책 검사가 경로를 빈 값으로 읽어
     * "정책 없는 경로" 로 걸린다.
     */
    @GetMapping(value = "/{itineraryId}/days/{dayIndex}/pace")
    public ApiResponse<ItineraryPaceResponse> pace(@PathVariable String itineraryId, @PathVariable int dayIndex,
            Authentication authentication) {

        String requester = AuthenticatedUsers.requireId(authentication).toString();
        ItineraryPaceResponse response = this.paceService.pace(itineraryId, dayIndex, requester);
        return ApiResponse.success(response, "req_" + UUID.randomUUID());
    }

    /** 여행 전체의 리듬 요약. */
    @GetMapping(value = "/{itineraryId}/rhythm")
    public ApiResponse<ItineraryRhythmResponse> rhythm(@PathVariable String itineraryId,
            Authentication authentication) {

        String requester = AuthenticatedUsers.requireId(authentication).toString();
        ItineraryRhythmResponse response = this.rhythmService.rhythm(itineraryId, requester);
        return ApiResponse.success(response, "req_" + UUID.randomUUID());
    }
}
