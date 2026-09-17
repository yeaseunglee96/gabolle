package com.gabolle.backend.story.presentation.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 기록 한 건 — 피드 항목과 상세 조회가 같은 모양을 쓴다. 앱이 둘을 한 컴포넌트로 그린다.
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

		/**
		 * 🔴 S15P21E201-1183 — 댓글이면 부모 글의 id, 원글이면 {@code null}. 맨 뒤에 더한 칸이다.
		 *
		 * <p><b>댓글도 이 응답 모양을 그대로 쓴다.</b> 별도 타입을 만들지 않는 것이 시안의 요구다 —
		 * *「댓글 = 같은 StoryDto 형태 + parentId. 별도 Comment 타입 만들지 말 것」*. 화면이 원글과
		 * 댓글을 같은 부품으로 그리므로 응답도 같은 모양이어야 한다.
		 *
		 * <p>🔴 <b>댓글의 댓글도 이 칸 하나로 이어진다.</b> 깊이 제한은 없다 — 몇 단까지 보여줄지는
		 * 화면이 정한다.
		 */
		String parentId,

		/**
		 * 🔴 S15P21E201-1183 — <b>직접</b> 달린 댓글 수. 손자 이하는 안 센다.
		 *
		 * <p>없으면 <b>0</b> 이고 {@code null} 이 아니다.
		 *
		 * <p>손자까지 세면 댓글 하나를 지울 때 조상을 전부 거슬러 올라가며 내려야 한다. 직접 달린
		 * 것만 세면 고치는 자리가 깊이와 무관하게 언제나 한 칸이다. 화면은 트위터처럼 「답글 3개」를
		 * 그 댓글 밑에 붙이면 되므로 손해가 없다.
		 */
		int replyCount,

		/**
		 * 🔴 S15P21E201-1204 — 글을 <b>눌러서 연</b> 횟수. 노출 수가 아니다.
		 *
		 * <p>사람 × 글 × 하루 한 번이고 <b>작성자 본인은 안 센다.</b> 비회원도 세지만 익명 세션으로
		 * 식별되는 경우만이다 — 식별할 수 없으면 「하루 한 번」을 지킬 방법이 없어 안 센다.
		 *
		 * <p>없으면 <b>0</b> 이고 {@code null} 이 아니다.
		 */
		int viewCount,

		/**
		 * 🔴 S15P21E201-1204 — 공유 링크 <b>복사 버튼을 누른</b> 횟수.
		 *
		 * <p>화면에서 부르는 말은 「인용수」지만 이름은 {@code linkCopyCount} 다. 우리가 아는 것은
		 * 「복사 버튼을 눌렀다」뿐이고, 복사한 사람이 인용했는지 어디에 붙였는지 우리는 모른다.
		 *
		 * <p>⚠️ <b>아직 오르지 않는다.</b> 세는 코드는 다음 티켓이고 지금은 언제나 0 이다.
		 * 칸을 미리 내보내는 이유는 응답 모양을 <b>두 번 바꾸지 않으려는 것</b>이다 — 화면이
		 * 한 번만 받으면 된다.
		 */
		int linkCopyCount) {

	public record Author(String id, String displayName) {
	}

	/**
	 * 기록이 가리키는 장소.
	 *
	 * <p>🔴 {@code lat}·{@code lng} 는 S15P21E201-829 에서 더했다. 화면이 사진을 지도에 찍으려면
	 * 좌표가 필요한데, 그전에는 장소마다 {@code GET /api/v1/places/&#123;id&#125;} 를 한 번씩 더
	 * 불러야 했다(사진 여러 장이 같은 장소면 같은 장소를 반복해서). 좌표가 없는 장소도 있으므로
	 * 두 값은 {@code null} 일 수 있다 — 화면은 그때 마커를 찍지 않는다.
	 *
	 * <p>🔴 {@code address}·{@code nameEn}·{@code addressEn} 은 S15P21E201-1189 에서 더했다. 좌표와
	 * 같은 이유다 — 피드 카드가 장소 이름과 주소를 쓰는데, 그것 때문에 장소마다
	 * {@code GET /api/v1/places/&#123;id&#125;} 를 다시 불러야 했다. {@code place} 조인이 이미 돼 있어
	 * 질의는 늘지 않는다.
	 *
	 * <p>🔴 <b>영문 둘은 셋이 아니라 넷으로 온다 — 이름과 주소를 함께 더했다.</b> 주소만 영문으로
	 * 내면 <b>이름은 한글인데 주소만 영어인</b> 화면이 된다. 앱은 이미 장소 화면에서
	 * {@code placeNameForLanguage(nameKo, nameEn, …)} 와 {@code addressEn ?? address} 로 둘을 함께
	 * 쓰고 있어서, 한쪽만 주면 기록 피드만 반쪽으로 남는다.
	 *
	 * <p>🔴 <b>영문 둘은 값이 없으면 키 자체가 빠진다</b>({@code NON_NULL}). {@code addressEn} 이
	 * 이 저장소에서 이미 그렇게 나가고 있고(택시 카드 응답), 화면이 {@code ??} 로 한국어에 되돌아가는
	 * 방식이라 {@code null} 과 「키 없음」을 구분할 필요가 없다. 여기서 {@code resolvedLanguage} 를
	 * 흉내내지 않는 이유이기도 하다 — 그 칸은 장소 상세 응답만의 것이다.
	 *
	 * <p>{@code address} 는 없는 장소가 있어 {@code null} 일 수 있다 — 화면은 그때 그 줄을 비운다.
	 * 한글 이름({@code name})만은 {@code Place} 가 {@code null} 을 허용하지 않는다.
	 */
	public record PlaceRef(String id, String name, Double lat, Double lng, String address,
		@JsonInclude(JsonInclude.Include.NON_NULL) String nameEn,
		@JsonInclude(JsonInclude.Include.NON_NULL) String addressEn) {
	}

	public record Image(String url, int position) {
	}
}
