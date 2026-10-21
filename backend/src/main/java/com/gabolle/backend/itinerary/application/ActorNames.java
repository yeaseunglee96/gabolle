package com.gabolle.backend.itinerary.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 사용자 UUID 목록 → 표시 이름. 판 목록과 최근 변경이 "누가" 를 그릴 때 쓴다.
 * 이름 조회는 한 번의 IN 질의다 — 판마다 사용자를 따로 읽으면 질의 수가 판 수에 비례한다.
 * 사용자 행이 없으면(탈퇴로 지워짐) 그 키는 결과에 없다. 호출자는 {@code null} 을 받고 화면이
 * 문구를 정한다 — 서버가 대신 지어내지 않는다.
 */
@Service
@Profile({ "db", "dev" })
public class ActorNames {

	private final AppUserRepository appUserRepository;

	public ActorNames(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	/**
	 * 키는 요청한 문자열 그대로(UUID 문자열). 없는 사용자는 빠진다.
	 * {@code null} 이 섞여 들어온다. 판 이력의 작성자 칸은 탈퇴하면 비워질 자리라
	 * ({@code ON DELETE SET NULL}), 거르지 않고 {@code UUID.fromString} 에 넘기면 그 자리에서
	 * 터지고 판 하나 때문에 목록 전체가 안 그려진다. 그 피해는 탈퇴한 본인이 아니라 같은 여행을
	 * 쓰던 동행자에게 간다.
	 *
	 * <p>🔴 <b>돌려주는 맵은 {@code null} 키로 찾아도 된다 — 그게 이 메서드의 계약이다
	 * (S15P21E201-1484).</b> 부르는 쪽은 죄다 {@code names.get(version.createdBy())} 처럼
	 * <b>걸러 내기 전의 값</b>으로 되찾는다. 그 값이 바로 위에서 말한 빈 칸일 수 있다.
	 * 그래서 이른 반환에도 {@code Map.of()} 를 쓰지 않는다 — {@code Map.of()} 는 없는 키가
	 * 아니라 <b>{@code null} 키를 찾는 것 자체로 NPE 를 던진다.</b> 「아무도 못 찾았다」가
	 * 「목록 전체가 500」이 되는 자리였다. 나가는 길을 하나로 두어 늘 같은 종류의 맵이
	 * 나가게 한다 — {@link HashMap} 은 {@code null} 키를 찾으면 그냥 {@code null} 이다.
	 */
	public Map<String, String> resolve(Collection<String> userIds) {
		Map<String, String> names = new HashMap<>();
		if (userIds == null || userIds.isEmpty()) {
			return names;
		}
		List<UUID> ids = userIds.stream().filter(Objects::nonNull).distinct().map(UUID::fromString).toList();
		if (ids.isEmpty()) {
			return names;
		}
		for (AppUser user : this.appUserRepository.findAllById(ids)) {
			names.put(user.getUserId().toString(), user.getDisplayName());
		}
		return names;
	}

	/**
	 * 사람마다 앱 언어 — 폰 알림을 그 사람 언어로 보내려고(S15P21E201-1864). 값이 없는 사람은 빠진다(부르는 쪽이 한국어로 본다).
	 */
	public Map<String, String> languagesOf(Collection<String> userIds) {
		Map<String, String> languages = new HashMap<>();
		if (userIds == null || userIds.isEmpty()) {
			return languages;
		}
		// 🔴 언어는 문구를 고르는 데만 쓴다 — 번호가 이상한 한 사람 때문에 알림이 통째로 안 나가면 안 된다. 못 읽는 번호는 건너뛴다.
		List<UUID> ids = userIds.stream().filter(Objects::nonNull).distinct().map(ActorNames::uuidOrNull).filter(Objects::nonNull).toList();
		if (ids.isEmpty()) {
			return languages;
		}
		for (AppUser user : this.appUserRepository.findAllById(ids)) {
			if (user.getLanguage() != null) {
				languages.put(user.getUserId().toString(), user.getLanguage());
			}
		}
		return languages;
	}

	private static UUID uuidOrNull(String raw) {
		try {
			return UUID.fromString(raw);
		}
		catch (IllegalArgumentException invalid) {
			return null;
		}
	}
}
