package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 손으로 쓴 upsert SQL 의 칸 이름이 표 정의와 맞는지 본다.
 * {@code JpaItineraryItemActualRepository} 의 {@code INSERT ... ON CONFLICT} 는 원시 SQL
 * 이라 컴파일러가 칸 이름을 봐 주지 않고, 마이그레이션과 어긋나도 실행할 때까지 드러나지
 * 않는다.
 *
 * <p>DB 검증은 아니다. SQL 이 실제로 도는지·{@code ON CONFLICT} 가 정말 덮어쓰는지는
 * {@link ItineraryActualTimeIntegrationTest} 가 본다.
 */
class ItineraryItemActualSqlMatchesMigrationTest {

	private static final Path MIGRATION = Path.of(
			"src/main/resources/db/migration/V20260907170000__moderation_visit_and_reviews.sql");

	private static final Path REPOSITORY = Path.of(
			"src/main/java/com/gabolle/backend/itinerary/infra/JpaItineraryItemActualRepository.java");

	private static final String TABLE = "itinerary_item_actual";

	@Test
	@DisplayName("INSERT 에 적은 칸이 모두 표에 있다 — 원시 SQL 의 칸 이름은 컴파일러가 안 봐 준다")
	void insertColumnsExistInTheTable() throws IOException {
		Set<String> tableColumns = tableColumns();
		Set<String> insertColumns = insertColumns();

		// 파서가 아무것도 못 찾았는데 초록이 되는 것을 막는다 — 빈 집합끼리는 항상 맞는다.
		assertThat(tableColumns).hasSize(8);
		assertThat(insertColumns).hasSize(8);

		assertThat(tableColumns).containsAll(insertColumns);
	}

	@Test
	@DisplayName("ON CONFLICT 대상이 표의 UNIQUE 와 같은 칸이다 — 다르면 덮어쓰기가 아니라 오류가 난다")
	void conflictTargetMatchesTheUniqueConstraint() throws IOException {
		List<String> unique = columnsInside(read(MIGRATION),
				Pattern.compile("UNIQUE\\s*\\(([^)]*)\\)"));
		List<String> conflictTarget = columnsInside(read(REPOSITORY),
				Pattern.compile("ON CONFLICT\\s*\\(([^)]*)\\)"));

		assertThat(unique).containsExactly("itinerary_id", "item_key");
		assertThat(conflictTarget).isEqualTo(unique);
	}

	@Test
	@DisplayName("덮어쓸 때 고치는 칸은 넷이고 created_at 은 그대로 둔다 — 처음 적은 시각을 잃지 않는다")
	void updateSetTouchesTheRightColumns() throws IOException {
		Set<String> updated = new LinkedHashSet<>();
		Matcher matcher = Pattern.compile("(\\w+)\\s*=\\s*EXCLUDED\\.(\\w+)").matcher(read(REPOSITORY));
		while (matcher.find()) {
			// 왼쪽 칸과 오른쪽 칸의 이름이 다르면 값이 엉뚱한 자리에 들어간다.
			assertThat(matcher.group(1)).isEqualTo(matcher.group(2));
			updated.add(matcher.group(1));
		}

		assertThat(updated).containsExactly("arrived_at", "departed_at", "recorded_by", "updated_at");
		assertThat(updated).doesNotContain("created_at");
		assertThat(tableColumns()).containsAll(updated);
	}

	/** {@code CREATE TABLE itinerary_item_actual (...)} 안의 칸 이름. */
	private static Set<String> tableColumns() throws IOException {
		String migration = read(MIGRATION);
		int start = migration.indexOf("CREATE TABLE " + TABLE);
		assertThat(start).as("마이그레이션에서 %s 표 정의를 못 찾았다", TABLE).isNotNegative();

		String definition = migration.substring(start, migration.indexOf(");", start));
		Set<String> columns = new LinkedHashSet<>();
		for (String line : definition.split("\n")) {
			String trimmed = line.trim();
			// 주석·제약·표 이름 줄은 칸이 아니다.
			if (trimmed.startsWith("--") || trimmed.startsWith("CONSTRAINT") || trimmed.startsWith("CREATE")) {
				continue;
			}
			Matcher matcher = Pattern.compile("^(\\w+)\\s+(UUID|TIMESTAMPTZ)").matcher(trimmed);
			if (matcher.find()) {
				columns.add(matcher.group(1));
			}
		}
		return columns;
	}

	/** upsert SQL 의 {@code INSERT INTO ... ( ... )} 칸 목록. */
	private static Set<String> insertColumns() throws IOException {
		String source = read(REPOSITORY);
		int start = source.indexOf("INSERT INTO " + TABLE);
		assertThat(start).as("저장소에서 %s INSERT 를 못 찾았다", TABLE).isNotNegative();

		String columnList = source.substring(source.indexOf('(', start) + 1, source.indexOf(')', start));
		Set<String> columns = new LinkedHashSet<>();
		for (String raw : columnList.split(",")) {
			String name = raw.replace("\n", " ").trim();
			if (!name.isEmpty()) {
				columns.add(name);
			}
		}
		return columns;
	}

	private static List<String> columnsInside(String text, Pattern pattern) {
		Matcher matcher = pattern.matcher(text);
		assertThat(matcher.find()).as("%s 를 못 찾았다", pattern.pattern()).isTrue();
		return java.util.Arrays.stream(matcher.group(1).split(","))
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.toList();
	}

	/**
	 * UNIQUE·ON CONFLICT 를 찾을 때 파일 전체가 아니라 이 표의 구간만 봐야 하므로,
	 * 두 파일 모두 {@code itinerary_item_actual} 이 처음 나오는 자리부터 읽는다.
	 * 마이그레이션에는 다른 표의 UNIQUE 도 있어서 그냥 첫 UNIQUE 를 잡으면 엉뚱한 표를 본다.
	 */
	private static String read(Path path) throws IOException {
		String content = Files.readString(path, StandardCharsets.UTF_8);
		int start = content.indexOf("CREATE TABLE " + TABLE);
		if (start < 0) {
			start = content.indexOf("INSERT INTO " + TABLE);
		}
		assertThat(start).as("%s 에서 %s 구간을 못 찾았다", path, TABLE).isNotNegative();
		return content.substring(start);
	}
}
