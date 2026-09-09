package com.gabolle.backend.moderation.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.moderation.application.StoryReportService;
import com.gabolle.backend.moderation.presentation.dto.StoryReportRequest;

/**
 * 기록 신고 — S15P21E201-254.
 *
 * <pre>
 * POST /api/v1/stories/{storyId}/reports   신고 접수 (204, 이미 신고했어도 204)
 * </pre>
 *
 * <p>🔴 응답은 언제나 204 다. 신규 신고와 중복 신고를 구분해 답하지 않는다 — 그 근거는
 * {@link StoryReportService} 클래스 주석에 있다.
 */
@RestController
@RequestMapping("/api/v1/stories/{storyId}/reports")
@Profile({ "db", "dev" })
public class StoryReportController {

	private final StoryReportService storyReportService;

	public StoryReportController(StoryReportService storyReportService) {
		this.storyReportService = storyReportService;
	}

	@PostMapping
	public ResponseEntity<Void> file(@PathVariable UUID storyId, @RequestBody StoryReportRequest request,
			Authentication authentication) {
		UUID reporter = AuthenticatedUsers.requireId(authentication);
		this.storyReportService.file(storyId, reporter, request);
		return ResponseEntity.noContent().build();
	}
}
