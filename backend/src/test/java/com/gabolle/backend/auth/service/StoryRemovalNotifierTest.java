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
 * 삭제 알림을 듣는 쪽. 발송이 실패해도 삭제 자체는 성공해야 한다
 * ({@link #senderBlowingUpDoesNotEscape()}).
 *
 * <p>커밋 뒤에 도는 자리라 실제로 롤백되지는 않지만, 예외가 새어 나가면 트랜잭션 동기화
 * 콜백을 타고 올라가 호출자에게 보이는 오류가 된다.
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
