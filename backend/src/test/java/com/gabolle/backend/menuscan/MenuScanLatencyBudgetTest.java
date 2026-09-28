package com.gabolle.backend.menuscan;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.menuscan.config.MenuScanProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서버가 앱보다 먼저 포기하는가. 이 규칙은 숫자 하나에 달려 있어 주석만으로는 안 지켜진다.
 *
 * <p>서버가 더 오래 기다리면 읽기가 성공해도 사용자는 결과를 못 받는다 — 호출값은 치렀고, 하루 한도도
 * 한 번 깎였으며(한도는 부르기 전에 센다), 화면에는 실패로 보여 다시 누른다.
 */
class MenuScanLatencyBudgetTest {

	/**
	 * 앱이 메뉴판 요청을 끊는 시간. 이 경로만 앱 기본값(12초)보다 길다 — 음식 30개짜리 실제
	 * 메뉴판이 12~13초 걸리고, 시간을 먹는 것이 써 내는 양이라 프롬프트를 줄여도 안 줄어든다.
	 *
	 * <p>출처는 {@code frontend/src/field/menuScan.ts} 의 요청 시간 제한이다. 저쪽이 바뀌면 이 상수도
	 * 함께 바꾼다 — 자바에서 프런트 설정을 읽을 방법이 없어 두 곳에 적히는 것은 감수한다.
	 */
	private static final Duration APP_REQUEST_TIMEOUT = Duration.ofSeconds(30);

	@Test
	@DisplayName("완료 기준 — 연결과 읽기를 합쳐도 앱이 기다리는 시간보다 짧다")
	void theServerGivesUpBeforeTheAppDoes() {
		MenuScanProperties properties = new MenuScanProperties();

		Duration worstCase = properties.getConnectTimeout().plus(properties.getReadTimeout());

		assertThat(worstCase)
				.as("서버 최악 지연 %s 가 앱 대기 %s 를 넘으면, 성공한 읽기도 사용자에게 안 간다",
						worstCase, APP_REQUEST_TIMEOUT)
				.isLessThan(APP_REQUEST_TIMEOUT);
	}

	@Test
	@DisplayName("여유가 최소 1초는 남는다 — 딱 맞추면 망 지연 한 번에 넘어간다")
	void theBudgetKeepsHeadroom() {
		MenuScanProperties properties = new MenuScanProperties();

		Duration worstCase = properties.getConnectTimeout().plus(properties.getReadTimeout());
		Duration headroom = APP_REQUEST_TIMEOUT.minus(worstCase);

		assertThat(headroom)
				.as("예산을 앱 대기 시간에 딱 붙이면 사진 업로드와 응답 전송에 쓸 시간이 없다")
				.isGreaterThanOrEqualTo(Duration.ofSeconds(1));
	}

	@Test
	@DisplayName("우리 모델이 시간을 다 쓰고 실패한 뒤 GMS 로 대신 읽어도, 앱보다 먼저 포기한다")
	void theFallbackPathStillGivesUpBeforeTheApp() {
		// S15P21E201-1538 — 우리 모델(menu-ocr) 이 먼저 돌고, 실패하면 GMS 비전이 대신 읽는다. 최악은
		// 우리 모델이 읽기 제한까지 다 쓰고 실패한 뒤 GMS 도 제한까지 다 쓰는 경우다
		MenuScanProperties properties = new MenuScanProperties();

		Duration worstCase = properties.getLocalConnectTimeout().plus(properties.getLocalReadTimeout())
				.plus(properties.getConnectTimeout()).plus(properties.getReadTimeout());

		assertThat(APP_REQUEST_TIMEOUT.minus(worstCase))
				.as("우리 모델 %s + %s 와 대체 %s + %s 를 더한 %s 가 앱 대기 %s 보다 1초 넘게 짧아야 한다",
						properties.getLocalConnectTimeout(), properties.getLocalReadTimeout(),
						properties.getConnectTimeout(), properties.getReadTimeout(), worstCase, APP_REQUEST_TIMEOUT)
				.isGreaterThanOrEqualTo(Duration.ofSeconds(1));
	}

	@Test
	@DisplayName("우리 모델이 읽고 나서 이름을 옮겨도 앱보다 먼저 끝난다")
	void theTranslationStepFitsToo() {
		MenuScanProperties properties = new MenuScanProperties();

		Duration worstCase = properties.getLocalConnectTimeout().plus(properties.getLocalReadTimeout())
				.plus(properties.getConnectTimeout()).plus(properties.getTranslateReadTimeout());

		assertThat(worstCase).isLessThan(APP_REQUEST_TIMEOUT.minus(Duration.ofSeconds(1)));
	}

	@Test
	@DisplayName("읽기가 연결보다는 길다 — 모델이 생각하는 시간이 연결보다 짧을 리 없다")
	void mostOfTheBudgetGoesToReading() {
		MenuScanProperties properties = new MenuScanProperties();

		assertThat(properties.getReadTimeout())
				.as("연결이 읽기보다 길게 잡히면 예산을 잘못 나눈 것이다")
				.isGreaterThan(properties.getConnectTimeout());
	}
}
