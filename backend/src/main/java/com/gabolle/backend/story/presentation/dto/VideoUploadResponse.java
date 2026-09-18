package com.gabolle.backend.story.presentation.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code POST /api/v1/uploads/story-video} 의 성공 응답 본문 — S15P21E201-1275.
 *
 * <p>{@code UploadResponse}(사진)와 같은 모양이되 둘이 다르다.
 *
 * <ul>
 *   <li>{@code byteSize} 가 {@code long} 이다 — 사진은 3MB 상한이 있어 {@code int} 로 안전했다</li>
 *   <li>{@code durationSec} 가 있다. <b>앱이 잰 값을 그대로 돌려주는 것</b>이고 서버가 확인한 것이
 *       아니다 — 서버는 동영상 파일을 열지 않는다. 값을 못 받았으면 <b>칸 자체가 빠진다</b></li>
 * </ul>
 *
 * <p>🔴 <b>기록에 붙이는 것은 다음 단계다.</b> 이 응답은 「파일이 올라갔고 주소는 이것이다」까지만
 * 말한다. 돌려받은 {@code videoUrl} 을 기록 작성에 실어 보내는 계약({@code media} 배열)은
 * 아직 안 나갔다.
 */
public record VideoUploadResponse(UUID videoId, String videoUrl, String contentType, long byteSize,
		@JsonInclude(JsonInclude.Include.NON_NULL) Integer durationSec) {
}
