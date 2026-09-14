package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import com.gabolle.backend.moderation.domain.StoryRemovedByModerator;

/**
 * 삭제 알림을 듣는 쪽 — S15P21E201-794.
 *
 * <h2>🔴 여기서 제일 중요한 검사</h2>
 * {@link #senderBlowingUpDoesNotEscape()} 다. 이 자리에서 예외가 올라가면 무엇이 되돌아가는지가
 * 티켓의 완료 기준("발송이 실패해도 삭제 자체는 성공한다")이고, 그것을 재는 방법은 <b>일부러
 * 터지는 발송기를 물려 보는 것</b>이다. 커밋 뒤에 도는 자리라 실제로 롤백되지는 않지만, 예외가
 * 새어 나가면 트랜잭션 동기화 콜백을 타고 올라가 호출자에게 보이는 오류로 바뀔 수 있다.
 */
class StoryRemovalNotifierTest {

	private final NotificationEmailResolver resolver = mock(NotificationEmailResolver.class);

	private final EmailSender sender = mock(EmailSender.class);

	private final StoryRemovalNotifier notifier = new StoryRemovalNotifier(resolver, sender);

	@Test
	@DisplayName("주소를 찾으면 그 주소로 삭제 알림을 보낸다")
	void sendsToResolvedAddress() {
		UUID author = UUID.randomUUID();
		when(resolver.resolve(author)).thenReturn(Optional.of("author@example.com"));

		notifier.onStoryRemoved(new StoryRemovedByModerator(UUID.randomUUID(), author, "부산 여행 첫째 날"));

		verify(sender, times(1)).sendStoryRemovedByModerator("author@example.com", "부산 여행 첫째 날");
	}

	@Test
	@DisplayName("🔴 주소가 없으면 보내지 않는다 — 애플·기본 동의 카카오 계정이 그렇다")
	void skipsWhenNoAddress() {
		UUID author = UUID.randomUUID();
		when(resolver.resolve(author)).thenReturn(Optional.empty());

		notifier.onStoryRemoved(new StoryRemovedByModerator(UUID.randomUUID(), author, "본문"));

		verify(sender, never()).sendStoryRemovedByModerator(anyString(), anyString());
	}

	@Test
	@DisplayName("🔴 발송기가 터져도 예외가 안 올라간다 — 알림은 삭제의 성공 조건이 아니다")
	void senderBlowingUpDoesNotEscape() {
		UUID author = UUID.randomUUID();
		when(resolver.resolve(author)).thenReturn(Optional.of("author@example.com"));
		org.mockito.Mockito.doThrow(new MailSendException("SMTP 서버가 응답하지 않는다"))
				.when(sender).sendStoryRemovedByModerator(anyString(), anyString());

		assertThatCode(() -> notifier.onStoryRemoved(
				new StoryRemovedByModerator(UUID.randomUUID(), author, "본문")))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("주소를 찾는 쪽이 터져도 예외가 안 올라간다")
	void resolverBlowingUpDoesNotEscape() {
		UUID author = UUID.randomUUID();
		when(resolver.resolve(author)).thenThrow(new IllegalStateException("DB 연결이 끊겼다"));

		assertThatCode(() -> notifier.onStoryRemoved(
				new StoryRemovedByModerator(UUID.randomUUID(), author, "본문")))
				.doesNotThrowAnyException();
		verify(sender, never()).sendStoryRemovedByModerator(anyString(), anyString());
	}
}
