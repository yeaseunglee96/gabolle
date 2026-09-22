package com.gabolle.backend.event.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * event 패키지의 설정 묶음 (S15P21E201-561).
 *
 * <p>{@code @EnableConfigurationProperties} 를 빼면 {@link KafkaEventProperties} 가 빈으로 안
 * 만들어지고, 그 순간 {@code KafkaEventPublisher} 가 주입 실패로 기동을 막는다.
 */
@Configuration
@EnableConfigurationProperties({ KafkaEventProperties.class, OutboxRelayProperties.class })
public class EventConfiguration {

	/**
	 * 키·값이 모두 문자열이라고 <b>못 박은</b> 발송 틀.
	 *
	 * <p>키·값 타입을 못 박아 두면 {@code KafkaEventPublisher} 가 받는 자리와 정확히 맞고,
	 * 직렬화기가 무엇인지도 이 파일 한 곳에서 보인다.
	 *
	 * <p>🔴 <b>Spring Boot 4 에서는 {@code org.springframework.kafka:spring-kafka} 만 넣으면
	 * 자동설정이 안 따라온다.</b> 자동설정이 별도 모듈({@code spring-boot-kafka})로 갈라져
	 * 나갔기 때문이다. 그래서 {@code build.gradle} 은 {@code spring-boot-starter-kafka} 를 쓴다 —
	 * 그것이 둘을 같이 끌어온다.
	 *
	 * <p>이 함정이 고약한 이유는 <b>증상이 엉뚱한 곳에서 나기 때문</b>이다. 클래스패스에
	 * {@code KafkaTemplate} 클래스는 있으니 컴파일은 통과하고, 기동할 때 비로소
	 * <i>"No qualifying bean of type KafkaTemplate&lt;String, String&gt;"</i> 가 난다. 그 문구만
	 * 보면 제네릭이 안 맞는 줄 알기 쉬운데, 실제로는 <b>빈이 아예 하나도 없는</b> 것이다.
	 * 실제로 이 티켓에서 그 오진을 한 번 했다.
	 *
	 * <p>설정값은 {@code spring.kafka.*} 를 그대로 읽는다({@link KafkaProperties}). 우리 이름으로
	 * 한 벌 더 두지 않는다 — 두면 한쪽만 고쳤을 때 아무 오류도 안 난다.
	 *
	 * <p>부트의 자동설정 빈은 {@code @ConditionalOnMissingBean} 이라 이 빈이 있으면 물러난다.
	 */
	@Bean
	@ConditionalOnProperty(prefix = "gabolle.event.kafka", name = "enabled", havingValue = "true")
	public KafkaTemplate<String, String> eventKafkaTemplate(KafkaProperties kafkaProperties) {
		return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties()));
	}
}
