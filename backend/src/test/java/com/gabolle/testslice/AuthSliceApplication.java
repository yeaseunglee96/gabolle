package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 인증 통합 테스트가 띄우는 애플리케이션 — <b>인증·사용자·공통만</b> 올린다.
 *
 * <h2>🔴 왜 이게 지금 필요한가</h2>
 *
 * 인증은 이 저장소에서 배포에 가장 많이 나간 모듈인데 <b>실제 DB 위에서 도는 테스트가 하나도
 * 없었다.</b> 21개 테스트가 전부 Mockito 로 리포지토리를 흉내 낸다. 그러면 확인할 수 없는 것이
 * 있다 — 트랜잭션이 되돌려질 때 무엇이 남고 무엇이 사라지는지다.
 *
 * <p>S15P21E201-421(로그인 연속 실패 차단)이 정확히 그 자리에 걸린다. 실패 횟수를 세는 코드가
 * 예외를 던지는 트랜잭션 안에 있으면 센 것까지 같이 사라져서 기능이 한 번도 동작하지 않는데,
 * 응답은 정상이라 Mockito 테스트는 전부 초록이다. 그래서 이 슬라이스를 만들었다.
 *
 * <p>🔴 {@code com.gabolle.backend} 밖에 두는 것이 필수다. 테스트 소스도 클래스패스에 올라가므로
 * 안에 두면 본 애플리케이션 스캔에 걸려 아래 {@code @EnableJpaRepositories} 가 {@code no-db}
 * 프로필에서도 켜지고 {@code contextLoads} 가 죽는다. 같은 사고가
 * {@code RecommendationSliceApplication} 주석에 기록돼 있다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.auth",
		"com.gabolle.backend.user"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.auth.domain",
		"com.gabolle.backend.user.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.auth.repository",
		"com.gabolle.backend.user.repository"
})
public class AuthSliceApplication {
}
