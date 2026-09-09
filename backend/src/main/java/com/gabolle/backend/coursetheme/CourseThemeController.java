package com.gabolle.backend.coursetheme;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;

/**
 * 코스 테마 목록 — S15P21E201-450 · 상세설계서 v2 3장.
 *
 * <p>화면이 테마 목록을 따로 갖고 있으면 서버와 화면의 테마가 갈라진다. 목록은 여기서만
 * 나온다.
 *
 * <p>목록만 준다. 테마 설정이 점수와 배치에 어떻게 먹히는지는 {@code S15P21E201-452} 의
 * 일이라 이 경로에는 계산이 없다 — 다만 설정의 네 칸을 <b>그대로 실어 보낸다.</b> 화면이
 * "이 테마는 하루에 몇 곳" 같은 것을 보여 줄 수 있어야 하고, 그 값을 화면이 따로 적어 두면
 * 다시 두 벌이 된다.
 */
@RestController
public class CourseThemeController {

	private final CourseThemeProperties properties;

	public CourseThemeController(CourseThemeProperties properties) {
		this.properties = properties;
	}

	/**
	 * 테마 목록. 설정 파일의 순서를 그대로 유지한다.
	 *
	 * <p>인증이 필요하다. 이 목록 자체는 비밀이 아니지만 쓰이는 자리가 여행 만들기 안이고,
	 * 이 저장소는 자원에 주인이 없는 조회도 로그인 뒤에 두는 것을 기본으로 한다. 열어야 할
	 * 이유가 생기면 그때 인가 정책 표에서 옮긴다.
	 */
	@GetMapping(value = "/api/v1/course-categories")
	public ApiResponse<List<CourseThemeResponse>> list() {
		List<CourseThemeResponse> themes = this.properties.getThemes().stream()
				.map(CourseThemeResponse::of)
				.toList();
		return ApiResponse.success(themes, "req_" + UUID.randomUUID());
	}

	/**
	 * 응답 한 줄.
	 *
	 * <p>설정 클래스를 그대로 내보내지 않는다. 설정은 서버가 읽는 모양이고 응답은 앱과의
	 * 계약이라, 한 덩어리로 두면 설정에 칸을 하나 더할 때마다 앱의 계약이 조용히 바뀐다.
	 *
	 * @param code            화면과 서버가 함께 쓰는 코드
	 * @param label           사람이 읽는 이름
	 * @param boostedFeatures 가산점을 주는 장소 성격. 없으면 빈 배열이다 — {@code null} 로
	 *                        내보내지 않는다. 앱이 배열로 읽는 자리에 {@code null} 이 오면 그 자리에서 깨진다
	 * @param scoreEmphasis   점수에서 무엇을 더 중시하는가
	 * @param placesPerDay    하루에 도는 방문지 수
	 * @param touristPenalty  유명 관광지 감점. 안 적은 테마는 {@code null} 이고, 그것은 0 과 다른 뜻이다
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
