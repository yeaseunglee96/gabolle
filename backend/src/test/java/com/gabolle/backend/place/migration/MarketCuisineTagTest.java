package com.gabolle.backend.place.migration;

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시장 음식 태그 목록이 손으로 가린 그대로인가. 목록은 이름에 「시장」이 든 곳을 사람이 한 줄씩
 * 보고 고른 것이라, 규칙으로 바꾸거나 늘리면 그 판단이 소리 없이 사라진다.
 *
 * <p>마이그레이션 파일을 글로 읽어 검사한다 — 이 마이그레이션은 {@code place} 와 조인하므로
 * 빈 시험 DB 에서는 0행을 넣어, 목록이 맞는지를 DB 검사로는 애초에 잴 수 없다.
 */
class MarketCuisineTagTest {

	private static final Path MIGRATION = Path.of(
			"src/main/resources/db/migration/V20260918160000__market_cuisine_tag.sql");

	/** 손으로 가린 결과. 이 수를 고치려면 무엇을 왜 넣고 뺐는지를 마이그레이션 머리말에 같이 적는다. */
	private static final int CURATED_PLACES = 51;

	/** {@code (UUID 'xxx'),   -- 이름} 한 줄에서 이름을 뽑는다. */
	private static final Pattern ROW = Pattern.compile("\\(UUID\\s+'([0-9a-fA-F-]{36})'\\)\\s*,?\\s*--\\s*(.+)");

	/**
	 * 이 낱말이 목록에 들어오면 빨개진다. 도매시장은 여행자가 갈 자리가 아니고, 전자상가는 음식과
	 * 무관하고, 박람회는 장소가 아니며, 횟집·식당·맛집은 상호에 「시장」이 든 가게다.
	 */
	private static final List<String> MUST_NOT_APPEAR = List.of("도매", "전자", "박람회", "횟집", "식당", "맛집");

	@Test
	@DisplayName("🔴 손으로 가린 시장이 51곳 그대로다 — 늘리거나 줄이려면 머리말에 이유를 같이 적는다")
	void curatedMarketCountIsPinned() {
		assertThat(rows()).as("""
				시장 목록의 개수가 달라졌습니다.

				이 목록은 이름에 「시장」이 든 60곳을 사람이 한 줄씩 보고 9곳을 뺀 결과입니다.
				늘리거나 줄이는 것 자체는 괜찮지만, **무엇을 왜 넣고 뺐는지**를 마이그레이션
				머리말에 같이 적고 이 숫자를 고쳐 주세요.""")
				.hasSize(CURATED_PLACES);
	}

	@Test
	@DisplayName("🔴 도매시장·전자상가·박람회·식당이 목록에 없다 — 이름으로 자동으로 붙였으면 들어갔을 것들이다")
	void excludedKindsStayExcluded() {
		List<String> offenders = new ArrayList<>();
		for (String name : rows()) {
			for (String banned : MUST_NOT_APPEAR) {
				if (name.contains(banned)) {
					offenders.add(name + "  (금지 낱말: " + banned + ")");
				}
			}
		}

		assertThat(offenders).as("""
				「시장」 음식 태그에 들어가면 안 되는 곳이 목록에 있습니다.

				도매시장은 상인이 새벽에 거래하는 곳이고, 전자상가는 음식과 무관하고,
				박람회는 장소가 아니며, 「…시장횟집」은 상호에 시장이 든 식당입니다.
				정말로 넣어야 한다면 이 검사의 MUST_NOT_APPEAR 와 그 이유를 함께 고치십시오.

				%s""".formatted(String.join("\n", offenders)))
				.isEmpty();
	}

	@Test
	@DisplayName("같은 장소가 두 번 적혀 있지 않다")
	void noDuplicatePlaces() {
		List<String> ids = ids();
		Set<String> unique = new LinkedHashSet<>(ids);

		assertThat(ids).as("같은 장소가 여러 번 적혀 있습니다 — 중복은 NOT EXISTS 가 막지만 목록이 거짓말을 하게 됩니다")
				.hasSameSizeAs(unique);
	}

	@Test
	@DisplayName("🔴 place 와 조인한다 — 안 그러면 빈 시험 DB 에서 외래키 위반으로 죽는다")
	void insertsByJoiningPlace() {
		String sql = text();

		assertThat(sql).as("place 와 조인하지 않으면 CI 의 빈 DB 에서 마이그레이션이 통째로 죽습니다")
				.contains("FROM place p")
				.contains("WHERE p.place_id IN");
	}

	@Test
	@DisplayName("두 번 돌려도 행이 안 는다 — 다시 돌리는 것이 안전해야 한다")
	void isSafeToRunTwice() {
		assertThat(text()).as("NOT EXISTS 가 없으면 다시 돌릴 때마다 같은 태그가 쌓입니다")
				.contains("NOT EXISTS");
	}

	// 파일 읽기

	private static List<String> rows() {
		List<String> names = new ArrayList<>();
		Matcher matcher = ROW.matcher(text());
		while (matcher.find()) {
			names.add(matcher.group(2).trim());
		}
		return names;
	}

	private static List<String> ids() {
		List<String> ids = new ArrayList<>();
		Matcher matcher = ROW.matcher(text());
		while (matcher.find()) {
			ids.add(matcher.group(1).toLowerCase());
		}
		return ids;
	}

	private static String text() {
		try {
			return Files.readString(MIGRATION, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("마이그레이션 파일을 못 읽었습니다: " + MIGRATION, e);
		}
	}
}
