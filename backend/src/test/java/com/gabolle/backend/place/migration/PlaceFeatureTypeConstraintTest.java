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
 * 피처 갈래 목록은 줄어들지 않는다. {@code ck_place_feature_type} 은 갈래를 더할 때마다
 * {@code DROP CONSTRAINT} 뒤 {@code ADD CONSTRAINT} 로 목록 전체를 다시 쓰므로, 바로 앞 파일이
 * 아니라 그보다 앞선 파일에서 목록을 복사하면 그 사이에 들어온 갈래가 조용히 금지된다.
 *
 * <p>마이그레이션 파일을 글로 읽어 목록을 뽑는다 — DB 가 없어도 돈다.
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
				// 영업시간 적재
				"OPENING_HOURS", "CHECK_IN_OUT",
				// 혼밥 안심·시각 사실
				"SOLO_FRIENDLY", "BREAK_TIME", "LAST_ORDER_TIME",
				// 장소 상세 사실 적재(PlaceDetailExtrasLoader) — 공인 표식까지
				"MENU_ITEMS", "FOREIGN_MENU", "AMENITIES", "ADMISSION_FEE", "NEARBY_LANDMARK", "BEST_TIME",
				"RECOGNITION");
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
