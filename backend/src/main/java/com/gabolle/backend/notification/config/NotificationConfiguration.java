package com.gabolle.backend.notification.config;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gabolle.backend.notification.application.EditPushBatcher;

/**
 * 푸시 발송 설정을 스프링에 연결한다 — {@code ExchangeRateConfiguration} 과 같은 방식이다.
 * 칸마다 기본값이 있어 설정 파일이 없어도 뜬다 (S15P21E201-1391).
 */
@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class NotificationConfiguration {

	/**
	 * 연달아 바꾼 일정 알림을 한 통으로 모으는 자리(S15P21E201-1880). 예약 실행자 한 줄이면 된다 — 한 번에 도는 일은
	 * 「모은 것을 보내기」뿐이고 그것도 몇 초면 끝난다. 데몬 스레드라 서버가 꺼질 때 붙잡지 않는다.
	 */
	@Bean(destroyMethod = "")
	EditPushBatcher editPushBatcher(PushProperties properties) {
		if (properties.getEditBatchQuiet() == null || properties.getEditBatchQuiet().isZero()) {
			return EditPushBatcher.immediate();
		}
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor((runnable) -> {
			Thread thread = new Thread(runnable, "push-edit-batch");
			thread.setDaemon(true);
			return thread;
		});
		return new EditPushBatcher(scheduler, properties.getEditBatchQuiet(), properties.getEditBatchMax(), Clock.systemUTC());
	}
}
