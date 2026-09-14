package com.gabolle.backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.gabolle.backend.moderation.domain.StoryRemovedByModerator;

/**
 * 운영자가 기록을 지우면 작성자에게 메일로 알린다 — S15P21E201-794.
 *
 * <h2>왜 {@code moderation} 이 아니라 {@code auth} 에 있나</h2>
 * 알리는 데 필요한 둘이 여기 있다 — 사람에게 닿는 주소를 찾는 것({@link NotificationEmailResolver})과
 * 메일을 보내는 것({@link EmailSender})이다. 그리고 반대 방향은 막혀 있다: 기록·신고 통합 테스트가
 * 띄우는 슬라이스({@code StorySliceApplication})가 {@code auth} 를 스캔하지 않아서,
 * {@code moderation} 이 발송기를 직접 물면 그 테스트들이 컨텍스트 로딩부터 깨진다.
 *
 * <p>즉 <b>방향이 설계가 아니라 제약에서 나왔다.</b> 그 사실을 숨기지 않고 적어 둔다 — 나중에
 * 알림이 여러 종류로 늘면 이 자리는 {@code notification} 같은 자기 패키지로 나가야 한다. 지금
 * 하나뿐인 것을 위해 패키지를 만들지는 않았다.
 *
 * <h2>🔴 커밋 뒤에 보낸다</h2>
 * {@code AFTER_COMMIT} 이다. 삭제 트랜잭션 안에서 보내면 메일 서버가 안 될 때 <b>운영자가 지운
 * 기록이 되살아난다.</b> 감춰야 할 글이 다시 보이는 것은 메일이 안 가는 것보다 나쁘다.
 *
 * <h2>🔴 실패를 삼킨다 — 다만 로그에는 남긴다</h2>
 * 알림은 삭제의 성공 조건이 아니다. 여기서 예외가 올라가도 커밋은 이미 끝났으므로 되돌릴 것이
 * 없고, 대신 스택트레이스가 아무 맥락 없이 로그에 찍힌다. 그래서 잡아서 <b>무엇이 안 나갔는지</b>
 * 와 함께 남긴다. {@code INC-TEST-005}(실패를 삼키는 코드를 트랜잭션 안에 두면 아무것도 안
 * 잡힌다)가 같은 자리의 기록이고, 여기는 <b>삼키는 자리가 커밋 밖</b>이라 그 함정을 피한다.
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
					// 조용히 넘어가지 않는다 — 주소가 없는 것도 그 사용자가 못 받은 경우다.
					// 완료 기준이 "발송이 실패했을 때 그 사실이 로그에 남는다" 이고, 주소 없음은
					// 고장이 아니라 지금 구조의 한계(애플·기본 동의 카카오)라 운영에서 세어 볼
					// 값이기도 하다.
					() -> log.warn("기록 삭제 알림을 보낼 주소가 없습니다. storyId={} authorUserId={}",
							event.storyId(), event.authorUserId()));
		}
		catch (RuntimeException exception) {
			log.error("기록 삭제 알림 발송에 실패했습니다. 삭제는 이미 끝났으므로 되돌리지 않습니다. storyId={}",
					event.storyId(), exception);
		}
	}
}
