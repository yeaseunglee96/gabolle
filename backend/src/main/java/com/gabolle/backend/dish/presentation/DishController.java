package com.gabolle.backend.dish.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.dish.application.DishService;
import com.gabolle.backend.dish.presentation.dto.DishRequest;
import com.gabolle.backend.dish.presentation.dto.DishResponse;

import java.time.Duration;

/**
 * 메뉴에서 읽은 음식 하나에 설명과 그림을 붙인다.
 *
 * <p>설명과 그림을 두 번에 나눠 부른다. 그림 한 장이 가장 빠른 설정으로도 10.9초인데 앱은 12초에
 * 끊는다. 앱의 대기 시간을 늘리는 대신 요청을 짧게 쪼갠다.
 *
 * <p>이 API 가 주는 것은 메뉴판 읽기와 달리 사진에 보이는 것이 아니라 모델이 아는 것이다. 응답의
 * {@code descriptionSource}·{@code imageSource} 칸이 그것을 말한다. 알레르기를 말하는 통로는 메뉴판
 * 응답의 {@code allergenWords} 하나뿐이고 이 API 는 그것을 늘리지도 줄이지도 않는다.
 */
@RestController
@Profile({ "db", "dev" })
public class DishController {

	/**
	 * 그림을 앱이 다시 안 받아 가게 하는 시간. 한 번 만든 그림은 바뀌지 않고 내용이 달라지면 주소도
	 * 달라져, 길게 잡아도 틀릴 일이 없다.
	 */
	private static final Duration IMAGE_CACHE = Duration.ofDays(30);

	private final DishService service;

	public DishController(DishService service) {
		this.service = service;
	}

	@PostMapping("/api/v1/dishes")
	public ApiResponse<DishResponse> describe(@RequestBody DishRequest request,
			Authentication authentication) {

		UUID userId = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(
				this.service.describe(userId, request.name(), request.language()),
				"req_" + UUID.randomUUID());
	}

	/**
	 * 만들어 둔 그림을 내준다. 화면이 이미지 주소로 바로 부르므로 봉투에 담지 않고 바이트를 그대로
	 * 낸다. 기록 사진과 달리 로그인 없이 열리는 자리로 두지 않는다.
	 */
	@GetMapping("/api/v1/dishes/images/{imageId}")
	public ResponseEntity<byte[]> image(@PathVariable UUID imageId, Authentication authentication) {
		AuthenticatedUsers.requireId(authentication);

		DishService.StoredImage image = this.service.image(imageId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.contentType()))
				.cacheControl(CacheControl.maxAge(IMAGE_CACHE).cachePublic())
				.body(image.bytes());
	}
}
