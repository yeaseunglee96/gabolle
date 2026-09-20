package com.gabolle.backend.auth.config;

import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.service.EmailNormalizer;
import com.gabolle.backend.common.privacy.EmailMasker;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserRole;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배포 설정에 적힌 계정만 기동 시점에 운영자(ADMIN)로 만든다. 권한을 주는 경로는 이것 하나뿐이고
 * HTTP 경로는 만들지 않는다.
 *
 * <p>정한 것과 그 이유.
 * <ul>
 * <li>설정에 기본값을 두지 않는다. 비어 있으면 아무도 운영자가 아니고 기동은 성공한다.</li>
 * <li>식별자는 이메일이다. {@link EmailNormalizer} 로 정규화하므로 설정에 {@code Boss@Example.COM}
 *     이라고 적어도 가입할 때 쓴 주소와 같게 취급된다.</li>
 * <li>목록에서 빠지면 내린다. 올리기만 하면 권한 회수 방법이 "DB 를 손으로 고치기" 가 된다.</li>
 * <li>적었는데 계정이 없으면 기동을 멈춘다. 조용히 넘기면 오타 하나로 "운영자를 지정했다고
 *     생각했는데 아무도 없는" 상태가 유지된다.</li>
 * </ul>
 *
 * <p>{@code @PostConstruct} 에서 {@code IllegalStateException} 을 던지면 컨텍스트 조립이 실패해
 * 서버가 뜨지 않는다. 리포지토리를 주입받으므로 이 시점에 Flyway 는 이미 돌았다.
 *
 * <p>{@code @Transactional} 은 안 쓴다 — 생명주기 콜백은 프록시가 아니라 대상 객체에서 직접
 * 불려서 붙여도 안 걸린다. 그래서 {@link TransactionTemplate} 로 명시적으로 감싼다. 내림과 올림이
 * 한 트랜잭션에 있어야 중간에 실패했을 때 절반만 적용된 권한 상태가 남지 않는다.
 */
@Component
@Profile({"db", "dev"})
public class AdminRoleStartupSynchronizer {

	/** 오류 메시지가 가리켜야 하는 설정 이름. 배포하는 사람이 이 문자열로 자기 설정을 찾는다. */
	public static final String PROPERTY = "gabolle.auth.admin-emails";

	/** 그 설정을 채우는 환경변수. 메시지에 함께 적어야 사람이 어디를 고칠지 안다. */
	public static final String ENVIRONMENT_VARIABLE = "GABOLLE_ADMIN_EMAILS";

	private static final Logger log = LoggerFactory.getLogger(AdminRoleStartupSynchronizer.class);

	private final AuthProperties properties;
	private final LocalCredentialRepository credentialRepository;
	private final AuthIdentityRepository identityRepository;
	private final AppUserRepository userRepository;
	private final TransactionTemplate transactionTemplate;

	public AdminRoleStartupSynchronizer(AuthProperties properties, LocalCredentialRepository credentialRepository,
			AuthIdentityRepository identityRepository, AppUserRepository userRepository,
			TransactionTemplate transactionTemplate) {
		this.properties = properties;
		this.credentialRepository = credentialRepository;
		this.identityRepository = identityRepository;
		this.userRepository = userRepository;
		this.transactionTemplate = transactionTemplate;
	}

	@PostConstruct
	void synchronizeOnStartup() {
		Result result = synchronize(properties.getAdminEmails());
		if (result.configuredCount() == 0) {
			log.info("운영자로 지정된 계정이 없다 ({} 가 비어 있다). 내린 계정 {}건.", PROPERTY, result.demotedUserIds().size());
			return;
		}
		log.info("운영자 계정을 설정에 맞췄다 — 지정 {}건, 올림 {}건, 내림 {}건.", result.configuredCount(),
				result.promotedUserIds().size(), result.demotedUserIds().size());
	}

	/**
	 * 설정에 적힌 이메일의 계정만 ADMIN 으로 두고 나머지 ADMIN 은 USER 로 내린다.
	 *
	 * <p>목록을 인자로 받는 이유는 테스트가 여러 설정을 컨텍스트 하나에서 확인할 수 있게 하려는
	 * 것이다 — 컨텍스트가 늘어나면 PostgreSQL 연결 자리가 마른다.
	 *
	 * @param configuredEmails 배포 설정에 적힌 그대로의 이메일 목록. 비어 있어도 된다.
	 * @return 실제로 바뀐 계정
	 * @throws IllegalStateException 설정이 형식에 맞지 않거나, 적힌 계정을 찾을 수 없을 때.
	 *         이 예외가 기동을 멈춘다.
	 */
	public Result synchronize(List<String> configuredEmails) {
		Map<String, Integer> targets = normalizedTargets(configuredEmails);
		return transactionTemplate.execute(status -> apply(targets));
	}

	/**
	 * 설정 문자열만 보고 걸러낼 수 있는 것을 먼저 걸러낸다. DB 를 열기 전이다.
	 *
	 * @return 정규화한 이메일 → 설정에 적힌 순서(0부터). 오류 메시지가 "몇 번째 줄" 을 말하려면
	 *         이 순서를 들고 있어야 한다. 가려 적은 이메일만으로는 사람이 자기 설정에서 어느
	 *         줄인지 못 찾는다.
	 */
	private Map<String, Integer> normalizedTargets(List<String> configuredEmails) {
		Map<String, Integer> targets = new LinkedHashMap<>();
		for (int index = 0; index < configuredEmails.size(); index++) {
			String position = PROPERTY + "[" + index + "]";
			String raw = configuredEmails.get(index);
			if (raw == null || raw.isBlank()) {
				throw new IllegalStateException(position + " 이 비어 있다. 쉼표를 하나 더 찍었는지 확인하십시오 — "
						+ "운영자를 지정하지 않으려면 " + PROPERTY + " 를 통째로 비워 두면 된다.");
			}
			String email = EmailNormalizer.normalize(raw);
			int at = email.indexOf('@');
			if (at <= 0 || at == email.length() - 1 || email.indexOf('@', at + 1) >= 0) {
				// 원문을 메시지에 싣지 않는다. 이메일 형식이 아니라 가릴 수도 없고, 어느 줄인지만
				// 알려 주면 배포하는 사람은 자기 설정을 보고 고칠 수 있다.
				throw new IllegalStateException(position + " 이 이메일 형식이 아니다 (@ 가 하나 있어야 한다). "
						+ "값은 로그에 남기지 않았다.");
			}
			targets.putIfAbsent(email, index);
		}
		return targets;
	}

	/** 트랜잭션 안에서 도는 본체. 확인을 모두 마친 뒤에야 권한을 바꾼다. */
	private Result apply(Map<String, Integer> targets) {
		Map<String, UUID> resolved = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		List<String> ambiguous = new ArrayList<>();
		for (Map.Entry<String, Integer> target : targets.entrySet()) {
			String email = target.getKey();
			String position = PROPERTY + "[" + target.getValue() + "] = " + EmailMasker.mask(email);
			Set<UUID> candidates = userIdsFor(email);
			if (candidates.isEmpty()) {
				missing.add(position);
			}
			else if (candidates.size() > 1) {
				ambiguous.add(position);
			}
			else {
				resolved.put(email, candidates.iterator().next());
			}
		}

		List<AppUser> wanted = userRepository.findAllById(resolved.values());
		List<String> unusable = new ArrayList<>();
		for (AppUser user : wanted) {
			if (user.getStatus() != UserStatus.ACTIVE) {
				unusable.add(positionOf(targets, resolved, user.getUserId()) + " (상태=" + user.getStatus() + ")");
			}
		}

		if (!missing.isEmpty() || !ambiguous.isEmpty() || !unusable.isEmpty()) {
			// 확인을 다 끝낸 뒤에 한 번만 던진다. 첫 오류에서 바로 던지면 설정을 고쳐 다시
			// 띄울 때마다 다음 오류를 하나씩 새로 만나게 된다.
			throw new IllegalStateException(failureMessage(missing, ambiguous, unusable));
		}

		// 내리는 일이 먼저다. 올린 뒤에 훑으면 방금 올린 계정까지 "지금 ADMIN" 으로 잡힌다.
		Set<UUID> keep = new LinkedHashSet<>(resolved.values());
		List<UUID> demoted = new ArrayList<>();
		for (AppUser admin : userRepository.findAllByRole(UserRole.ADMIN)) {
			if (!keep.contains(admin.getUserId())) {
				admin.revokeAdmin();
				demoted.add(admin.getUserId());
			}
		}

		List<UUID> promoted = new ArrayList<>();
		for (AppUser user : wanted) {
			if (user.getRole() != UserRole.ADMIN) {
				user.grantAdmin();
				promoted.add(user.getUserId());
			}
		}

		// save 를 부르지 않는다 — 위 엔티티는 이 트랜잭션의 영속성 컨텍스트가 들고 있어
		// 커밋할 때 바뀐 값이 그대로 나간다.
		return new Result(List.copyOf(promoted), List.copyOf(demoted), targets.size());
	}

	/**
	 * 이 이메일로 로그인할 수 있는 계정을 찾는다.
	 *
	 * <p>두 곳을 본다. 비밀번호로 로그인하는 사람은 {@code local_credential} 에 이메일이 있고,
	 * 소셜 로그인만 쓰는 사람은 {@code auth_identity} 에만 있다.
	 *
	 * @return 계정이 하나면 원소 하나. 아무도 없거나 둘 이상이면 부르는 쪽이 기동을 멈춘다 —
	 *         어느 쪽에 권한을 줄지 코드가 임의로 고를 문제가 아니다.
	 */
	private Set<UUID> userIdsFor(String email) {
		Set<UUID> userIds = new LinkedHashSet<>();
		credentialRepository.findByEmail(email)
				.ifPresent(credential -> userIds.add(credential.getUser().getUserId()));
		for (AuthIdentity identity : identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull(email)) {
			userIds.add(identity.getUser().getUserId());
		}
		return userIds;
	}

	/** 이 계정이 설정의 몇 번째 줄에서 왔는지. 메시지가 설정을 가리키게 하려고 되짚는다. */
	private String positionOf(Map<String, Integer> targets, Map<String, UUID> resolved, UUID userId) {
		for (Map.Entry<String, UUID> entry : resolved.entrySet()) {
			if (entry.getValue().equals(userId)) {
				return PROPERTY + "[" + targets.get(entry.getKey()) + "] = " + EmailMasker.mask(entry.getKey());
			}
		}
		return PROPERTY + "[?]";
	}

	private String failureMessage(List<String> missing, List<String> ambiguous, List<String> unusable) {
		StringBuilder message = new StringBuilder();
		message.append("운영자 계정 설정(").append(PROPERTY).append(")을 적용할 수 없어 기동을 멈춘다.");
		appendSection(message, missing, "그런 계정이 없다 — 그 사람이 먼저 가입해야 하고, 오타면 설정을 고쳐야 한다");
		appendSection(message, ambiguous, "이 이메일이 계정 여러 개에 걸려 있다 — 어느 쪽에 권한을 줄지 사람이 정해야 한다");
		appendSection(message, unusable, "계정은 있지만 로그인할 수 없는 상태다 — 운영자로 올려도 들어갈 수 없다");
		message.append(System.lineSeparator()).append(System.lineSeparator())
				.append("대괄호 안 번호는 설정에 적은 순서(0부터)다. 이메일은 원문을 남기지 않으려고 가려 적었다 — ")
				.append("앞 한 글자와 도메인만 보인다.").append(System.lineSeparator())
				.append(PROPERTY).append(" 를 비워 두면(").append(ENVIRONMENT_VARIABLE)
				.append(" 를 주지 않으면) 아무도 운영자가 아닌 상태로 기동한다. 그것이 정상 상태다.");
		return message.toString();
	}

	private void appendSection(StringBuilder message, List<String> entries, String title) {
		if (entries.isEmpty()) {
			return;
		}
		message.append(System.lineSeparator()).append(System.lineSeparator()).append(title).append(':');
		for (String entry : entries) {
			message.append(System.lineSeparator()).append("  - ").append(entry);
		}
	}

	/**
	 * 이번 기동에서 실제로 바뀐 것.
	 *
	 * @param promotedUserIds USER 였다가 ADMIN 이 된 계정
	 * @param demotedUserIds ADMIN 이었다가 설정에서 빠져 USER 가 된 계정
	 * @param configuredCount 설정에 적혀 있던(중복을 뺀) 계정 수. {@code 0} 이면 운영자가 없는
	 *        정상 상태다 — 바뀐 것이 없다는 뜻이 아니다.
	 */
	public record Result(List<UUID> promotedUserIds, List<UUID> demotedUserIds, int configuredCount) {
	}
}
