package com.gabolle.backend.place.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.gabolle.backend.place.photo.GooglePlacePhotoLink.PhotoRef;

/**
 * S15P21E201-2059 — {@code photo_file_page} 끝의 {@code &photo=N} 읽기·쓰기.
 *
 * <p>규칙은 둘이다. 장소 번호는 예전처럼 <b>엄격하게</b> 읽는다(밖에서 온 글자가 Google 요청 주소로 흘러들면
 * 안 된다). 사진 번호는 <b>너그럽게</b> 읽는다 — 읽을 수 없으면 0 이고, 그렇다고 사진이 사라지지는 않는다.
 */
class GooglePlacePhotoLinkTest {

	private static final String ID = "ChIJfYLMelSTaDURFlCRfk5W1PA";

	private static final String BASE = GooglePlacePhotoLink.PREFIX + ID;

	// ---- 번호 없음: 예전과 똑같다 ----

	@Test
	void 번호가_없으면_장소_번호만_있고_첫_번째_사진이다() {
		assertThat(GooglePlacePhotoLink.photoRefOf(BASE)).contains(new PhotoRef(ID, 0));
		assertThat(GooglePlacePhotoLink.googlePlaceIdOf(BASE)).contains(ID);
		assertThat(GooglePlacePhotoLink.photoIndexOf(BASE)).isZero();
	}

	@Test
	void 번호_없이_만드는_주소는_예전_그대로다() {
		assertThat(GooglePlacePhotoLink.filePageOf(ID))
				.isEqualTo("https://www.google.com/maps/place/?q=place_id:ChIJfYLMelSTaDURFlCRfk5W1PA");
	}

	// ---- 번호 있음 ----

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 })
	void 번호가_있으면_그_번호를_돌려주고_장소_번호는_그대로다(int index) {
		String page = BASE + "&photo=" + index;

		assertThat(GooglePlacePhotoLink.photoRefOf(page)).contains(new PhotoRef(ID, index));
		assertThat(GooglePlacePhotoLink.googlePlaceIdOf(page)).contains(ID);
		assertThat(GooglePlacePhotoLink.photoIndexOf(page)).isEqualTo(index);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 })
	void 번호를_붙여_만든_주소를_다시_읽으면_같은_번호다(int index) {
		String page = GooglePlacePhotoLink.filePageOf(ID, index);

		assertThat(page).isEqualTo(BASE + "&photo=" + index);
		assertThat(GooglePlacePhotoLink.photoRefOf(page)).contains(new PhotoRef(ID, index));
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 10, 11, 100, Integer.MAX_VALUE, Integer.MIN_VALUE })
	void 범위_밖의_번호로는_주소를_만들지_않는다(int index) {
		assertThatThrownBy(() -> GooglePlacePhotoLink.filePageOf(ID, index))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 번호를_붙여도_장소_번호_형식은_똑같이_검사한다() {
		assertThatThrownBy(() -> GooglePlacePhotoLink.filePageOf("a/b", 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GooglePlacePhotoLink.filePageOf(null, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GooglePlacePhotoLink.filePageOf("짧음", 0)).isInstanceOf(IllegalArgumentException.class);
	}

	// ---- 이상한 번호: 장소는 살리고 첫 번째 사진으로 ----

	@ParameterizedTest
	@ValueSource(strings = {
			"", // 값이 비었다
			"abc", // 숫자가 아니다
			"-1", // 음수
			"10", "11", "99", // 범위(0~9)를 넘는다
			"100", "007", // 두 자리를 넘는다
			"9999999999999999999999", // int 에 안 들어가는 수 — 읽다가 터지면 안 된다
			"3.5", "1x", "x1", "0x1", "+1", // 숫자 하나가 아니다
			" 1", "1 ", "\n1", // 공백이 섞였다
			"%31", // 퍼센트 인코딩은 풀지 않는다
			"٣", "３", // 아라비아 숫자·전각 숫자는 번호로 읽지 않는다
			"1&photo=2", "1&foo=2" // 꼬리가 더 붙었다
	})
	void 읽을_수_없는_번호는_첫_번째로_읽고_장소_번호는_잃지_않는다(String value) {
		String page = BASE + "&photo=" + value;

		assertThat(GooglePlacePhotoLink.photoRefOf(page)).as(page).contains(new PhotoRef(ID, 0));
		assertThat(GooglePlacePhotoLink.googlePlaceIdOf(page)).as(page).contains(ID);
		assertThat(GooglePlacePhotoLink.photoIndexOf(page)).as(page).isZero();
	}

	// ---- 우리가 만든 주소가 아니면 번호가 있어도 비어 있다 ----

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			"https://commons.wikimedia.org/wiki/File:x.jpg", // 다른 원천의 원본 페이지
			"http://www.google.com/maps/place/?q=place_id:ChIJfYLMelSTaDURFlCRfk5W1PA&photo=2", // http
			"https://www.google.com/maps/place/?q=place_id:short&photo=2", // 장소 번호가 너무 짧다
			"https://www.google.com/maps/place/?q=place_id:../../evil?x=1", // 장소 번호 자리에 경로가 섞였다
			"https://www.google.com/maps/place/?q=place_id:../../evil&photo=1",
			"https://www.google.com/maps/place/?q=place_id:ChIJfYLMelSTaDURFlCRfk5W1PA&foo=1", // 모르는 꼬리
			"https://www.google.com/maps/place/?q=place_id:ChIJfYLMelSTaDURFlCRfk5W1PA?photo=2", // & 가 아니라 ?
			"https://www.google.com/maps/place/?q=place_id:ChIJfYLMelSTaDURFlCRfk5W1PA#photo=2",
			"https://www.google.com/maps/place/?q=place_id:ChIJfYLMelSTaDURFlCRfk5W1PA&PHOTO=2", // 대소문자
			"https://www.google.com/maps/place/?q=place_id:ChIJfYLMelSTaDURFlCRfk5W1PA&photo", // = 가 없다
			"https://www.google.com/maps/place/?q=place_id:ChIJfYLMelSTaDURFlCRfk5W1PA/&photo=2"
	})
	void 우리가_만든_주소가_아니면_비어_있고_사진은_첫_번째다(String page) {
		assertThat(GooglePlacePhotoLink.photoRefOf(page)).as(page).isEmpty();
		assertThat(GooglePlacePhotoLink.googlePlaceIdOf(page)).as(page).isEmpty();
		assertThat(GooglePlacePhotoLink.photoIndexOf(page)).as(page).isZero();
	}

	@Test
	void 장소_번호_자리에_글자가_너무_길어도_터지지_않는다() {
		String tooLong = BASE + "x".repeat(400);

		assertThat(GooglePlacePhotoLink.photoRefOf(tooLong + "&photo=2")).isEmpty();
		assertThat(GooglePlacePhotoLink.photoRefOf(tooLong)).isEmpty();
	}
}
