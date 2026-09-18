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
 * 메뉴에서 읽은 음식 하나에 설명과 그림을 붙인다 — S15P21E201-1272.
 *
 * <pre>
 * POST /api/v1/dishes            {"name":"돼지국밥","language":"en"}
 *                                → 설명은 그 자리에서. 그림은 imageStatus 로만 알린다
 * GET  /api/v1/dishes/images/{id}
 *                                → 200 그림 바이트 · 202 아직 만드는 중 · 404 못 만들었다
 * </pre>
 *
 * <h2>🔴 왜 두 번에 나눠 부르나</h2>
 *
 * 그림 한 장이 가장 빠른 설정으로도 <b>10.9초</b>다(2026-09-18 실측). 앱은 12초에 끊는다
 * ({@code frontend/src/api/client.ts} 의 {@code API_TIMEOUT_MS}). 한 번에 주려면 그
 * 제한을 이 경로만 늘려야 하는데, <b>그러지 않기로 한 것이 DEC-LATENCY-001</b> 이다.
 * 제한을 늘리는 대신 <b>요청을 짧게 쪼갠다</b> — 둘 다 12초 안에 넉넉히 끝난다.
 *
 * <h2>🔴 이 API 가 주는 것은 「모델이 아는 것」이다</h2>
 *
 * 메뉴판 읽기({@code /api/v1/menu-scans})가 주는 것은 전부 <b>사진에 보이는 것</b>이고,
 * 여기 있는 것은 전부 <b>모델이 아는 것</b>이거나 <b>모델이 만든 것</b>이다. 응답이
 * {@code descriptionSource}·{@code imageSource} 칸으로 그것을 말한다 —
 * 화면이 문구를 빠뜨릴 수는 있어도 칸을 안 받을 수는 없게.
 *
 * <p>알레르기를 말하는 통로는 <b>메뉴판 응답의 {@code allergenWords} 하나뿐</b>이고
 * 이 API 는 그것을 늘리지도 줄이지도 않는다.
 */
@RestController
@Profile({ "db", "dev" })
public class DishController {

	/**
	 * 그림을 앱이 다시 안 받아 가게 하는 시간.
	 *
	 * <p>한 번 만든 그림은 <b>바뀌지 않는다</b> — 주소가 그 그림 하나를 가리키고, 내용이
	 * 달라지면 주소도 달라진다. 그래서 길게 잡아도 틀릴 일이 없고, 여행지의 통신망에서
	 * 같은 그림을 다시 내려받지 않는 것이 실제로 크다.
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
	 * 만들어 둔 그림을 내준다.
	 *
	 * <p>🔴 <b>봉투에 담지 않고 바이트를 그대로</b> 낸다. 화면이 {@code <Image src>} 로
	 * 바로 부르는 주소라, JSON 으로 감싸면 앱이 base64 를 풀어 다시 그려야 한다.
	 *
	 * <p>🔴 <b>로그인을 요구한다.</b> 기록 사진 업로드 경로처럼 「주소만 알면 열리는」
	 * 자리로 두지 않는다 — 그쪽이 그런 이유는 피드가 로그인 없이 {@code <img>} 로
	 * 부르기 때문이고, 여기는 그럴 이유가 없다.
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
