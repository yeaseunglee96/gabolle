package com.gabolle.backend.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각 제공자.
 *
 * <p>🔴 코드에서 {@code Instant.now()} 를 직접 부르지 않고 {@link Clock} 을 주입받는 이유는
 * <b>테스트에서 시각을 고정할 수 있어야</b> 하기 때문이다. "30분 뒤에 만료된다" 같은
 * 규칙을 검증하려면 시계를 앞으로 돌릴 수 있어야 한다.
 *
 * <p>🔴 시간대는 UTC 로 고정한다. 서버·DB·연결의 시간대가 다르면
 * "일정이 통째로 9시간 밀린다" (실행계획 6.2절). 표시용 변환은 표현 계층에서 한다.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
