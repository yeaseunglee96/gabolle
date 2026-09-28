package com.gabolle.backend.event.application;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 행동 기반 개인화 동의를 판정하는 <b>유일한 자리</b> — S15P21E201-549 의 규칙이 사는 곳.
 *
 * <p>이 규칙을 쓰는 곳이 둘이다: 수집 API 입구({@link EventIngestService})와 Outbox
 * 입구({@link OutboxService}). <b>규칙 자체는 여기 한 벌만 둔다</b> — 두 벌이 되면 한쪽만
 * 바뀌고, 그 어긋남은 「껐는데 이 종류만 계속 쌓이는」 모양으로 나타나서 화면에서는 안 보인다.
 * {@code EventType.BEHAVIOR_SIGNALS} 가 같은 이유로 한 벌인 것과 같은 판단이다.
 *
 * <p>전에는 이 메서드가 {@code EventIngestService} 안에 있었고, {@code OutboxService} 를
 * 직접 부르는 경로가 그것을 {@code public} 으로 빌려 썼다. 그 자리에 <i>"임시 방편이고,
 * 제대로 된 자리는 입구인 OutboxService 다"</i> 라고 적혀 있었다 (S15P21E201-1096).
 */
@Service
@Profile({ "db", "dev" })
public class BehaviorConsent {

	private final AppUserRepository users;

	public BehaviorConsent(AppUserRepository users) {
		this.users = users;
	}

	/**
	 * 이 사람의 행동을 지금 적어도 되는가.
	 *
	 * <p>탈퇴처럼 {@code user_id} 만 비우는 방법은 여기서 쓸 수 없다. 행동 이벤트는 대부분 축이
	 * 여행이라 {@code aggregate_id} 에 {@code trip_id} 가 들어 있고 그 여행에는 주인이 있다 —
	 * 비워도 여행을 거쳐 그 사람으로 되돌아갈 수 있다. 되돌릴 수 있는 가리기는 가린 것이
	 * 아니므로 아예 적지 않는다.
	 *
	 * <p>🔴 <b>없는 사람과 모르는 사람을 다르게 다룬다.</b> {@code userId == null} 이면 적는다 —
	 * 이미 익명이라 막아도 지켜지는 개인정보가 없고 집계만 사라진다. 사람은 있는데 계정을 못
	 * 찾으면 안 적는다 — 동의를 확인할 수 없는 상태이고, 실패는 조용한 수집이 아니라 빈 자리로
	 * 나타나야 한다.
	 */
	public boolean collects(UUID userId) {
		if (userId == null) {
			return true;
		}
		return this.users.findPersonalizationMode(userId)
				.filter(PersonalizationMode.BEHAVIOR_ENABLED::equals)
				.isPresent();
	}
}
