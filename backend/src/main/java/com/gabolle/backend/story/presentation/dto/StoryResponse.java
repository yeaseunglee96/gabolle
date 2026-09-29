package com.gabolle.backend.story.presentation.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 기록 한 건 — 피드 항목과 상세 조회가 같은 모양을 쓴다.
 *
 * @param mine 요청자가 작성자인가. 수정·삭제 버튼을 켤지 정한다
 * @param published 공개 시각이 지났나. 작성자에게만 false 가 보일 수 있다(남에게는 아예 안 보인다)
 */
public record StoryResponse(
		String id,
		Author author,
		String body,
		String region,
		PlaceRef place,
		String tripId,
		List<Image> images,
		String visibility,
		String publishAt,
		String createdAt,
		String updatedAt,
		boolean mine,
		boolean published,

		/** 댓글이면 부모 글의 id, 원글이면 {@code null}. 댓글의 댓글도 이 칸 하나로 이어지고 깊이 제한은 없다. */
		String parentId,

		/** 직접 달린 댓글 수 — 손자 이하는 안 센다. 없으면 {@code null} 이 아니라 0 이다. */
		int replyCount,

		/**
		 * 글을 눌러서 연 횟수다 — 노출 수가 아니다. 사람 × 글 × 하루 한 번이고 작성자 본인은 안 센다.
		 * 비회원도 세지만 익명 세션으로 식별되는 경우만이다. 없으면 {@code null} 이 아니라 0 이다.
		 */
		int viewCount,

		/**
		 * 공유 링크 복사 버튼을 누른 횟수. 화면에서 부르는 말은 「인용수」지만 우리가 아는 것은 복사를 눌렀다는
		 * 것뿐이라 이름이 다르다. 없으면 0 이다.
		 */
		int linkCopyCount,

		/**
		 * 좋아요 수. 없으면 0 이다. 누적 칸이 아니라 반응 표를 그때그때 세어 붙인다 — 누적 칸은 취소와 짝이
		 * 어긋나면 수와 행이 다른 상태가 남는다. 취소한 사람은 안 센다(취소해도 반응 행은 남는다).
		 */
		int likeCount,

		/** 싫어요 수. 없으면 0 이다. 좋아요에서 빼지 않고 따로 낸다 — 뺄셈은 화면이 할 판단이다. */
		int dislikeCount,

		/**
		 * 내가 지금 눌러 둔 것 — {@code "LIKE"} · {@code "DISLIKE"} · {@code null}(안 눌렀거나 취소했거나 비회원).
		 *
		 * <p>불리언이 아닌 이유는 좋아요·싫어요·안 누름 셋을 가르지 못해 토글 버튼이 자기 상태를 못 그리기
		 * 때문이다. 키를 빼지 않고 {@code null} 을 그대로 낸다 — 「없음」도 뜻이 있는 세 값짜리 칸이다.
		 */
		String myReaction,

		/**
		 * 사진이 아닌 미디어. 지금은 동영상 0개 또는 1개다.
		 *
		 * <p>{@code images} 에 섞지 않는다. 배포된 앱이 그 배열의 모든 원소를 사진으로 그리므로
		 * 동영상 주소를 섞으면 구판 화면이 깨진다. 칸을 더하면 구판은 동영상을 못 볼 뿐이다.
		 *
		 * <p>없으면 빈 배열이고 {@code null} 이 아니다.
		 */
		List<Media> media,

		/**
		 * 함께 쓰는 사람 — 초대를 수락해 합류한 사람만, 합류한 순서대로. 만든 사람({@code author})은 들어가지
		 * 않는다. 없으면 빈 배열이고 {@code null} 이 아니다.
		 *
		 * <p>참여자 목록({@code GET /stories/{id}/coauthors})과 같은 사실이지만, 피드 카드가 글마다 그것을
		 * 따로 부르지 않게 여기에도 싣는다.
		 */
		List<Coauthor> coauthors) {

	/**
	 * @param avatarUrl 프로필 사진 주소. 안 골랐거나 탈퇴했으면 {@code null} — 화면은 첫 글자를 그린다.
	 * 나중에 더한 칸이라 구판 앱은 모르고 지나간다 (S15P21E201-1803)
	 */
	public record Author(String id, String displayName, String avatarUrl) {
	}

	/**
	 * @param displayName 탈퇴했거나 이름이 비었으면 {@code null} — 무엇으로 부를지는 화면이 정한다
	 * @param avatarUrl 프로필 사진 주소. 없거나 탈퇴했으면 {@code null}
	 */
	public record Coauthor(String id, String displayName, String avatarUrl) {
	}

	/**
	 * 기록이 가리키는 장소.
	 *
	 * <p>{@code lat}·{@code lng} 와 {@code address} 는 없는 장소가 있어 {@code null} 일 수 있다 — 화면은 그때
	 * 마커를 찍지 않거나 그 줄을 비운다. 한글 이름({@code name})만은 {@code null} 이 아니다.
	 *
	 * <p>영문 이름·주소는 값이 없으면 키 자체가 빠진다({@code NON_NULL}) — 화면이 {@code ??} 로 한국어에
	 * 되돌아가므로 {@code null} 과 「키 없음」을 구분할 필요가 없다.
	 */
	public record PlaceRef(String id, String name, Double lat, Double lng, String address,
		@JsonInclude(JsonInclude.Include.NON_NULL) String nameEn,
		@JsonInclude(JsonInclude.Include.NON_NULL) String addressEn,
	/**
	 * 장소 이름의 일본어·중국어(간체·번체) — 관광공사가 번역해 둔 곳만 있다(V20260930130000, S15P21E201-1859).
	 * 키는 앱의 언어 코드({@code ja} · {@code zh-Hans} · {@code zh-Hant}), 없는 언어는 빠지고 다 없으면 칸째 빠진다.
	 */
	@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> localNames) {
	}

	public record Image(String url, int position) {
	}

	/**
	 * 사진이 아닌 미디어 하나.
	 *
	 * @param kind 지금은 {@code "VIDEO"} 하나뿐이다
	 * @param url 재생 주소. 스프링을 안 지나고 저장소에서 바로 나간다 — 그 길만 구간 요청(Range)을
	 * 지원해서 되감기·건너뛰기가 된다
	 * @param thumbnailUrl 없으면 칸 자체가 빠진다. 빈 문자열도 자리표시 주소도 넣지 않는다
	 * @param durationSec 앱이 잰 길이(초). 서버가 확인한 값이 아니다. 못 받았으면 칸이 빠진다
	 */
	public record Media(String kind, String url,
			@JsonInclude(JsonInclude.Include.NON_NULL) String thumbnailUrl,
			@JsonInclude(JsonInclude.Include.NON_NULL) Integer durationSec) {
	}
}
