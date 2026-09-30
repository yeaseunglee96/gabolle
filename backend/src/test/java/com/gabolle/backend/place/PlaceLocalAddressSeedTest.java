package com.gabolle.backend.place;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 다국어 주소 마이그레이션(S15P21E201-1876)에 실린 주소 글자 자체를 본다. DB 없이 돈다.
 *
 * <p>관광공사 원본에는 주소가 한국어 그대로인 행, 시 이름이 「プサン広域市」 「釜山広域地」(오타)로 섞인 행, 간체에 번체
 * 「釜山廣域市」가 섞인 행이 있었다. 생성 스크립트가 거르고 맞춘 결과가 파일에 그대로 남았는지 확인한다 — 외국인이 읽는
 * 글이라 한 줄이라도 섞이면 그 줄이 앱 전체의 번역을 의심하게 만든다.
 */
class PlaceLocalAddressSeedTest {

	private static final Pattern ROW = Pattern.compile("^\\s+\\('(ja|zh-Hans|zh-Hant)', '((?:[^']|'')*)',", Pattern.MULTILINE);

	private static final Map<String, String> CITY = Map.of("ja", "釜山広域市 ", "zh-Hans", "釜山广域市", "zh-Hant", "釜山廣域市");

	private static List<String[]> rows() {
		try {
			Resource[] found = new PathMatchingResourcePatternResolver()
					.getResources("classpath*:db/migration/V*__place_local_addresses.sql");
			assertThat(found).hasSize(1);
			String sql = found[0].getContentAsString(StandardCharsets.UTF_8);
			List<String[]> rows = new ArrayList<>();
			Matcher m = ROW.matcher(sql);
			while (m.find()) {
				rows.add(new String[] { m.group(1), m.group(2) });
			}
			return rows;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Test
	@DisplayName("언어마다 백 곳 넘게 실려 있다 — 정규식이 어긋나 0행을 보고 통과하지 않게")
	void seedIsNotEmpty() {
		List<String[]> rows = rows();
		for (String lang : CITY.keySet()) {
			assertThat(rows.stream().filter(r -> r[0].equals(lang)).count()).as(lang).isGreaterThan(100);
		}
	}

	@Test
	@DisplayName("🔴 한국어가 섞인 주소는 없다 — 원본에 한국어 그대로인 주소가 언어마다 세 곳 있었다")
	void noHangul() {
		assertThat(rows()).filteredOn(r -> r[1].matches(".*[가-힣].*")).extracting(r -> r[0] + " " + r[1]).isEmpty();
	}

	@Test
	@DisplayName("🔴 시 이름은 언어마다 한 표기다 — 원본에 「プサン広域市」 「釜山広域地」·간체 속 번체가 섞여 있었다")
	void cityIsNormalized() {
		assertThat(rows()).filteredOn(r -> !r[1].startsWith(CITY.get(r[0]))).extracting(r -> r[0] + " " + r[1]).isEmpty();
	}

	@Test
	@DisplayName("일본어 구 이름은 한자다 — 원본에 「ヘウンデ区」와 「海雲台区」가 섞여 있었다. 관광특구처럼 구역이면 구까지만 있다")
	void japaneseGuIsKanji() {
		assertThat(rows()).filteredOn(r -> r[0].equals("ja"))
				.filteredOn(r -> !r[1].matches("釜山広域市 [一-龥]+[区郡]( .+)?"))
				.extracting(r -> r[1]).isEmpty();
	}
}
