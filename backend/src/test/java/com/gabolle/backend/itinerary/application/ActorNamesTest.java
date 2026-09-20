package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * {@link ActorNames} 검증. 지키는 것은 탈퇴한 사람이 만든 판이다 — 작성자 칸은 탈퇴하면
 * 비워지고({@code ON DELETE SET NULL}), 그 {@code null} 을 거르지 않으면 판 하나 때문에
 * 판 목록과 여행 활동 기록이 통째로 안 그려진다.
 */
class ActorNamesTest {

	private final AppUserRepository appUserRepository = mock(AppUserRepository.class);

	private final ActorNames actorNames = new ActorNames(this.appUserRepository);

	@Test
	@DisplayName("작성자가 비어 있어도 터지지 않는다 — 탈퇴한 사람이 만든 판")
	void toleratesNullUserId() {
		when(this.appUserRepository.findAllById(any())).thenReturn(List.of());

		assertThatCode(() -> this.actorNames.resolve(Arrays.asList((String) null)))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("전부 비어 있으면 DB 를 부르지 않는다")
	void skipsQueryWhenEveryIdIsNull() {
		Map<String, String> names = this.actorNames.resolve(Arrays.asList(null, null));

		assertThat(names).isEmpty();
		// 빈 IN 질의를 보내지 않는다. 판 목록은 화면을 열 때마다 이 길을 지난다.
		verify(this.appUserRepository, never()).findAllById(any());
	}

	@Test
	@DisplayName("비어 있는 것과 섞여 있어도 나머지는 그대로 찾는다")
	void resolvesRemainingIdsWhenSomeAreNull() {
		UUID alive = UUID.randomUUID();
		when(this.appUserRepository.findAllById(any())).thenReturn(List.of());

		Map<String, String> names = this.actorNames
				.resolve(Arrays.asList(alive.toString(), null, alive.toString()));

		// 사용자 행이 없으면 그 키는 결과에 없다 — 클래스 주석이 정한 계약 그대로다.
		// 호출자는 null 을 받고 화면이 문구를 정한다. 서버는 문구를 지어내지 않는다.
		assertThat(names).doesNotContainKey(alive.toString());
		verify(this.appUserRepository).findAllById(List.of(alive));
	}
}
