package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.domain.ItineraryRun;
import com.gabolle.backend.itinerary.domain.ItineraryStopEvent;

/**
 * 손으로 쓴 upsert SQL 과 표 정의가 맞는지 본다 — S15P21E201-1325.
 *
 * <h2>이 검사가 막는 것</h2>
 * {@code JpaItineraryRunRepository} 의 {@code INSERT ... ON CONFLICT} 는 원시 SQL 이라
 * <b>컴파일러가 칸 이름을 봐 주지 않는다.</b> 마이그레이션에서 칸 이름을 바꾸면 그 어긋남은
 * 실행할 때까지 안 드러나고, 실행되는 자리는 사용자가 「출발」을 누르는 순간이다.
 *
 * <p>{@link ItineraryItemActualSqlMatchesMigrationTest} 와 같은 갈래다 — 그쪽 주석이
 * 이 검사가 무엇이 <b>아닌지</b>도 적어 두었다. DB 검증이 아니다.
 *
 * <p>여기에 하나 더 본다: 표의 {@code CHECK} 에 적힌 이름들과 자바 {@code enum} 이 같은가.
 * 둘은 <b>같은 사실</b>인데 두 파일에 나뉘어 적혀 있어서, 한쪽에만 값을 더하면 그 값을 쓰는
 * 순간 제약 위반이 난다.
 */
class ItineraryRunSqlMatchesMigrationTest {

	private static final Path MIGRATION = Path.of(
			"src/main/resources/db/migration/V20260919100000__itinerary_run.sql");

	private static final Path REPOSITORY = Path.of(
			"src/main/java/com/gabolle/backend/itinerary/infra/JpaItineraryRunRepository.java");

	@Test
	@DisplayName("INSERT 에 적은 칸이 모두 itinerary_run 에 있다")
	void insertColumnsExistInTheTable() throws IOException {
		Set<String> tableColumns = tableColumns("itinerary_run");
		Set<String> insertColumns = insertColumns();

		// 파서가 아무것도 못 찾았는데 초록이 되는 것을 막는다 — 빈 집합끼리는 항상 맞는다.
		assertThat(tableColumns).hasSize(6);
		assertThat(insertColumns).hasSize(6);

		assertThat(tableColumns).containsAll(insertColumns);
	}

	@Test
	@DisplayName("🔴 ON CONFLICT 대상이 표의 기본키와 같다 — 다르면 덮어쓰기가 아니라 오류가 난다")
	void conflictTargetIsThePrimaryKey() throws IOException {
		// 🔴 파일 전체가 아니라 SQL 덩이만 본다. 지금은 주석에 괄호가 없어 우연히 맞지만,
		//    누가 주석에 「ON CONFLICT (itinerary_id)」라고 적는 순간 이 검사가 그걸 잡는다.
		Matcher matcher = Pattern.compile("ON CONFLICT\\s*\\(([^)]*)\\)").matcher(upsertSql());

		assertThat(matcher.find()).isTrue();
		assertThat(matcher.group(1).trim()).isEqualTo("itinerary_id");
		assertThat(read(MIGRATION)).contains("itinerary_id       UUID        PRIMARY KEY");
	}

	/**
	 * 🔴 {@code started_at} 은 덮어쓰지 않는다. 중지했다 다시 출발했다고 「언제 시작했나」가
	 * 바뀌면, 그 여행이 몇 시에 시작했는지 아무도 못 답한다.
	 */
	@Test
	@DisplayName("🔴 DO UPDATE 가 started_at 을 안 건드린다")
	void doUpdateKeepsStartedAt() throws IOException {
		// 🔴 SQL 덩이를 먼저 집는다. 파일 전체에서 「DO UPDATE」를 찾으면 **주석에 적힌 말**이
		//    먼저 걸린다 — 처음 그렇게 썼다가 이 시험이 자기 설명문을 검사했다.
		String doUpdate = upsertSql().split("DO UPDATE")[1];

		assertThat(doUpdate).doesNotContain("started_at");
		assertThat(doUpdate).contains("status").contains("current_stop_index").contains("updated_at");
	}

	/** {@code UPSERT} 상수 안의 SQL 만. 주석은 안 들어온다. */
	private static String upsertSql() throws IOException {
		String repository = read(REPOSITORY);
		int start = repository.indexOf("INSERT INTO itinerary_run");
		assertThat(start).as("upsert SQL 덩이를 못 찾았다").isGreaterThan(0);
		int end = repository.indexOf("\"\"\"", start);
		assertThat(end).as("SQL 덩이의 끝을 못 찾았다").isGreaterThan(start);
		return repository.substring(start, end);
	}

	@Test
	@DisplayName("🔴 표의 CHECK 와 자바 enum 이 같은 이름을 쓴다 — 한쪽에만 더하면 쓰는 순간 제약 위반이 난다")
	void checkConstraintsMatchTheEnums() throws IOException {
		String migration = read(MIGRATION);

		assertThat(namesInCheck(migration, "ck_itinerary_run_status"))
				.containsExactlyInAnyOrderElementsOf(names(ItineraryRun.Status.values()));
		assertThat(namesInCheck(migration, "ck_itinerary_stop_event_type"))
				.containsExactlyInAnyOrderElementsOf(names(ItineraryStopEvent.Type.values()));
	}

	private static List<String> names(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).toList();
	}

	/** {@code CONSTRAINT <이름> CHECK (... IN ('A', 'B'))} 안의 홑따옴표 이름들. */
	private static List<String> namesInCheck(String migration, String constraint) {
		Matcher matcher = Pattern.compile(constraint + "[\\s\\S]*?IN\\s*\\(([^)]*)\\)").matcher(migration);
		assertThat(matcher.find()).as("제약 %s 을 못 찾았다", constraint).isTrue();
		Matcher names = Pattern.compile("'([A-Z_]+)'").matcher(matcher.group(1));
		List<String> found = new java.util.ArrayList<>();
		while (names.find()) {
			found.add(names.group(1));
		}
		assertThat(found).as("제약 %s 에서 이름을 하나도 못 읽었다", constraint).isNotEmpty();
		return found;
	}

	private static Set<String> tableColumns(String table) throws IOException {
		Matcher matcher = Pattern.compile("CREATE TABLE " + table + "\\s*\\(([\\s\\S]*?)\\n\\);").matcher(read(MIGRATION));
		assertThat(matcher.find()).as("표 %s 을 못 찾았다", table).isTrue();

		Set<String> columns = new LinkedHashSet<>();
		for (String line : matcher.group(1).split("\n")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("CONSTRAINT")) {
				continue;
			}
			Matcher name = Pattern.compile("^([a-z_]+)\\s").matcher(trimmed);
			if (name.find()) {
				columns.add(name.group(1));
			}
		}
		return columns;
	}

	private static Set<String> insertColumns() throws IOException {
		Matcher matcher = Pattern.compile("INSERT INTO itinerary_run\\s*\\n\\s*\\(([\\s\\S]*?)\\)").matcher(upsertSql());
		assertThat(matcher.find()).isTrue();

		Set<String> columns = new LinkedHashSet<>();
		for (String raw : matcher.group(1).split(",")) {
			String name = raw.trim();
			if (!name.isEmpty()) {
				columns.add(name);
			}
		}
		return columns;
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
