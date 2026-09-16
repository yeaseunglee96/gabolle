package com.gabolle.backend.menuscan;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.menuscan.config.MenuScanProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서버가 앱보다 먼저 포기하는가 — S15P21E201-1083.
 *
 * <h2>왜 시험으로 두는가</h2>
 *
 * 이 규칙은 숫자 하나에 달려 있어서 주석만으로는 안 지켜진다. 「모델이 가끔 느리니 조금만
 * 올리자」는 판단은 그 자리에서는 늘 합리적으로 보이고, 올린 사람은 앱이 12초에 끊는다는
 * 것을 모른다. 그래서 규칙을 읽을거리가 아니라 <b>빨개지는 검사</b>로 둔다.
 *
 * <h2>안 지키면 무슨 일이 일어나나</h2>
 *
 * 서버가 앱보다 오래 기다리면 읽기가 성공해도 사용자는 그 결과를 못 받는다 — 앱이 이미
 * 끊었기 때문이다. 그 한 번에 세 가지가 함께 나간다.
 *
 * <ul>
 * <li>바깥 모델 호출값을 치른다</li>
 * <li>그 사람의 하루 한도가 한 번 깎인다 — 한도는 부르기 <b>전에</b> 세므로 되돌아오지 않는다</li>
 * <li>화면에는 실패로 보이니 다시 누른다. 세 번이면 셋이 날아간다</li>
 * </ul>
 *
 * 규칙 자체는 {@code context/decisions.md} 의 {@code DEC-LATENCY-001} 이 소유한다.
 */
class MenuScanLatencyBudgetTest {

	/**
	 * 앱이 요청을 끊는 시간.
	 *
	 * <p>출처는 {@code frontend/src/api/client.ts} 의 {@code API_TIMEOUT_MS} 이고 메뉴판
	 * 경로는 그 값을 따로 안 늘린다({@code frontend/src/field/menuScan.ts} 가 기본
	 * {@code apiRequest} 를 쓴다). 저쪽이 바뀌면 이 상수도 함께 바꾼다 — 자바에서 프런트
	 * 설정을 읽을 방법이 없어 두 곳에 적히는 것은 감수한다.
	 */
	private static final Duration APP_REQUEST_TIMEOUT = Duration.ofSeconds(12);

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
	@DisplayName("읽기가 연결보다는 길다 — 모델이 생각하는 시간이 연결보다 짧을 리 없다")
	void mostOfTheBudgetGoesToReading() {
		MenuScanProperties properties = new MenuScanProperties();

		assertThat(properties.getReadTimeout())
				.as("연결이 읽기보다 길게 잡히면 예산을 잘못 나눈 것이다")
				.isGreaterThan(properties.getConnectTimeout());
	}
}
