package com.gabolle.backend.trip.presentation;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * 계정 기본 취향 API 의 오류 응답 — S15P21E201-959.
 *
 * <h2>🔴 이것이 없어서 400 이라고 적힌 자리가 실제로는 500 이었다</h2>
 *
 * <p>{@link TastePreferencesController} 와 {@code PreferenceDefaultsService.putTaste} 는
 * <i>"계정 기본값으로 둘 수 없는 차원은 400 으로 거절한다 — 조용히 버리면 화면은 저장된
 * 줄 알고 다음에 빈칸을 본다"</i> 고 적어 두었다. 그 의도는 맞지만 <b>거절을 400 으로
 * 옮겨 주는 것이 없었다.</b>
 *
 * <p>Spring 은 예외를 <b>advice 단위로</b> 순서대로 훑어 "처리할 메서드가 있는 첫 advice"
 * 에서 멈춘다. 이 저장소의 advice 는 전부 {@code assignableTypes} 로 범위가 좁혀져 있고
 * ({@code GlobalAuthExceptionHandler} 조차 {@code AuthException} 하나만 잡는다), 그 경로를
 * 맡는 advice 가 없었다. 그러면 Spring Boot 기본 오류 처리로 떨어져 <b>500</b> 이 된다.
 *
 * <p>화면에게 400 과 500 은 정반대의 말이다 — 앞엣것은 <i>"보낸 것이 잘못됐다"</i> 라
 * 사용자에게 고칠 거리를 보여줄 수 있고, 뒤엣것은 <i>"서버가 고장났다"</i> 라 사용자가
 * 할 수 있는 것이 없다. 마이페이지에서 못 두는 차원 하나를 보냈다는 이유로 빨간 화면이
 * 뜨면, 사용자는 자기 취향이 왜 저장이 안 되는지 영영 알 수 없다.
 *
 * <p>🔴 <b>주석이 무엇을 주장하든 그것은 실행되는 것이 아니다.</b> 원래 커밋의 시험 넷은
 * 전부 200 만 확인했고, 그래서 이 구멍이 초록 불 아래에 있었다. 그 자리를
 * {@code TastePreferencesControllerTest} 의 400 검사가 이제 막는다.
 *
 * <p>범위와 순서를 명시하는 것은 {@link SpendProfileExceptionHandler} 와 같은 이유다 —
 * 이 저장소는 예외 처리기 범위를 안 좁혀서 엉뚱한 advice 에 잡히는 사고를 이미 여러 번 겪었다.
 */
@RestControllerAdvice(assignableTypes = TastePreferencesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TastePreferencesExceptionHandler {

	/**
	 * 계정 기본값으로 둘 수 없는 차원 · 모르는 차원 이름 · 모르는 {@code answerStatus} ·
	 * {@code status} 와 값 유무 불일치(예: SELECTED 인데 값이 없음) — 전부 400.
	 *
	 * <p>🔴 {@code PreferenceDimensions.UnknownPreferenceDimensionException} 도
	 * {@link IllegalArgumentException} 의 하위 타입이라 여기 같이 걸린다. 앱이 보내는
	 * camelCase 이름을 어휘로 바꾸는 그 클래스가 모르는 이름을 <b>조용히 버리지 않고</b>
	 * 던지도록 만들어져 있고(-665), 그 판단이 여기서 사용자에게 보이는 답이 된다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.failure(new ApiError("TASTE_PREFERENCES_REJECTED", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
