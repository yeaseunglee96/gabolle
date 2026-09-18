package com.gabolle.backend.dish.presentation.dto;

import java.util.UUID;

/**
 * 음식 하나에 대해 <b>모델이 아는 것</b> — S15P21E201-1272.
 *
 * <h2>🔴 이 응답은 메뉴판 응답과 출처가 다르다. 그것을 칸으로 말한다</h2>
 *
 * {@code MenuScanResponse} 에 있는 것은 전부 <b>사진에 보이는 것</b>이다. 여기 있는 것은
 * 전부 <b>모델이 아는 것</b>이거나 <b>모델이 만든 것</b>이다. 둘을 같은 화면에 나란히
 * 그리면 사용자는 출처를 못 가른다 — 「이 설명도 메뉴판에 적혀 있었나 보다」로 읽는다.
 *
 * <p>그래서 출처를 <b>말로 적지 않고 칸으로</b> 낸다. 화면이 문구를 빠뜨릴 수는 있어도
 * 칸을 안 받을 수는 없다.
 *
 * <h2>🔴 여기에도 「안전하다」를 담을 칸이 없다</h2>
 *
 * {@code MenuScanResponse} 가 {@code safe}·{@code allergenFree} 칸을 아예 안 둔 것과
 * 같은 이유다. 있으면 언젠가 누군가 그린다. 알레르기를 말하는 통로는 메뉴판 응답의
 * {@code allergenWords} <b>하나뿐</b>이고, 이 응답은 그것을 늘리지도 줄이지도 않는다.
 *
 * @param name 물어본 음식 이름 — 사진에서 읽은 그대로
 * @param description 어떤 음식인지 한 줄. 🔴 <b>모델이 모르는 음식이면 빈 문자열</b>이다.
 *     빈 것은 「설명할 것이 없는 음식」이 아니라 <b>「모델이 모른다」</b>다
 * @param descriptionSource 언제나 {@link #MODEL_KNOWLEDGE}. 사진에서 읽은 값이 아니라는
 *     것을 화면이 문구가 아니라 <b>값으로</b> 알 수 있게 둔다
 * @param imageStatus {@link #IMAGE_PENDING}(만드는 중) · {@link #IMAGE_READY}(있다) ·
 *     {@link #IMAGE_FAILED}(못 만들었다) · {@link #IMAGE_NONE}(그릴 근거가 없다 — 모델이
 *     모르는 음식이라 묘사가 없다)
 * @param imageId 그림을 받아 갈 주소에 쓰는 값. 🔴 <b>그림이 준비되기 전에도 온다</b> —
 *     화면은 이것으로 조금 뒤에 다시 물어본다. 그릴 근거가 없으면 {@code null}
 * @param imageSource 그림이 있을 때 언제나 {@link #GENERATED}. 🔴 <b>그 식당의 음식
 *     사진이 아니라 만들어진 그림</b>이라는 뜻이다. 화면은 이 사실을 그림에 붙여서
 *     말해야 한다 — 접어 두거나 작게 쓰면 안 붙인 것과 같다
 */
public record DishResponse(String name, String description, String descriptionSource,
		String imageStatus, UUID imageId, String imageSource) {

	/** 사진에서 읽은 것이 아니라 모델이 아는 것이다. */
	public static final String MODEL_KNOWLEDGE = "MODEL_KNOWLEDGE";

	/** 찍은 사진이 아니라 만들어진 그림이다. */
	public static final String GENERATED = "GENERATED";

	public static final String IMAGE_PENDING = "PENDING";

	public static final String IMAGE_READY = "READY";

	public static final String IMAGE_FAILED = "FAILED";

	/** 그릴 근거가 없다 — 모델이 모르는 음식이라 묘사 자체가 없다. */
	public static final String IMAGE_NONE = "NONE";

	/**
	 * 🔴 그림을 <b>지금은</b> 못 만든다 — 그 사람의 그림 한도가 찼다 (S15P21E201-1294).
	 *
	 * <p>{@link #IMAGE_FAILED} 와 가른 이유는 <b>다음에 할 일이 다르기 때문</b>이다.
	 * 실패는 그 음식이 원래 안 되는 것일 수 있지만, 이것은 <b>잠시 뒤 다시 누르면 된다.</b>
	 * 화면이 그 둘을 같은 문구로 그리면 사용자는 기다리면 될 것을 포기한다.
	 *
	 * <p>🔴 <b>이 값이 와도 설명은 함께 온다.</b> 그림 한도가 설명까지 막던 것을 고친 것이
	 * 이 티켓이다.
	 */
	public static final String IMAGE_RATE_LIMITED = "RATE_LIMITED";

	public static DishResponse of(String name, String description, String imageStatus, UUID imageId) {
		// 그림이 없거나 아직 못 만든 상태에서는 «만들어진 그림» 이라는 딱지도 붙이지 않는다 —
		// 붙일 그림이 없는데 출처만 있는 것은 아무 뜻이 없다.
		boolean noImageYet = IMAGE_NONE.equals(imageStatus) || IMAGE_RATE_LIMITED.equals(imageStatus);
		return new DishResponse(name, description, MODEL_KNOWLEDGE, imageStatus, imageId,
				noImageYet ? null : GENERATED);
	}
}
