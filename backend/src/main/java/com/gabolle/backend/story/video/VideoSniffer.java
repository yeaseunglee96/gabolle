package com.gabolle.backend.story.video;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.gabolle.backend.story.application.VideoUploadService;

/**
 * 동영상 형식을 파일 맨 앞 바이트로만 판단한다. 파일 이름도 클라이언트가 보낸 Content-Type 도
 * 보지 않는다 — 화면에서만 막으면 서버를 직접 부르는 요청이 그 검사를 지나간다.
 *
 * <p>MP4 는 4~7번째 바이트가 {@code ftyp} 이고 8~11번째가 브랜드다. 앞 {@link #HEAD_BYTES}
 * 바이트면 판별이 끝나므로 파일 전체를 읽지 않는다.
 *
 * <p>QuickTime({@code .mov})도 같은 {@code ftyp} 구조를 쓰므로 {@code ftyp} 만 보고 통과시키면
 * 받지 않기로 한 형식이 들어온다. 아는 브랜드 목록 안에 있을 때만 통과시키고, 브랜드를 늘릴
 * 때는 실제로 올라온 파일을 보고 목록에 더한다.
 */
public final class VideoSniffer {

	/** 판별에 필요한 앞부분 길이. 이보다 많이 읽을 이유가 없다. */
	public static final int HEAD_BYTES = 12;

	/**
	 * 통과시키는 MP4 브랜드.
	 *
	 * <p>{@code isom}·{@code iso2}·{@code mp41}·{@code mp42} 는 표준 MP4, {@code avc1} 은 H.264
	 * 를 담은 MP4, {@code mmp4}·{@code dash} 는 조각 재생용 변종, {@code M4V } 는 애플이 쓰는
	 * MP4 다. 브랜드는 언제나 4글자라 뒤의 빈칸까지가 이름이다.
	 *
	 * <p>{@code "qt  "}(QuickTime, {@code .mov})는 일부러 뺐다.
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
