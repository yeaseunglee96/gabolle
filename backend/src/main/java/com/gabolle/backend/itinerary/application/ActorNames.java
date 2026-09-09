package com.gabolle.backend.itinerary.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 사용자 UUID 목록 → 표시 이름 — 판 목록(ITN-02)과 최근 변경이 "누가" 를 그릴 때 쓴다.
 *
 * <p>🔴 이름 조회는 한 번의 IN 질의다. 판마다 사용자를 따로 읽으면 질의 수가 판 수에 비례한다.
 * 사용자 행이 없으면(탈퇴로 지워짐) 그 키는 결과에 없다 — 호출자는 {@code null} 을 받고 화면이
 * "알 수 없는 사용자" 로 그린다. 서버가 대신 문구를 지어내지 않는다(문구는 화면·언어의 몫이다).
 */
@Service
@Profile({ "db", "dev" })
public class ActorNames {

	private final AppUserRepository appUserRepository;

	public ActorNames(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	/** 키는 요청한 문자열 그대로(UUID 문자열). 없는 사용자는 빠진다. */
	public Map<String, String> resolve(Collection<String> userIds) {
		if (userIds == null || userIds.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = userIds.stream().distinct().map(UUID::fromString).toList();
		Map<String, String> names = new HashMap<>();
		for (AppUser user : this.appUserRepository.findAllById(ids)) {
			names.put(user.getUserId().toString(), user.getDisplayName());
		}
		return names;
	}
}
