package com.gabolle.backend.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카프카 어댑터만 쓰는 설정 (S15P21E201-561).
 *
 * <p>재시도 상한·배치 크기는 여기 없다 — 그것은 브로커가 무엇이든 성립하는 <b>릴레이의</b>
 * 설정이라 {@link OutboxRelayProperties} 에 있다. 브로커를 갈아 끼울 때 같이 따라오면 안 되는
 * 값을 한 상자에 담으면, 갈아 끼우는 순간 그 값들도 같이 사라진다.
 *
 * <p>브로커 주소는 스프링 표준 {@code spring.kafka.bootstrap-servers} 를 그대로 쓴다. 같은 값을
 * 두 곳에 두면 한쪽만 고쳤을 때 아무 오류도 안 난다.
 *
 * <p>🔴 {@code enabled} 기본값이 {@code false} 인 이유. 의존성을 추가하는 것만으로 동작이
 * 바뀌면 안 된다. 켜기 전까지는 {@code NoOpEventPublisher} 가 꽂혀 있고 이벤트는 지금까지처럼
 * {@code published_at = null} 로 쌓인다. <b>켜는 순간 그동안 쌓인 것이 전부 나간다</b> —
 * 처음 켤 때는 쌓인 건수를 먼저 보고 켠다.
 */
@ConfigurationProperties(prefix = "gabolle.event.kafka")
public class KafkaEventProperties {

	/** 토픽 이름. 규약을 바꾸면 뒤에 붙은 판 번호를 올리고 새 토픽으로 간다. */
	private String topic = "gabolle.events.v1";

	/** 브로커로 실제로 내보낼 것인가. 꺼져 있으면 {@code NoOpEventPublisher} 가 꽂힌다. */
	private boolean enabled = false;

	/**
	 * 받는 쪽을 켤 것인가. <b>보내는 쪽({@link #enabled})과 따로 켠다.</b>
	 *
	 * <p>둘을 한 스위치로 묶으면 "내보내기만 먼저 켜서 토픽에 쌓이는지 본다" 를 할 수 없다.
	 * 처음 붙일 때는 그 확인을 꼭 한 번 하게 된다.
	 */
	private boolean consumerEnabled = false;

	/**
	 * 소비자 묶음 이름.
	 *
	 * <p>🔴 이 값을 바꾸면 <b>토픽을 처음부터 다시 읽는다.</b> 카프카는 어디까지 읽었는지를
	 * 묶음 이름별로 기억하기 때문이다. 반영 장부의 기본키가 중복을 막아 주므로 자료가 틀어지진
	 * 않지만, 쌓인 것이 많으면 다시 읽는 동안 브로커와 DB 가 바쁘다. 이름을 가볍게 바꾸지 않는다.
	 */
	private String consumerGroup = "gabolle-event-consumer";

	/**
	 * 한 건을 몇 번까지 다시 처리해 볼 것인가. 넘기면 DLQ 토픽으로 보낸다.
	 *
	 * <p>보내는 쪽 상한({@code outbox-relay.max-attempts})과 다른 값이다. 저쪽은 "브로커에
	 * 넣기" 가 실패하는 경우이고, 이쪽은 "받아서 처리하기" 가 실패하는 경우다.
	 */
	private int consumerMaxAttempts = 3;

	public String getTopic() {
		return this.topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	/**
	 * 못 처리한 것을 모아 두는 토픽. {@code <토픽>.DLT} 다.
	 *
	 * <p>이름을 따로 설정하게 두지 않는다 — 스프링 카프카의 기본 규칙이 그렇고, 우리가 다른
	 * 이름을 쓰면 나중에 그 기본 동작을 쓰는 도구들과 어긋난다.
	 */
	public String getDeadLetterTopic() {
		return this.topic + ".DLT";
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isConsumerEnabled() {
		return this.consumerEnabled;
	}

	public void setConsumerEnabled(boolean consumerEnabled) {
		this.consumerEnabled = consumerEnabled;
	}

	public String getConsumerGroup() {
		return this.consumerGroup;
	}

	public void setConsumerGroup(String consumerGroup) {
		this.consumerGroup = consumerGroup;
	}

	public int getConsumerMaxAttempts() {
		return this.consumerMaxAttempts;
	}

	public void setConsumerMaxAttempts(int consumerMaxAttempts) {
		this.consumerMaxAttempts = consumerMaxAttempts;
	}
}
