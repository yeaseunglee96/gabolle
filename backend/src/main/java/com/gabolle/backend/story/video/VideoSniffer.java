package com.gabolle.backend.story.video;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.gabolle.backend.story.application.VideoUploadService;

/**
 * 동영상 형식을 <b>파일 맨 앞 바이트</b>로만 판단한다 — S15P21E201-1275.
 *
 * <p>{@code ImageSniffer} 와 같은 원칙이다: <b>파일 이름도 클라이언트가 보낸 Content-Type 도 보지
 * 않는다.</b> 화면에서만 막으면 서버를 직접 부르는 요청(Postman, 조작된 앱)이 그 검사를 지나간다.
 *
 * <h2>MP4 는 어떻게 생겼나</h2>
 *
 * <pre>
 *   0 ─ 3   박스 크기 (4바이트)
 *   4 ─ 7   "ftyp"          ← 이게 있으면 이 계열이다
 *   8 ─ 11  브랜드          ← "isom" "mp42" … 무엇인지는 여기서 갈린다
 * </pre>
 *
 * <p>즉 <b>앞 12바이트면 판별이 끝난다.</b> 파일 전체가 필요 없다 — 동영상은 클 수 있으므로
 * 이것이 중요하다. {@link #HEAD_BYTES} 만 읽어서 넘기면 된다.
 *
 * <h2>🔴 {@code "qt  "} 는 일부러 거부한다</h2>
 *
 * QuickTime({@code .mov})도 같은 {@code ftyp} 구조를 쓴다. 브랜드만 다르다. 그래서
 * <b>{@code ftyp} 이 있다고 통과시키면 {@code .mov} 가 그대로 들어온다</b> — 받지 않기로 한
 * 형식이 검사를 지나가는 셈이다. 아는 브랜드 목록을 두고 그 안에 있을 때만 통과시킨다.
 *
 * <p>브랜드가 늘면 <b>실제로 올라온 파일을 보고 이 목록에 더한다.</b> 모르는 브랜드를 통과시키는
 * 쪽으로 넓히지 않는다 — 그건 재 보지 않은 것을 된다고 가정하는 일이다
 * ({@code PhotoUrlScheme} 이 호스트 목록에 같은 판단을 적어 뒀다).
 */
public final class VideoSniffer {

	/** 판별에 필요한 앞부분 길이. 이보다 많이 읽을 이유가 없다. */
	public static final int HEAD_BYTES = 12;

	/**
	 * 통과시키는 MP4 브랜드.
	 *
	 * <p>{@code isom}·{@code iso2}·{@code mp41}·{@code mp42} 는 표준 MP4 다. {@code avc1} 은 H.264
	 * 를 담은 MP4, {@code mmp4}·{@code dash} 는 조각 재생용 변종, {@code M4V } 는 애플이 쓰는
	 * MP4 다(뒤의 빈칸까지가 이름이다 — 브랜드는 언제나 4글자다).
	 *
	 * <p>🔴 {@code "qt  "}(QuickTime, {@code .mov})는 <b>여기 없다.</b> 일부러 뺀 것이다.
	 */
	private static final Set<String> MP4_BRANDS = Set.of(
			"isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "mmp4", "dash", "M4V ");

	private VideoSniffer() {
	}

	/**
	 * @param head 파일 맨 앞 바이트. {@link #HEAD_BYTES} 이상이면 되고 더 길어도 된다
	 * @return 알아본 형식
	 * @throws VideoUploadService.UnsupportedVideoException 아는 형식이 아니다 — 415
	 */
	public static VideoFormat sniff(byte[] head) {
		if (isMp4(head)) {
			return VideoFormat.MP4;
		}
		throw new VideoUploadService.UnsupportedVideoException();
	}

	private static boolean isMp4(byte[] b) {
		if (b == null || b.length < HEAD_BYTES) {
			return false;
		}
		boolean ftyp = b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
		if (!ftyp) {
			return false;
		}
		String brand = new String(b, 8, 4, StandardCharsets.US_ASCII);
		return MP4_BRANDS.contains(brand);
	}
}
