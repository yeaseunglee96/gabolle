package com.gabolle.backend.review.presentation.dto;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.review.domain.PlaceReview;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceReviewResponseTest {

	@Test
	@DisplayName("🔴 리뷰 본문의 스크립트 태그가 인코딩된 채로 응답에 실린다")
	void bodyIsHtmlEncoded() {
		PlaceReview review = PlaceReview.write(UUID.randomUUID(), UUID.randomUUID(),
				new PlaceReview.Scores((short) 4, null, null, null), "<script>alert(1)</script>", true,
				"해운대구", Instant.now());

		PlaceReviewResponse response = PlaceReviewResponse.from(review, null);

		assertThat(response.body()).doesNotContain("<script>");
		assertThat(response.body()).contains("&lt;script&gt;");
	}
}
