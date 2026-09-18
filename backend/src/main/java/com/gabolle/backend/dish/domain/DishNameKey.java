package com.gabolle.backend.dish.domain;

import java.text.Normalizer;

/**
 * 사진에서 읽은 음식 이름을 <b>모으는 열쇠</b>로 다듬는다 — S15P21E201-1272.
 *
 * <h2>왜 다듬나</h2>
 *
 * 그림과 설명을 저장해 두고 다시 쓰는 것이 이 기능의 값과 시간을 거의 다 결정한다.
 * 그런데 사진에서 읽은 이름은 같은 음식이어도 조금씩 다르게 온다 — 앞뒤 공백, 줄
 * 가운데 들어간 두 칸 띄어쓰기, 전각·반각 섞임. 다듬지 않으면 「돼지국밥」과
 * 「돼지 국밥」이 <b>서로 다른 음식</b>이 되어 그림을 두 번 만든다.
 *
 * <h2>🔴 다만 이름 자체를 바꿔서 보여주지는 않는다</h2>
 *
 * 여기서 만든 값은 <b>열쇠로만</b> 쓴다. 화면에 보이는 것도, 모델에게 보내는 것도
 * 사진에서 읽은 원래 이름이다. 다듬은 값을 보여주면 「사진에 이렇게 적혀 있었다」가
 * 거짓이 된다 — 이 기능 전체가 그 약속 위에 서 있다.
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
