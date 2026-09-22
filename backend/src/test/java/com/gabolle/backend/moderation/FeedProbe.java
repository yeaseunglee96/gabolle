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
 * 이 기록이 그 피드나 상세에 보이는가를 묻는 도우미.
 *
 * 커서를 끝까지 걸어야 한다 — 이 DB 는 다른 테스트 클래스와 스키마를 함께 쓰므로 첫 페이지만 보면
 * 다른 테스트가 남긴 최신 기록에 밀려 안 보이는 것으로 오탐한다.
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
