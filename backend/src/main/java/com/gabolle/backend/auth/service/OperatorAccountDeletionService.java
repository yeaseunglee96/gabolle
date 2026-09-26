package com.gabolle.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.api.AccountDeletionPreviewResponse;
import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.domain.OperatorAccountDeletionLog;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.repository.OperatorAccountDeletionLogRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;

/**
 * 운영자 대리 탈퇴 (S15P21E201-1647) — 앱에 로그인할 수 없는 사람의 이메일 삭제 요청을 운영자가 대신 처리한다.
 *
 * <p>공개 삭제 안내 페이지와 자동응답이 「가입한 이메일에서 삭제 요청 메일을 보내면 7일 이내에 삭제한다」고 약속한다.
 * {@link AccountDeletionService#delete} 를 부르는 곳은 본인 요청 하나뿐이라, 이 약속을 지킬 수단이 이것 전에는 없었다.
 *
 * <h2>무엇을 하고 무엇을 안 하나</h2>
 * <ul>
 * <li><b>본인 확인은 사람이 한다.</b> 이 클래스는 「이 이메일을 가진 계정을 찾아 지운다」까지다. 요청 메일이 가입한
 * 주소에서 왔는지, 그 주소로 회신해 확인했는지는 운영자가 먼저 하고({@code backend/docs/ACCOUNT-DELETION-BY-EMAIL.md}),
 * 끝난 뒤에만 지우는 실행을 켠다.</li>
 * <li><b>지우는 일은 본인 탈퇴와 같은 코드다.</b> {@link AccountDeletionService#delete} 를 그대로 부른다 — 지우는 범위가
 * 두 벌이 되면 언젠가 한쪽만 고쳐져 「앱에서는 지워지는데 대리로는 남는」 일이 생긴다.</li>
 * <li><b>기본은 미리보기다.</b> {@code execute} 가 참일 때만 지운다. 미리보기는 아무것도 바꾸지 않고 기록도 안 남긴다.</li>
 * <li><b>애매하면 안 지운다.</b> 한 이메일이 서로 다른 계정 둘 이상을 가리키면(예: 한 사람의 이메일 가입과 다른 사람의
 * 소셜 계정) 아무것도 지우지 않고 사람이 가리게 한다.</li>
 * </ul>
 *
 * <h2>처리 기록</h2>
 * 지웠을 때와 못 지웠을 때({@code NOT_FOUND}·{@code AMBIGUOUS}) 모두 한 줄을 남긴다. 이메일 원문 없이 해시만 남기고,
 * 지우는 것과 같은 트랜잭션이라 「지웠는데 기록이 없는」 상태가 생기지 않는다.
 */
@Service
@Profile({ "db", "dev" })
public class OperatorAccountDeletionService {

	public enum Outcome {
		/** 미리보기 — 아무것도 바꾸지 않았다. */
		DRY_RUN,
		/** 계정 하나를 찾아 지웠다. */
		DELETED,
		/** 그 이메일로 지금 쓸 수 있는 계정이 없다. */
		NOT_FOUND,
		/** 이메일이 서로 다른 계정 둘 이상을 가리킨다 — 아무것도 지우지 않았다. */
		AMBIGUOUS
	}

	/**
	 * @param userId 찾은 계정({@link Outcome#NOT_FOUND}·{@link Outcome#AMBIGUOUS} 이면 널)
	 * @param preview 지우기 전에 센 영향 수({@link Outcome#DRY_RUN}·{@link Outcome#DELETED} 일 때만)
	 * @param loginMeans 그 계정의 로그인 수단 이름 — {@code LOCAL}·{@code GOOGLE} 등. 어떤 계정인지 운영자가 눈으로
	 *     확인하는 용도다
	 * @param matchedAccounts 이메일이 가리킨 서로 다른 계정 수
	 */
	public record Result(Outcome outcome, UUID userId, AccountDeletionPreviewResponse preview,
			List<String> loginMeans, int matchedAccounts) {
	}

	private final LocalCredentialRepository credentialRepository;

	private final AuthIdentityRepository identityRepository;

	private final AccountDeletionService accountDeletionService;

	private final OperatorAccountDeletionLogRepository logRepository;

	private final Clock clock;

	public OperatorAccountDeletionService(LocalCredentialRepository credentialRepository,
			AuthIdentityRepository identityRepository, AccountDeletionService accountDeletionService,
			OperatorAccountDeletionLogRepository logRepository, Clock clock) {
		this.credentialRepository = credentialRepository;
		this.identityRepository = identityRepository;
		this.accountDeletionService = accountDeletionService;
		this.logRepository = logRepository;
		this.clock = clock;
	}

	/**
	 * @param rawEmail 요청 메일을 보낸 주소. 앞뒤 공백·대소문자는 가입 때와 같은 규칙({@link EmailNormalizer})으로 맞춘다
	 * @param requestRef 요청 식별 — 메일 수신일·티켓 번호. 이메일 주소·이름을 적지 않는다({@code execute} 일 때 필수)
	 * @param operator 처리하는 운영자 이름({@code execute} 일 때 필수)
	 * @param execute 참일 때만 지운다. 거짓이면 무엇이 지워질지만 알려 준다
	 */
	@Transactional
	public Result process(String rawEmail, String requestRef, String operator, boolean execute) {
		if (rawEmail == null || rawEmail.isBlank()) {
			throw new IllegalArgumentException("이메일이 비었다");
		}
		if (execute) {
			// 기록에 남길 값이 없으면 지우기 전에 멈춘다 — 지운 뒤에 기록이 비는 것보다 안 지우는 편이 낫다.
			if (requestRef == null || requestRef.isBlank()) {
				throw new IllegalArgumentException("지우려면 요청 식별(request-ref)이 필요하다 — 메일 수신일이나 티켓 번호");
			}
			if (operator == null || operator.isBlank()) {
				throw new IllegalArgumentException("지우려면 처리하는 운영자 이름(operator)이 필요하다");
			}
			if (requestRef.contains("@")) {
				throw new IllegalArgumentException("request-ref 에 이메일 주소를 적지 않는다 — 수신일이나 티켓 번호만");
			}
		}

		String email = EmailNormalizer.normalize(rawEmail);
		String emailHash = sha256Hex(email);

		Map<UUID, List<String>> accounts = findActiveAccounts(email);

		if (accounts.isEmpty()) {
			if (execute) {
				record(requestRef, operator, emailHash, null, OperatorAccountDeletionLog.NOT_FOUND);
			}
			return new Result(Outcome.NOT_FOUND, null, null, List.of(), 0);
		}
		if (accounts.size() > 1) {
			if (execute) {
				record(requestRef, operator, emailHash, null, OperatorAccountDeletionLog.AMBIGUOUS);
			}
			return new Result(Outcome.AMBIGUOUS, null, null, List.of(), accounts.size());
		}

		Map.Entry<UUID, List<String>> only = accounts.entrySet().iterator().next();
		UUID userId = only.getKey();
		AccountDeletionPreviewResponse preview = this.accountDeletionService.preview(userId);

		if (!execute) {
			return new Result(Outcome.DRY_RUN, userId, preview, only.getValue(), 1);
		}

		// 본인 탈퇴와 같은 코드. 확인 값은 운영자가 이미 사람으로서 확인했으므로 고정 문구를 넘기고, 비밀번호는 없다.
		this.accountDeletionService.delete(userId, AccountDeletionService.CONFIRMATION_PHRASE, null);
		record(requestRef, operator, emailHash, userId, OperatorAccountDeletionLog.DELETED);
		return new Result(Outcome.DELETED, userId, preview, only.getValue(), 1);
	}

	/**
	 * 이 이메일로 지금 쓸 수 있는 계정들. 이메일 가입({@code local_credential})과 연결된 소셜 계정({@code auth_identity}의
	 * 제공자 이메일) 둘 다 본다 — 소셜로만 가입한 사람은 이메일이 뒤쪽에만 있다.
	 *
	 * <p>이미 지운 계정({@link UserStatus#DELETED})은 뺀다. 계정 하나가 두 경로로 잡혀도 한 계정으로 센다.
	 */
	private Map<UUID, List<String>> findActiveAccounts(String email) {
		Map<UUID, List<String>> byUser = new LinkedHashMap<>();

		this.credentialRepository.findByEmail(email).map(LocalCredential::getUser).filter(OperatorAccountDeletionService::isActive)
				.ifPresent(user -> byUser.computeIfAbsent(user.getUserId(), id -> new java.util.ArrayList<>()).add("LOCAL"));

		for (AuthIdentity identity : this.identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull(email)) {
			AppUser user = identity.getUser();
			if (isActive(user)) {
				byUser.computeIfAbsent(user.getUserId(), id -> new java.util.ArrayList<>())
						.add(identity.getProvider().name());
			}
		}
		return byUser;
	}

	private static boolean isActive(AppUser user) {
		return user != null && user.getStatus() != UserStatus.DELETED;
	}

	private void record(String requestRef, String operator, String emailHash, UUID userId, String outcome) {
		this.logRepository.save(new OperatorAccountDeletionLog(requestRef.strip(), operator.strip(), emailHash, userId,
				outcome, this.clock.instant()));
	}

	/** 소문자로 내린 이메일의 SHA-256, 16진 64자. */
	static String sha256Hex(String normalizedEmail) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(normalizedEmail.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			// 모든 자바 실행 환경이 SHA-256 을 갖춘다. 여기 오면 환경이 깨진 것이다.
			throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
		}
	}

}
