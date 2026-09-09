package com.gabolle.backend.moderation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 신고·검토 통합 테스트가 "이 기록이 이 피드/상세에 보이는가" 를 물을 때 쓰는 도우미.
 *
 * <p>🔴 커서를 끝까지 걸어 전부 모은 뒤에 포함 여부를 본다. 이 DB 는 다른 테스트 클래스와 스키마를
 * 함께 쓰므로, 첫 페이지(예: 50 개)만 보면 다른 테스트가 남긴 최신 기록들에 밀려 "신고 전인데도
 * 없다" 는 오탐이 날 수 있다({@code StoryFeedIntegrationTest.allIdsFiltered} 와 같은 이유).
 */
final class FeedProbe {

	private static final ObjectMapper JSON = new ObjectMapper();

	private FeedProbe() {
	}

	static boolean containsStory(MockMvc mockMvc, String path, Object[] pathVars, Authentication viewer,
			String scopeOrNull, UUID storyId) throws Exception {
		String cursor = null;
		int pages = 0;
		do {
			MockHttpServletRequestBuilder request = get(path, pathVars).principal(viewer).param("limit", "50");
			if (scopeOrNull != null) {
				request = request.param("scope", scopeOrNull);
			}
			if (cursor != null) {
				request = request.param("cursor", cursor);
			}
			MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
			JsonNode page = JSON.readTree(result.getResponse().getContentAsString()).get("data");
			for (JsonNode item : page.get("items")) {
				if (item.get("id").asText().equals(storyId.toString())) {
					return true;
				}
			}
			cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
		}
		while (cursor != null && ++pages < 500);
		return false;
	}
}
