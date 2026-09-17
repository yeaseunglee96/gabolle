package com.gabolle.backend.auth.service;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import com.gabolle.backend.auth.service.AccountDeletionService.OwnedRows;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴 삭제 목록({@link AccountDeletionService#USER_OWNED_ROWS})이 <b>실재하는 것만</b> 가리키는지
 * 본다 — S15P21E201-1157.
 *
 * <h2>🔴 이 시험이 막는 것</h2>
 *
 * 그 목록은 엔티티 이름과 칸 경로를 <b>문자열로</b> 들고 있고, JPQL 은 그것을 <b>실행할 때</b>
 * 비로소 검사한다. 오타가 나면 컴파일은 통과하고 <b>탈퇴가 500 으로 죽는 순간에</b> 처음 드러난다.
 * 그 자리는 사용자가 계정을 지우는 자리라 가장 늦게, 가장 나쁘게 드러나는 자리다.
 *
 * <p>여기서 <b>DB 없이</b> 같은 것을 먼저 잡는다. 실제 JPQL 실행은
 * {@code AccountDeletionIntegrationTest} 가 진짜 PostgreSQL 에서 하지만, 그 시험은 도커가 없는
 * 곳에서 <b>실패가 아니라 건너뜀</b>이 된다 — 그래서 이쪽이 필요하다.
 *
 * <p>🔴 이 시험은 <b>"목록이 빠짐없는가" 는 보지 않는다.</b> 그건 {@code pg_constraint} 를 읽어야
 * 알 수 있고 다음 티켓의 CI 검사가 할 일이다. 여기는 <b>"적어 둔 것이 실재하는가" 까지</b>다.
 */
class AccountDeletionOwnedRowsTest {

	/** 엔티티 이름 → 그 클래스. {@code @Entity(name = ...)} 가 있으면 그 이름이 JPQL 이름이다. */
	private static final Map<String, Class<?>> ENTITIES = scanEntities();

	@Test
	@DisplayName("🔴 목록의 엔티티 이름이 전부 실재한다 — 오타면 탈퇴가 500 으로 죽는다")
	void everyEntityNameResolves() {
		List<String> missing = AccountDeletionService.USER_OWNED_ROWS.stream()
				.map(OwnedRows::entityName)
				.distinct()
				.filter(name -> !ENTITIES.containsKey(name))
				.toList();

		assertThat(missing)
				.as("JPQL 이 쓰는 엔티티 이름인데 @Entity 가 없다 — 아는 이름: %s", ENTITIES.keySet())
				.isEmpty();
	}

	@Test
	@DisplayName("🔴 목록의 칸 경로가 전부 실재한다 — 묻힌 키(key.x)와 관계(existingUser.userId)까지")
	void everyUserFieldPathResolves() {
		List<String> broken = AccountDeletionService.USER_OWNED_ROWS.stream()
				.filter(owned -> !pathResolves(ENTITIES.get(owned.entityName()), owned.userField()))
				.map(owned -> owned.entityName() + "." + owned.userField())
				.toList();

		assertThat(broken).as("그 엔티티에 없는 칸을 가리킨다").isEmpty();
	}

	@Test
	@DisplayName("🔴 같은 표를 두 번 적지 않는다 — 단, 사람을 가리키는 칸이 둘인 표는 예외다")
	void noAccidentalDuplicates() {
		// 팔로우·차단은 칸이 둘이라 같은 엔티티가 두 줄 나오는 것이 맞다. 그것 말고 같은
		// (엔티티, 칸) 짝이 두 번 나오면 그건 실수다 — 지우는 일이 두 번 도는 것은 해롭지
		// 않지만, 목록을 읽는 사람이 "왜 두 번이지" 를 매번 다시 확인해야 한다.
		List<String> pairs = AccountDeletionService.USER_OWNED_ROWS.stream()
				.map(owned -> owned.entityName() + "." + owned.userField())
				.toList();

		assertThat(pairs).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("🔴 사람을 가리키는 칸이 둘인 표는 두 칸을 다 지운다")
	void twoSidedTablesCoverBothSides() {
		// 한쪽만 지우면 "내가 없는데 나를 팔로우한 기록" 이 남는다. 그 상태는 탈퇴한 사람의
		// 흔적이 남의 화면에 남는다는 뜻이다.
		Map<String, Set<String>> fieldsByEntity = AccountDeletionService.USER_OWNED_ROWS.stream()
				.collect(Collectors.groupingBy(OwnedRows::entityName,
						Collectors.mapping(OwnedRows::userField, Collectors.toSet())));

		assertThat(fieldsByEntity.get("UserFollow"))
				.containsExactlyInAnyOrder("key.followerUserId", "key.followeeUserId");
		assertThat(fieldsByEntity.get("UserBlock"))
				.containsExactlyInAnyOrder("key.blockerUserId", "key.blockedUserId");
	}

	/** {@code a.b.c} 를 한 칸씩 따라가며 그 필드가 실재하는지 본다. */
	private boolean pathResolves(Class<?> entity, String path) {
		if (entity == null) {
			return false;
		}
		Class<?> current = entity;
		for (String segment : path.split("\\.")) {
			Field field = findField(current, segment);
			if (field == null) {
				return false;
			}
			current = field.getType();
		}
		return true;
	}

	private Field findField(Class<?> type, String name) {
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			try {
				return c.getDeclaredField(name);
			}
			catch (NoSuchFieldException ignored) {
				// 상위 클래스로 계속 올라간다
			}
		}
		return null;
	}

	private static Map<String, Class<?>> scanEntities() {
		ClassPathScanningCandidateComponentProvider scanner =
				new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

		Map<String, Class<?>> found = new HashMap<>();
		scanner.findCandidateComponents("com.gabolle.backend").forEach(definition -> {
			try {
				Class<?> type = Class.forName(definition.getBeanClassName());
				Entity annotation = type.getAnnotation(Entity.class);
				String name = (annotation != null && !annotation.name().isBlank())
						? annotation.name()
						: type.getSimpleName();
				found.put(name, type);
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException("스캔은 찾았는데 로드가 안 된다: " + definition, ex);
			}
		});
		return Map.copyOf(found);
	}
}
