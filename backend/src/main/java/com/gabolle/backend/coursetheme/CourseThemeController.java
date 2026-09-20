package com.gabolle.backend.coursetheme;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;

/**
 * 코스 테마 목록. 화면이 목록을 따로 갖고 있으면 서버와 갈라지므로 목록은 여기서만 나온다.
 * 계산은 없고 설정의 네 칸을 그대로 실어 보낸다.
 */
@RestController
public class CourseThemeController {

	private final CourseThemeProperties properties;

	public CourseThemeController(CourseThemeProperties properties) {
		this.properties = properties;
	}

	/**
	 * 테마 목록. 설정 파일의 순서를 그대로 유지한다. 인증이 필요하다 — 주인이 없는 조회도
	 * 로그인 뒤에 두는 것이 이 저장소의 기본이다.
	 */
	@GetMapping(value = "/api/v1/course-categories")
	public ApiResponse<List<CourseThemeResponse>> list() {
		List<CourseThemeResponse> themes = this.properties.getThemes().stream()
				.map(CourseThemeResponse::of)
				.toList();
		return ApiResponse.success(themes, "req_" + UUID.randomUUID());
	}

	/**
	 * @param boostedFeatures 가산점을 주는 장소 성격. 없으면 빈 배열이다 — 앱이 배열로 읽는
	 *        자리라 {@code null} 로 내보내지 않는다
	 * @param placesPerDay 하루에 도는 방문지 수
	 * @param touristPenalty 유명 관광지 감점. 안 적은 테마는 {@code null} 이고, 0 과 다른 뜻이다
	 */
	public record CourseThemeResponse(String code, String label, List<String> boostedFeatures,
			String scoreEmphasis, Integer placesPerDay, Double touristPenalty) {

		static CourseThemeResponse of(CourseThemeProperties.Theme theme) {
			return new CourseThemeResponse(theme.getCode(), theme.getLabel(),
					theme.getBoostedFeatures() == null ? List.of() : List.copyOf(theme.getBoostedFeatures()),
					theme.getScoreEmphasis(), theme.getPlacesPerDay(), theme.getTouristPenalty());
		}
	}
}
