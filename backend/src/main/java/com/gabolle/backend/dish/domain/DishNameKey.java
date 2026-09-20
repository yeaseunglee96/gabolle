package com.gabolle.backend.dish.domain;

import java.text.Normalizer;

/**
 * 사진에서 읽은 음식 이름을 모으는 열쇠로 다듬는다. 같은 음식도 공백·전각반각이 조금씩 다르게
 * 오므로, 다듬지 않으면 «돼지국밥»과 «돼지 국밥»이 서로 다른 음식이 되어 그림을 두 번 만든다.
 *
 * <p>여기서 만든 값은 열쇠로만 쓴다. 화면에 보이는 것도 모델에게 보내는 것도 사진에서 읽은 원래
 * 이름이다 — 다듬은 값을 보여주면 «사진에 이렇게 적혀 있었다»가 거짓이 된다.
 */
public final class DishNameKey {

	private DishNameKey() {
	}

	/**
	 * @param name 사진에서 읽은 이름
	 * @return 모으는 데 쓸 열쇠. 이름이 비어 있으면 빈 문자열
	 */
	public static String of(String name) {
		if (name == null) {
			return "";
		}
		// 전각·반각과 자모 분리를 한 모양으로 모은다 — 같은 글자가 다른 바이트로 오는 것을
		// 여기서 끝낸다. 한글은 NFKC 로 모아야 자모가 합쳐진 한 글자가 된다.
		String normalized = Normalizer.normalize(name, Normalizer.Form.NFKC);
		return normalized.trim().replaceAll("\\s+", " ").toLowerCase();
	}
}
