package com.gabolle.backend.feedback.adapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.feedback.application.FeedbackIssueTracker;
import com.gabolle.backend.feedback.application.FeedbackService.Feedback;

import tools.jackson.databind.json.JsonMapper;

/**
 * 받은 의견을 Jira 이슈(라벨 {@code user-feedback})로 올린다 — S15P21E201-2049.
 *
 * <p>🔴 <b>기본은 꺼져 있다.</b> 아래 환경변수를 모두 넣어야 켜진다. 토큰을 코드에 두지 않는다.
 * <ul>
 * <li>{@code GABOLLE_FEEDBACK_JIRA_ENABLED=true}</li>
 * <li>{@code GABOLLE_FEEDBACK_JIRA_BASE_URL} — 예: https://ssafy.atlassian.net</li>
 * <li>{@code GABOLLE_FEEDBACK_JIRA_EMAIL} — API 토큰 주인의 계정 이메일</li>
 * <li>{@code GABOLLE_FEEDBACK_JIRA_API_TOKEN}</li>
 * <li>{@code GABOLLE_FEEDBACK_JIRA_PROJECT_KEY} — 기본 S15P21E201</li>
 * <li>{@code GABOLLE_FEEDBACK_JIRA_ISSUE_TYPE} — 기본 Task</li>
 * </ul>
 * 실패해도 접수는 그대로다 — 이슈 키 칸만 빈다.
 */
@Component
@Profile({ "db", "dev" })
public class JiraFeedbackIssueTracker implements FeedbackIssueTracker {

	private static final Logger log = LoggerFactory.getLogger(JiraFeedbackIssueTracker.class);

	private final boolean enabled;

	private final String baseUrl;

	private final String email;

	private final String token;

	private final String projectKey;

	private final String issueType;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private final JsonMapper json = JsonMapper.builder().build();

	public JiraFeedbackIssueTracker(@Value("${gabolle.feedback.jira.enabled:false}") boolean enabled,
			@Value("${gabolle.feedback.jira.base-url:}") String baseUrl,
			@Value("${gabolle.feedback.jira.email:}") String email,
			@Value("${gabolle.feedback.jira.api-token:}") String token,
			@Value("${gabolle.feedback.jira.project-key:S15P21E201}") String projectKey,
			@Value("${gabolle.feedback.jira.issue-type:Task}") String issueType) {
		this.enabled = enabled && !baseUrl.isBlank() && !email.isBlank() && !token.isBlank();
		this.baseUrl = baseUrl.replaceAll("/+$", "");
		this.email = email;
		this.token = token;
		this.projectKey = projectKey;
		this.issueType = issueType;
		if (enabled && !this.enabled) {
			log.warn("의견 Jira 연동이 켜져 있지만 BASE_URL·EMAIL·API_TOKEN 중 빈 것이 있어 끈다");
		}
	}

	@Override
	public String create(Feedback f) {
		if (!this.enabled) {
			return null;
		}
		try {
			Map<String, Object> fields = new LinkedHashMap<>();
			fields.put("project", Map.of("key", this.projectKey));
			fields.put("issuetype", Map.of("name", this.issueType));
			fields.put("summary", summary(f));
			fields.put("labels", List.of("user-feedback"));
			fields.put("description", description(f));
			String auth = Base64.getEncoder()
				.encodeToString((this.email + ":" + this.token).getBytes(StandardCharsets.UTF_8));
			HttpRequest request = HttpRequest.newBuilder(URI.create(this.baseUrl + "/rest/api/3/issue"))
				.timeout(Duration.ofSeconds(10))
				.header("Authorization", "Basic " + auth)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(this.json.writeValueAsString(Map.of("fields", fields))))
				.build();
			HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				log.warn("Jira 이슈 생성 실패 {}: {}", response.statusCode(), response.body());
				return null;
			}
			return String.valueOf(this.json.readValue(response.body(), Map.class).get("key"));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
		catch (Exception e) {
			log.warn("Jira 이슈 생성 실패", e);
			return null;
		}
	}

	static String summary(Feedback f) {
		String label = switch (f.type()) {
			case "BUG" -> "버그";
			case "IDEA" -> "건의";
			default -> "기타";
		};
		String first = f.content().strip().split("\\R", 2)[0];
		if (first.length() > 80) {
			first = first.substring(0, 80) + "…";
		}
		return "[사용자 의견·" + label + "] " + first;
	}

	static Map<String, Object> description(Feedback f) {
		List<Object> content = new ArrayList<>();
		content.add(paragraph(f.content()));
		content.add(paragraph("의견 번호: " + f.feedbackId() + "\n로그인: " + (f.userId() == null ? "안 함" : "함")
				+ "\n앱 버전: " + n(f.appVersion()) + "\n플랫폼/OS: " + n(f.platform()) + " / " + n(f.osVersion())
				+ "\n언어: " + n(f.language()) + "\n화면: " + n(f.screen()) + "\n일정: " + n(f.tripId()) + "\n사진: "
				+ f.screenshotUrls().size() + "장" + (f.screenshotUrls().isEmpty() ? "" : "\n" + String.join("\n", f.screenshotUrls()))));
		return Map.of("type", "doc", "version", 1, "content", content);
	}

	private static Map<String, Object> paragraph(String text) {
		return Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", text)));
	}

	private static String n(String v) {
		return v == null ? "-" : v;
	}
}
