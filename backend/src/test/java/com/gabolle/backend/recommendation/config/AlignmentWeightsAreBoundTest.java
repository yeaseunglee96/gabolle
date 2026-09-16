package com.gabolle.backend.recommendation.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * 🔴 취향 비중 다섯 칸이 <b>실제로 읽히는지</b> 못 박는다 — S15P21E201-1040.
 *
 * <h2>이 파일이 막는 것</h2>
 *
 * 이 설정의 고장은 <b>조용하다.</b> 키 이름을 한 글자 틀리면 스프링이 그 줄을 그냥 무시하고,
 * {@link PreferenceAlignmentWeights} 의 압축 생성자가 빈 값을 <b>1.0 으로 채운다.</b> 그러면
 * 서버는 멀쩡히 뜨고, 추천도 나오고, 아무 오류도 안 나는데 <b>설문 계수만 빠진 채로 돈다.</b>
 *
 * <p>그리고 비율이 전부 1.0 이면 가중평균은 단순평균과 <b>같은 값</b>이다 — 즉 화면에서도
 * 순위가 그대로라 사람이 알아챌 단서가 없다. 2026-09-16 이전이 정확히 그 상태였다:
 * 다섯 키가 어느 properties 파일에도 없어서 설문 계수가 한 톨도 안 들어가 있었다.
 *
 * <h2>공허한 통과를 먼저 막는다</h2>
 *
 * 없는 것을 세는 검사는 <b>아무것도 못 읽었을 때도 초록</b>이 된다. 그래서 파일을 읽었는지
 * 먼저 단정하고({@link #theFileIsActuallyRead}), 그다음 바인딩 결과를 본다. 값 자체를 단정하지
 * 않고 "키가 있다" 만 보면, 키 이름이 틀렸을 때 그대로 통과한다 — 바인딩을 거쳐야 잡힌다.
 */
class AlignmentWeightsAreBoundTest {

	private static final Path FILE = Path.of("src/main/resources/application.properties");

	private static final String PREFIX = "gabolle.recommendation.baseline.alignment";

	private Properties load() {
		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(FILE)) {
			properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException(FILE + " 을 읽지 못했다", exception);
		}
		return properties;
	}

	private PreferenceAlignmentWeights bind() {
		Properties properties = load();
		Map<String, Object> source = new HashMap<>();
		properties.forEach((key, value) -> source.put(String.valueOf(key), value));
		return new Binder(new MapConfigurationPropertySource(source))
				.bind(PREFIX, PreferenceAlignmentWeights.class)
				.orElseThrow(() -> new AssertionError(
						PREFIX + ".* 가 하나도 안 읽혔다 — 키 이름이 틀렸거나 줄이 통째로 없다"));
	}

	@Test
	@DisplayName("설정 파일을 실제로 읽었다 — 못 읽고 통과하는 것을 막는다")
	void theFileIsActuallyRead() {
		Properties properties = load();

		assertThat(properties).isNotEmpty();
		assertThat(properties.stringPropertyNames())
				.as("baseline 설정이 이 파일에 있어야 한다. 옮겼다면 이 시험의 경로부터 고친다")
				.contains("gabolle.recommendation.baseline.model-version");
	}

	@Test
	@DisplayName("다섯 칸이 설정 파일의 값 그대로 바인딩된다 — 키 이름이 틀리면 여기서 잡힌다")
	void allFiveWeightsBindFromTheFile() {
		PreferenceAlignmentWeights weights = bind();

		// 🔴 값 자체를 단정한다. "키가 있다" 만 보면 이름이 틀렸을 때 기본값 1.0 으로
		//    조용히 통과한다 — 이 시험이 막으려는 것이 정확히 그 상황이다.
		assertThat(weights.shadePreference()).isEqualTo(0.39);
		assertThat(weights.locality()).isEqualTo(0.25);
		assertThat(weights.slopePreference()).isEqualTo(0.21);
		assertThat(weights.quietness()).isEqualTo(0.16);
	}

	@Test
	@DisplayName("관광지 선호는 일부러 0 이다 — 기본값 1.0 으로 되돌아가지 않았는지 본다")
	void touristPreferenceIsDeliberatelyZero() {
		PreferenceAlignmentWeights weights = bind();

		// 설문에 이 축을 묻는 문항이 없어 비율을 정할 근거가 없다. 비워 두면 1.0 이 되고,
		// 그러면 근거 없는 축 하나가 나머지 넷의 합(1.01)과 맞먹는 무게를 갖는다.
		// 합치거나 되살리는 것은 마감 뒤 결정이다 — 그때까지 0 이 유지되어야 한다.
		assertThat(weights.touristPreference()).isZero();
	}

	@Test
	@DisplayName("다섯이 전부 0 은 아니다 — 그러면 취향 정렬이 통째로 사라진다")
	void notEveryWeightIsZero() {
		PreferenceAlignmentWeights weights = bind();

		// PreferenceAlignmentWeights 는 다섯이 전부 0 이면 기동을 실패시킨다. 하나만 0 은
		// 정상이지만, 나중에 누가 나머지도 0 으로 내리면 서버가 안 뜬다 — 그 전에 잡는다.
		double sum = weights.locality() + weights.quietness() + weights.touristPreference()
				+ weights.shadePreference() + weights.slopePreference();
		assertThat(sum).isGreaterThan(0.0);
	}
}
