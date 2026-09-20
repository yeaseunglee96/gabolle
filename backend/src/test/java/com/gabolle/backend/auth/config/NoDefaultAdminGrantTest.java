package com.gabolle.backend.auth.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserRole;
import com.gabolle.backend.user.domain.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 어떤 설정값으로도 운영자 권한이 저절로 생기지 않는다.
 *
 * <p>값을 주지 않았을 때 편의로 권한이 열리는 자리는 코드가 아니라 배포 설정 파일에 조용히
 * 생긴다. {@code ...admin-emails=${GABOLLE_ADMIN_EMAILS:boss@example.com}} 같은 한 줄은 어떤
 * 통합 검사도 빨갛게 만들지 않는다. 그래서 설정 파일을 직접 읽어서 본다.
 *
 * <p>없는 것을 세는 검사는 아무것도 못 읽었을 때도 초록이 된다. 그래서 파일을 읽었는지,
 * 검사 대상 속성이 실제로 그 파일에 있는지 단정하고, {@link #theDetectorActuallyFires} 가
 * 일부러 심은 위험한 설정을 같은 판정 함수에 넣어 걸리는지 확인한다.
 */
class NoDefaultAdminGrantTest {

	private static final Path RESOURCES = Path.of("src/main/resources");

	private static final Path MAIN_SOURCES = Path.of("src/main/java");

	/** 이 이름 조각이 키에 있으면 "권한을 주는 설정" 으로 보고 기본값이 비어 있어야 한다고 본다. */
	private static final List<String> PRIVILEGE_KEY_HINTS = List.of("admin", "role", "superuser", "operator");

	/** {@code ${VAR:기본값}} 에서 기본값만 꺼낸다. 기본값이 없으면 두 번째 그룹이 {@code null} 이다. */
	private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([^:}]+)(?::(.*))?\\}$");

	@Test
	@DisplayName("🔴 배포 프로필에 운영자를 여는 기본값이 없다")
	void theDeployProfileGrantsAdminToNobodyByDefault() throws IOException {
		Map<String, String> properties = read("application-dev.properties");

		// 파싱이 빈 결과를 내면 아래 확인이 전부 무의미해진다. 반드시 있어야 하는 키로 하한을 박는다.
		assertThat(properties).as("application-dev.properties 를 읽지 못했다").isNotEmpty();
		assertThat(properties)
				.as("배포 프로필의 정본이 맞는지 — jwt-secret 이 여기 있어야 한다")
				.containsKey("gabolle.auth.jwt-secret");
		assertThat(properties)
				.as("운영자 목록 속성이 이 파일에 없으면 이 검사는 아무것도 보지 않는다")
				.containsKey("gabolle.auth.admin-emails");

		assertThat(properties.get("gabolle.auth.admin-emails"))
				.as("""
						운영자 목록은 환경변수로만 채워야 하고, 값이 없을 때의 기본값은 비어 있어야 합니다.
						여기에 계정을 하나라도 적어 두면 그 배포는 아무 확인 없이 운영자를 갖습니다.""")
				.isEqualTo("${GABOLLE_ADMIN_EMAILS:}");
	}

	@Test
	@DisplayName("🔴 어떤 프로필의 어떤 속성도 계정을 운영자로 만들지 않는다")
	void noPropertyInAnyProfileGrantsAdmin() throws IOException {
		List<String> files = List.of("application.properties", "application-dev.properties",
				"application-no-db.properties");

		List<String> offenders = new ArrayList<>();
		int examined = 0;
		for (String file : files) {
			Map<String, String> properties = read(file);
			assertThat(properties).as("%s 를 읽지 못했다", file).isNotEmpty();
			examined += properties.size();
			for (String key : offendingKeys(properties)) {
				offenders.add(file + " → " + key);
			}
		}

		assertThat(examined)
				.as("속성을 이만큼도 못 읽었다면 파일 목록이나 경로가 틀렸다")
				.isGreaterThan(30);
		assertThat(offenders)
				.as("""
						권한을 여는 기본값이 배포 설정에 들어 있습니다.

						%s

						운영자 지정은 환경변수로만 하고, 값이 없을 때는 아무도 운영자가 아닌 상태로
						떠야 합니다. 그것이 이 저장소의 정상 상태입니다.""".formatted(String.join("\n", offenders)))
				.isEmpty();
	}

	@Test
	@DisplayName("🔴 판정 함수가 실제로 작동한다 — 위험한 설정을 심어 걸리는지 본다")
	void theDetectorActuallyFires() {
		// 위 두 검사는 "걸린 것이 없다" 를 확인한다. 판정 함수가 아무것도 못 잡는 상태여도
		// 똑같이 초록이 되므로, 여기서 일부러 심은 두 줄이 걸리는지 본다.
		Map<String, String> planted = new LinkedHashMap<>();
		planted.put("gabolle.auth.admin-emails", "${GABOLLE_ADMIN_EMAILS:boss@example.com}");
		planted.put("gabolle.auth.signup-default-role", "ADMIN");
		planted.put("spring.application.name", "gabolle-backend");

		assertThat(offendingKeys(planted))
				.containsExactlyInAnyOrder("gabolle.auth.admin-emails", "gabolle.auth.signup-default-role");
	}

	@Test
	@DisplayName("🔴 운영자로 올리는 코드가 기동 동기화 한 곳에만 있다")
	void onlyTheStartupSynchronizerGrantsAdmin() throws IOException {
		Set<String> callers = sourceFilesContaining("grantAdmin(");

		assertThat(callers)
				.as("호출하는 곳을 하나도 못 찾았다 — 검색이 실패했거나 메서드 이름이 바뀌었다")
				.isNotEmpty();
		assertThat(callers)
				.as("""
						운영자 권한을 주는 코드가 기동 시점 동기화 밖에 생겼습니다.

						권한을 주는 경로가 늘어나면 "누가 운영자인가" 의 근거가 배포 설정 밖으로
						흩어집니다. HTTP 로 올리는 경로는 그 자체가 새 공격면이기도 합니다.
						정말 필요하면 이 목록과 함께 그 이유를 남겨 주세요.""")
				.containsExactlyInAnyOrder("AppUser.java", "AdminRoleStartupSynchronizer.java");
	}

	@Test
	@DisplayName("🔴 가입으로는 운영자가 되지 않는다")
	void registrationNeverProducesAnAdmin() {
		AppUser user = AppUser.register("여행자", "KO", Instant.now(), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE);

		assertThat(user.getRole()).isEqualTo(UserRole.USER);
	}

	/**
	 * 권한을 여는 기본값을 가진 키.
	 *
	 * <p>두 가지를 본다. 이름이 권한을 가리키는 키({@link #PRIVILEGE_KEY_HINTS})가 비어 있지 않은
	 * 기본값을 갖는 것, 그리고 어떤 키든 기본값이 역할 이름 {@code ADMIN} 을 담고 있는 것이다.
	 *
	 * <p>판정에 쓰는 것은 기본값이지 원문이 아니다. {@code ${GABOLLE_ADMIN_EMAILS:}} 는 변수
	 * 이름에 {@code ADMIN} 이 들어 있지만 기본값이 비어 있으므로 문제가 아니다.
	 */
	private static Set<String> offendingKeys(Map<String, String> properties) {
		Set<String> offenders = new LinkedHashSet<>();
		properties.forEach((key, value) -> {
			String fallback = defaultValueOf(value);
			boolean privilegeKeyHasDefault = PRIVILEGE_KEY_HINTS.stream().anyMatch(key.toLowerCase()::contains)
					&& !fallback.isBlank();
			if (privilegeKeyHasDefault || fallback.contains(UserRole.ADMIN.name())) {
				offenders.add(key);
			}
		});
		return offenders;
	}

	/**
	 * 환경변수가 없을 때 실제로 쓰이는 값.
	 *
	 * <p>{@code ${VAR:x}} → {@code x}, {@code ${VAR}} → 빈 문자열(값이 없으면 기동이 못 뜨므로
	 * 권한이 열릴 여지가 없다), 자리표시자가 아닌 값 → 그 값 자체.
	 */
	private static String defaultValueOf(String value) {
		Matcher matcher = PLACEHOLDER.matcher(value.trim());
		if (!matcher.matches()) {
			return value.trim();
		}
		String fallback = matcher.group(2);
		return fallback == null ? "" : fallback.trim();
	}

	/**
	 * {@code src/main/java} 아래에서 이 조각을 담고 있는 파일 이름.
	 *
	 * <p>주석은 걷어내지 않는다 — 주석에서 이름을 언급하는 것도 "그 파일이 이 결정에 관여한다"
	 * 는 뜻이라, 목록에 올라오는 편이 안전한 방향이다.
	 */
	private static Set<String> sourceFilesContaining(String fragment) throws IOException {
		Set<String> matches = new TreeSet<>();
		try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				if (Files.readString(file, StandardCharsets.UTF_8).contains(fragment)) {
					matches.add(file.getFileName().toString());
				}
			}
		}
		return matches;
	}

	/**
	 * {@code .properties} 를 Spring 이 읽는 방식과 같게 읽는다 — Java 규격상 ISO-8859-1 이다.
	 * 주석은 {@link Properties} 가 알아서 버린다.
	 */
	private static Map<String, String> read(String fileName) throws IOException {
		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(RESOURCES.resolve(fileName))) {
			properties.load(in);
		}
		Map<String, String> values = new LinkedHashMap<>();
		properties.stringPropertyNames().forEach(key -> values.put(key, properties.getProperty(key)));
		return values;
	}
}
