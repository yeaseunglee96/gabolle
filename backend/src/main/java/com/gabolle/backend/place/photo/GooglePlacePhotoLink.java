package com.gabolle.backend.place.photo;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google 장소 번호와 「그 장소의 사진 중 몇 번째인가」를 {@code place.photo_file_page} 에 담고
 * 꺼낸다(S15P21E201-1832 · 2059).
 *
 * <p>칸을 새로 만들지 않고 이 칸을 쓴다. 이 칸은 원래 "사진의 원본 페이지" 이고, Google 사진의
 * 원본 페이지는 그 장소의 Google 지도 페이지다 — Google 약관이 사진 옆에 두라는 링크도 이것이다.
 * 그리고 Google 약관이 영구 저장을 허락하는 것은 장소 번호뿐이라, 사진 주소 대신 이것만 남긴다.
 *
 * <p><b>몇 번째 사진인지</b>는 주소 끝에 {@code &photo=N} 으로 적는다. 쿼리(<b>주소 뒤에
 * {@code &이름=값} 꼴로 덧붙이는 부분</b>)라서, 모르는 이름은 Google 지도가 무시하므로 링크는 그대로
 * 열린다. N 은 0 부터 {@value #MAX_PHOTO_INDEX} 까지이고, 없으면 0(첫 번째)이다. 운영자는 이 칸 하나만
 * 고치면 된다 — 우리 장소 번호(UUID, 우리 DB 의 식별자) 자리에 장소를 넣어 돌린다.
 *
 * <pre>{@code
 * UPDATE gabolle.place
 *    SET photo_file_page = split_part(photo_file_page, '&photo=', 1) || '&photo=2'
 *  WHERE place_id = '<장소 UUID>'
 *    AND photo_file_page LIKE 'https://www.google.com/maps/place/?q=place_id:%';
 * }</pre>
 *
 * <p>{@code split_part} 가 이미 붙은 번호를 먼저 떼므로 몇 번을 돌려도 같다. 첫 번째로 되돌리려면 뒤의
 * {@code || '&photo=2'} 만 빼면 된다.
 *
 * <p>🔴 저장하는 것은 <b>우리가 매긴 순번</b>뿐이다. Google 이 준 사진 이름·사진 주소는 여전히 아무데도
 * 저장하지 않는다(시간이 지나면 만료되고, 약관이 금한다). 그 대가로 Google 이 사진 순서를 바꾸면 같은
 * 번호가 다른 사진이 될 수 있다 — 번호는 「지금 N 번째」이지 「이 사진」이 아니다.
 */
public final class GooglePlacePhotoLink {

	static final String PREFIX = "https://www.google.com/maps/place/?q=place_id:";

	/** 몇 번째 사진인지 적는 꼬리. Google 장소 번호 바로 뒤에만 올 수 있다. */
	static final String PHOTO_PARAM = "&photo=";

	/** Google 은 장소 하나에 사진을 최대 열 장 준다. 번호는 0 부터 이 값까지다. */
	public static final int MAX_PHOTO_INDEX = 9;

	/** 사진 출처 이름. 앱은 이 이름을 사진 옆 라이선스 칸에 그대로 보여 준다. */
	public static final String LICENSE_NAME = "Google 지도";

	/** Google 장소 번호에 쓰이는 글자. 그 밖의 글자가 섞이면 주소를 조립하지 않는다. */
	private static final String PLACE_ID_CHARS = "[A-Za-z0-9_-]{10,300}";

	private static final Pattern PLACE_ID = Pattern.compile(PLACE_ID_CHARS);

	/**
	 * 접두사 뒤에 오는 것 전부 — 장소 번호 하나, 그리고 있으면 {@code &photo=값} 하나. 그 밖의 글자가
	 * 붙으면 우리가 만든 주소가 아니다. 값은 무엇이든 받는다: 읽을 수 있는지는 {@link #photoIndexFrom}
	 * 이 따로 본다. 여기서 값을 걸러 버리면 번호를 잘못 적은 장소의 사진이 통째로 사라진다.
	 */
	private static final Pattern TAIL = Pattern.compile(
			"(" + PLACE_ID_CHARS + ")(?:" + PHOTO_PARAM + "(.*))?", Pattern.DOTALL);

	/** 사진 번호로 읽을 수 있는 값. 반드시 ASCII 숫자여야 한다 — 다른 나라 숫자 글자는 받지 않는다. */
	private static final Pattern PHOTO_NUMBER = Pattern.compile("[0-9]{1,2}");

	private GooglePlacePhotoLink() {
	}

	/**
	 * 이 칸이 가리키는 것: Google 장소 번호와, 그 장소의 사진 중 몇 번째인가(0 부터).
	 */
	public record PhotoRef(String googlePlaceId, int photoIndex) {
	}

	/** 첫 번째 사진을 가리키는 주소. 번호를 안 붙인다 — 번호 없는 주소가 곧 첫 번째다. */
	public static String filePageOf(String googlePlaceId) {
		if (googlePlaceId == null || !PLACE_ID.matcher(googlePlaceId).matches()) {
			throw new IllegalArgumentException("Google 장소 번호 형식이 아니다");
		}
		return PREFIX + googlePlaceId;
	}

	/**
	 * {@code photoIndex} 번째 사진을 가리키는 주소. 0 이어도 번호를 붙인다 — 부른 쪽이 번호를 골랐으면
	 * 주소에도 그대로 남는다.
	 *
	 * @throws IllegalArgumentException 번호가 0 ~ {@value #MAX_PHOTO_INDEX} 밖일 때
	 */
	public static String filePageOf(String googlePlaceId, int photoIndex) {
		if (photoIndex < 0 || photoIndex > MAX_PHOTO_INDEX) {
			throw new IllegalArgumentException("사진 번호는 0 부터 " + MAX_PHOTO_INDEX + " 까지다: " + photoIndex);
		}
		return filePageOf(googlePlaceId) + PHOTO_PARAM + photoIndex;
	}

	/**
	 * 이 칸이 Google 장소를 가리키면 그 장소 번호와 사진 번호를 한 번에 돌려준다. 아니면 비어 있음.
	 *
	 * <p>사진 번호가 없거나, 읽을 수 없거나(숫자가 아님 · 음수 · 세 자리 이상), 범위(0 ~
	 * {@value #MAX_PHOTO_INDEX}) 밖이면 <b>0 으로 읽는다</b> — 번호를 잘못 적었다고 그 장소의 사진을
	 * 없애지 않고 번호가 없던 때와 똑같이 첫 번째 사진을 보인다.
	 */
	public static Optional<PhotoRef> photoRefOf(String filePage) {
		if (filePage == null || !filePage.startsWith(PREFIX)) {
			return Optional.empty();
		}
		Matcher tail = TAIL.matcher(filePage.substring(PREFIX.length()));
		if (!tail.matches()) {
			return Optional.empty();
		}
		return Optional.of(new PhotoRef(tail.group(1), photoIndexFrom(tail.group(2))));
	}

	/**
	 * 이 칸이 Google 장소를 가리키면 그 번호를, 아니면 비어 있음을 돌려준다. {@code &photo=N} 이 붙어
	 * 있어도 장소 번호만 돌려준다.
	 */
	public static Optional<String> googlePlaceIdOf(String filePage) {
		return photoRefOf(filePage).map(PhotoRef::googlePlaceId);
	}

	/** 몇 번째 사진인가(0 부터). Google 주소가 아니거나 번호가 없거나 읽을 수 없으면 0. */
	public static int photoIndexOf(String filePage) {
		return photoRefOf(filePage).map(PhotoRef::photoIndex).orElse(0);
	}

	private static int photoIndexFrom(String value) {
		if (value == null || !PHOTO_NUMBER.matcher(value).matches()) {
			return 0;
		}
		int index = Integer.parseInt(value);
		return (index <= MAX_PHOTO_INDEX) ? index : 0;
	}
}
