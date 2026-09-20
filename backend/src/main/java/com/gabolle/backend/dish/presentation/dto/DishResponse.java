package com.gabolle.backend.dish.presentation.dto;

import java.util.UUID;

/**
 * 음식 하나에 대해 모델이 아는 것.
 *
 * <p>메뉴판 응답과 출처가 다르다는 것을 말이 아니라 칸으로 낸다. 화면이 문구를 빠뜨릴 수는 있어도
 * 칸을 안 받을 수는 없다.
 *
 * <p>«안전하다»를 담을 칸이 없다. 있으면 언젠가 누군가 그린다. 알레르기를 말하는 통로는 메뉴판
 * 응답의 {@code allergenWords} 하나뿐이고, 이 응답은 그것을 늘리지도 줄이지도 않는다.
 *
 * @param name 물어본 음식 이름 — 사진에서 읽은 그대로
 * @param description 어떤 음식인지 한 줄. 모델이 모르는 음식이면 빈 문자열이다 — «설명할 것이 없는
 *     음식»이 아니라 «모델이 모른다»다
 * @param descriptionSource 언제나 {@link #MODEL_KNOWLEDGE}
 * @param imageStatus {@link #IMAGE_PENDING}(만드는 중) · {@link #IMAGE_READY}(있다) ·
 *     {@link #IMAGE_FAILED}(못 만들었다) · {@link #IMAGE_NONE}(그릴 근거가 없다 — 모델이
 *     모르는 음식이라 묘사가 없다)
 * @param imageId 그림을 받아 갈 주소에 쓰는 값. 그림이 준비되기 전에도 온다 — 화면은 이것으로 조금
 *     뒤에 다시 물어본다. 그릴 근거가 없으면 {@code null}
 * @param imageSource 그림이 있을 때 언제나 {@link #GENERATED}. 그 식당의 음식 사진이 아니라 만들어진
 *     그림이라는 뜻이다
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
	 * 그림을 지금은 못 만든다 — 그 사람의 그림 한도가 찼다. {@link #IMAGE_FAILED} 와 가른 것은
	 * 다음에 할 일이 달라서다 — 이쪽은 잠시 뒤 다시 누르면 된다. 이 값이 와도 설명은 함께 온다.
	 */
	public static final String IMAGE_RATE_LIMITED = "RATE_LIMITED";

	public static DishResponse of(String name, String description, String imageStatus, UUID imageId) {
		// 붙일 그림이 없는데 출처만 있는 것은 아무 뜻이 없다.
		boolean noImageYet = IMAGE_NONE.equals(imageStatus) || IMAGE_RATE_LIMITED.equals(imageStatus);
		return new DishResponse(name, description, MODEL_KNOWLEDGE, imageStatus, imageId,
				noImageYet ? null : GENERATED);
	}
}
