package com.gabolle.backend.notification.adapter;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gabolle.backend.notification.application.PushMessage;
import com.gabolle.backend.notification.application.PushSender;

/**
 * 아직 안 켠 환경에서 쓰는 발송기 — 보내지 않고 로그만 남긴다 (S15P21E201-1391).
 * 메일 쪽 {@code LoggingEmailSender} 와 같은 짜임이다.
 *
 * <p>토큰은 안 찍는다. 그것을 아는 사람은 그 기기로 알림을 보낼 수 있고, 로그는 사람이 생각하는
 * 것보다 멀리 간다.
 */
@Component
@ConditionalOnProperty(prefix = "gabolle.push", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

	@Override
	public List<String> send(List<String> tokens, PushMessage message) {
		log.info("푸시 발송(모의) — 기기 {}대 · title={} · href={}",
				(tokens == null) ? 0 : tokens.size(), message.title(), message.href());
		// 안 보냈으니 죽은 기기도 알 수 없다. 여기서 뭔가를 돌려주면 부르는 쪽이 멀쩡한 토큰을 지운다.
		return List.of();
	}
}
