package com.gabolle.backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.gabolle.backend.moderation.domain.StoryRemovedByModerator;

/**
 * 운영자가 기록을 지우면 작성자에게 메일로 알린다.
 *
 * <p>{@code moderation} 이 아니라 {@code auth} 에 있는 것은 제약 때문이다 — 기록·신고 통합
 * 테스트가 띄우는 슬라이스가 {@code auth} 를 스캔하지 않아서, {@code moderation} 이 발송기를
 * 직접 물면 그 테스트들이 컨텍스트 로딩부터 깨진다. 알림이 여러 종류로 늘면 자기 패키지로
 * 나가야 한다.
 *
 * <p>{@code AFTER_COMMIT} 이어야 한다. 삭제 트랜잭션 안에서 보내면 메일 서버가 안 될 때 운영자가
 * 지운 기록이 되살아난다 — 감춰야 할 글이 다시 보이는 것은 메일이 안 가는 것보다 나쁘다.
 *
 * <p>실패는 삼키되 로그에는 남긴다. 커밋이 이미 끝나 되돌릴 것이 없고, 안 잡으면 스택트레이스가
 * 아무 맥락 없이 찍힌다. 삼키는 자리가 커밋 밖이라 삭제 자체는 영향을 안 받는다.
 */
@Component
@Profile({ "db", "dev" })
public class StoryRemovalNotifier {

	private static final Logger log = LoggerFactory.getLogger(StoryRemovalNotifier.class);

	private final NotificationEmailResolver emailResolver;

	private final EmailSender emailSender;

	public StoryRemovalNotifier(NotificationEmailResolver emailResolver, EmailSender emailSender) {
		this.emailResolver = emailResolver;
		this.emailSender = emailSender;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStoryRemoved(StoryRemovedByModerator event) {
		try {
			this.emailResolver.resolve(event.authorUserId()).ifPresentOrElse(
					(email) -> this.emailSender.sendStoryRemovedByModerator(email, event.excerpt()),
					// 주소가 없는 것도 그 사용자가 못 받은 경우다. 고장이 아니라 구조의
					// 한계(애플·기본 동의 카카오)라 운영에서 세어 볼 값이기도 하다.
					() -> log.warn("기록 삭제 알림을 보낼 주소가 없습니다. storyId={} authorUserId={}",
							event.storyId(), event.authorUserId()));
		}
		catch (RuntimeException exception) {
			log.error("기록 삭제 알림 발송에 실패했습니다. 삭제는 이미 끝났으므로 되돌리지 않습니다. storyId={}",
					event.storyId(), exception);
		}
	}
}
