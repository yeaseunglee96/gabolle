package com.gabolle.testslice;

import java.time.Clock;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.gabolle.backend.notification.application.PushTokenService;
import com.gabolle.backend.notification.infra.PushTokenJpaRepository;

/**
 * 기기 푸시 토큰 표를 진짜 PostgreSQL 위에서 보는 슬라이스.
 *
 * <p>여기서 표로만 확인되는 것은 <b>트랜잭션의 의미</b>다. 「지우라고 불렀는가」는 목으로도
 * 보이지만 「정말 지워졌는가」는 안 보인다 — {@code S15P21E201-1484} 의 결함이 정확히 그
 * 틈에 있었다. 부르기는 불렀고 예외도 안 났는데 행이 남아 있었다.
 *
 * <p>🔴 <b>{@code notification} 을 훑지 않는다.</b> 훑으면 {@code TripPushNotifier} 가 딸려 오고,
 * 그것이 일정·여행·사용자 저장소를 달라고 해서 이 시험과 상관없는 슬라이스 절반이 끌려온다.
 * 필요한 것은 {@link PushTokenService} 하나라 그것만 손으로 등록한다 — {@code @Bean} 으로 올려도
 * {@code @Transactional} 프록시는 그대로 걸린다. 표는 Flyway 가 전부 만든다.
 */
@SpringBootApplication(scanBasePackages = "com.gabolle.backend.common")
@EntityScan(basePackages = "com.gabolle.backend.notification.infra")
@EnableJpaRepositories(basePackages = "com.gabolle.backend.notification.infra")
public class NotificationSliceApplication {

	@Bean
	PushTokenService pushTokenService(PushTokenJpaRepository repository) {
		return new PushTokenService(repository, Clock.systemUTC());
	}
}
