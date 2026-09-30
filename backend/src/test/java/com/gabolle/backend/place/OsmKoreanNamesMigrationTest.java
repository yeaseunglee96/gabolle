package com.gabolle.backend.place;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.gabolle.backend.place.loader.OsmPlaceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OSM 장소 이름을 한국어로 고치는 마이그레이션(S15P21E201-1882)의 장소 번호를 본다. DB 없이 돈다.
 *
 * <p>번호는 생성 스크립트가 {@code UUID.nameUUIDFromBytes} 를 흉내 내 계산했다. 한 바이트만 달라도 UPDATE 가 아무 행도 못 찾고
 * 조용히 끝난다 — 운영에서 러시아어 이름이 그대로 남는데 아무도 모른다. 그래서 적재기의 {@link OsmPlaceLoader#placeIdOf} 로 다시 센다.
 */
class OsmKoreanNamesMigrationTest {

	private static final Pattern ROW = Pattern.compile(
			"^\\s+\\('([0-9a-f-]{36})'::uuid, '((?:[^']|'')*)', '((?:[^']|'')*)', (?:'(?:[^']|'')*'|NULL)\\),?\\s+-- osm node (\\d+)$",
			Pattern.MULTILINE);

	private static String sql() throws IOException {
		Resource[] found = new PathMatchingResourcePatternResolver()
				.getResources("classpath*:db/migration/V*__osm_korean_names.sql");
		assertThat(found).hasSize(1);
		return found[0].getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("🔴 모든 행의 장소 번호가 적재기의 번호와 같다 — 어긋나면 UPDATE 가 조용히 아무것도 안 한다")
	void placeIdsMatchTheLoader() throws IOException {
		Matcher m = ROW.matcher(sql());
		List<String> mismatched = new ArrayList<>();
		int rows = 0;
		while (m.find()) {
			rows++;
			String expected = OsmPlaceLoader.placeIdOf(Long.parseLong(m.group(4))).toString();
			if (!expected.equals(m.group(1))) {
				mismatched.add(m.group(4));
			}
		}
		assertThat(rows).as("정규식이 어긋나 0행을 보고 통과하지 않게").isGreaterThan(40);
		assertThat(mismatched).isEmpty();
	}

	@Test
	@DisplayName("사용자가 본 그 곳 — 「Храм святой Богородицы」가 「정교회」로")
	void theOrthodoxChurch() throws IOException {
		String sql = sql();
		assertThat(sql).contains("'" + OsmPlaceLoader.placeIdOf(6680782685L) + "'::uuid, 'Храм святой Богородицы', '정교회', 'Orthodox Church'");
	}
}
