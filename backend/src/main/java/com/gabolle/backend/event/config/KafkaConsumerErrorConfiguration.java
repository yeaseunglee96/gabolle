package com.gabolle.backend.event.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 못 처리한 이벤트를 어떻게 다룰 것인가 (S15P21E201-561).
 *
 * <p>완료 기준이 요구하는 것: <i>"실패 이벤트를 DLQ 에서 수정 후 replay 할 수 있다"</i>.
 *
 * <p>🔴 <b>기본 동작을 그대로 두면 안 된다.</b> 스프링 카프카의 기본 오류 처리기는 실패한
 * 레코드를 정해진 횟수만큼 다시 시도한 뒤 <b>로그만 남기고 넘어간다.</b> 그러면 그 이벤트는
 * 어디에도 안 남고 사라진다 — 아웃박스에는 "보냈다" 로 찍혀 있으므로 다시 보낼 방법도 없다.
 *
 * <p>여기서는 소진한 레코드를 {@code <토픽>.DLT} 로 옮긴다. 옮겨진 레코드에는 스프링 카프카가
 * 원래 토픽·파티션·오프셋과 예외 내용을 헤더로 붙여 준다. 사람이 원인을 고친 뒤 그 토픽의
 * 레코드를 원래 토픽으로 다시 넣으면 replay 다 — 반영 장부의 기본키가 중복을 막으므로
 * <b>이미 반영된 것을 다시 넣어도 두 번 반영되지 않는다.</b>
 *
 * <p>🔴 DLQ 로 보내는 것도 <b>브로커에 쓰는 일</b>이라 실패할 수 있다. 그때는 원래 예외가
 * 그대로 올라가 오프셋이 안 넘어가고, 다음 차례에 같은 레코드를 다시 읽는다. 잃는 것보다 낫다.
 */
@Configuration
@ConditionalOnProperty(prefix = "gabolle.event.kafka", name = "consumer-enabled", havingValue = "true")
public class KafkaConsumerErrorConfiguration {

	private static final Logger log = LoggerFactory.getLogger(KafkaConsumerErrorConfiguration.class);

	/**
	 * 다시 시도할 때 사이에 두는 시간.
	 *
	 * <p>0 으로 두지 않는다. 실패 원인이 "DB 가 잠깐 바쁘다" 같은 것일 때 간격 없이 세 번을
	 * 몰아치면 세 번 다 같은 이유로 실패하고, 재시도가 있으나 마나가 된다.
	 */
	private static final long RETRY_INTERVAL_MS = 1_000L;

	@Bean
	public DefaultErrorHandler eventConsumerErrorHandler(KafkaOperations<String, String> kafkaOperations,
			KafkaEventProperties properties) {

		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
				(record, exception) -> {
					log.error("event=EVENT_SENT_TO_DLQ topic={} partition={} offset={} reason={}", record.topic(),
							record.partition(), record.offset(), exception.getClass().getSimpleName(), exception);
					// 파티션을 -1 로 준다 = 브로커가 알아서 고르게 한다. 원래 파티션 번호를
					// 그대로 쓰면 DLT 의 파티션 수가 더 적을 때 존재하지 않는 자리를 가리켜
					// 발행 자체가 실패한다.
					return new org.apache.kafka.common.TopicPartition(properties.getDeadLetterTopic(), -1);
				});

		// maxAttempts 는 "처음 한 번 + 다시 몇 번" 이 아니라 총 시도 횟수다. FixedBackOff 는
		// **추가 시도 횟수**를 받으므로 하나를 뺀다 — 안 빼면 설정값보다 한 번 더 시도한다.
		long additionalAttempts = Math.max(0, properties.getConsumerMaxAttempts() - 1L);
		return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, additionalAttempts));
	}
}
