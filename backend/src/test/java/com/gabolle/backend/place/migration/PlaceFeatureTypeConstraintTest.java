package com.gabolle.backend.place.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 피처 갈래 목록은 줄어들지 않는다 — S15P21E201-852.
 *
 * <h2>🔴 이 검사가 막는 실수</h2>
 * {@code ck_place_feature_type} 은 갈래를 더할 때마다 <b>목록 전체를 다시 쓴다</b>
 * ({@code DROP CONSTRAINT} 뒤 {@code ADD CONSTRAINT}). 그래서 새 마이그레이션을 쓰는 사람이
 * <b>바로 앞 파일이 아니라 그 앞의 파일에서 목록을 복사하면</b> 그 사이에 들어온 갈래가
 * 조용히 금지된다.
 *
 * <p>실제로 이 티켓에서 그렇게 썼다. {@code V20260907160000} 에서 목록을 옮겨 와서
 * {@code V20260909020000} 이 더한 {@code SOLO_FRIENDLY} · {@code BREAK_TIME} ·
 * {@code LAST_ORDER_TIME} 셋이 빠졌다. 그 상태로 머지되면 어떻게 되나 —
 *
 * <ul>
 * <li>그 갈래의 행이 <b>이미 있으면</b> 배포가 이 마이그레이션에서 멈춘다. 그건 그나마 낫다</li>
 * <li>행이 <b>아직 없으면</b> 아무 일도 일어나지 않는다. 그리고 나중에 그 갈래를 넣는 적재가
 *     실패하는데, 그때 원인이 몇 달 전 마이그레이션이라는 것을 아무도 짚지 못한다</li>
 * </ul>
 *
 * <p>🔴 <b>DB 없이 돈다.</b> 마이그레이션 파일을 글로 읽어 목록을 뽑는다 — DB 를 띄워야
 * 하는 검사로 만들면 노트북에 PostgreSQL 이 없는 사람에게는 CI 에서 처음 돌고, 이 종류의
 * 실수는 코드를 쓰는 그 자리에서 잡혀야 한다.
 */
class PlaceFeatureTypeConstraintTest {

	private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

	/** 이 문장 뒤에 목록이 온다. 파일을 글로 찾는다 — 정규식보다 낡을 여지가 적다. */
	private static final String ADD_CONSTRAINT = "ADD CONSTRAINT ck_place_feature_type";

	/** 갈래 목록 안에 적힌 낱말. 정규식에 역슬래시를 안 쓰려고 이것 하나만 쓴다. */
	private static final Pattern QUOTED = Pattern.compile("'([A-Z_]+)'");

	@Test
	@DisplayName("🔴 나중 마이그레이션의 갈래 목록은 앞선 것을 전부 담는다 — 하나라도 빠지면 그 갈래가 조용히 금지된다")
	void everyLaterListContainsAllEarlierValues() {
		List<Definition> definitions = definitionsInFileOrder();
		assertThat(definitions)
				.as("ck_place_feature_type 을 정의하는 마이그레이션을 하나도 못 찾았다 — 정규식이 낡았다")
				.hasSizeGreaterThan(1);

		Set<String> seen = new LinkedHashSet<>();
		for (Definition definition : definitions) {
			Set<String> missing = new LinkedHashSet<>(seen);
			missing.removeAll(definition.values());
			assertThat(missing)
					.as("%s 가 앞선 마이그레이션의 갈래를 빠뜨렸다. 바로 앞 파일의 목록을 그대로 옮긴 뒤 새 값만 더해야 한다",
							definition.fileName())
					.isEmpty();
			seen.addAll(definition.values());
		}
	}

	@Test
	@DisplayName("마지막 목록에 이 저장소가 실제로 넣는 갈래가 다 있다")
	void theLatestListCoversWhatTheLoadersWrite() {
		List<Definition> definitions = definitionsInFileOrder();
		Set<String> latest = definitions.get(definitions.size() - 1).values();

		assertThat(latest).contains(
				// 장소 적재가 쓰는 것
				"INTEREST_TAG",
				// 인기도 적재
				"POPULARITY_SCORE",
				// 영업시간 적재 (S15P21E201-852)
				"OPENING_HOURS", "CHECK_IN_OUT",
				// 혼밥 안심·시각 사실 (S15P21E201-265)
				"SOLO_FRIENDLY", "BREAK_TIME", "LAST_ORDER_TIME");
	}

	private record Definition(String fileName, Set<String> values) {
	}

	/** 파일 이름이 판 번호라 이름순이 곧 적용 순서다 — Flyway 가 같은 순서로 돌린다. */
	private static List<Definition> definitionsInFileOrder() {
		List<Definition> found = new ArrayList<>();
		try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
			for (Path file : files.sorted().toList()) {
				String sql = Files.readString(file, StandardCharsets.UTF_8);
				int from = 0;
				while (true) {
					int at = sql.indexOf(ADD_CONSTRAINT, from);
					if (at < 0) {
						break;
					}
					// 한 문장은 세미콜론에서 끝난다. 그 안의 낱말만 목록으로 본다.
					int until = sql.indexOf(';', at);
					String statement = (until < 0) ? sql.substring(at) : sql.substring(at, until);
					Set<String> values = new LinkedHashSet<>();
					Matcher value = QUOTED.matcher(statement);
					while (value.find()) {
						values.add(value.group(1));
					}
					found.add(new Definition(file.getFileName().toString(), values));
					from = at + ADD_CONSTRAINT.length();
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("마이그레이션 폴더를 읽을 수 없다: " + MIGRATION_DIR, ex);
		}
		return found;
	}
}
