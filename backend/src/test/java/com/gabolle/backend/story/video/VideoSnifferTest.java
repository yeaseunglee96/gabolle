package com.gabolle.backend.story.video;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.story.application.VideoUploadService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S15P21E201-1275 — 동영상 형식 판별.
 *
 * <p>DB 를 안 띄운다. 재는 것이 <b>앞 12바이트를 어떻게 읽는가</b> 하나라서 바이트 배열로 답할 수 있다.
 *
 * <h2>🔴 {@code .mov} 가 막히는지가 이 파일의 핵심이다</h2>
 *
 * QuickTime 은 MP4 와 <b>같은 {@code ftyp} 구조</b>를 쓴다. 브랜드만 다르다. 그래서
 * <i>「{@code ftyp} 이 있으면 통과」</i> 로 짜면 <b>받지 않기로 한 형식이 그대로 들어온다</b> —
 * 그리고 그건 <b>파일이 저장소에 쌓인 뒤에야</b> 드러난다.
 */
class VideoSnifferTest {

	@Test
	@DisplayName("MP4 로 알아본다 — 표준 브랜드 isom")
	void recognisesMp4() {
		assertThat(VideoSniffer.sniff(ftyp("isom"))).isEqualTo(VideoFormat.MP4);
	}

	@Test
	@DisplayName("폰이 만드는 브랜드들도 MP4 다 — mp42·avc1")
	void recognisesPhoneBrands() {
		assertThat(VideoSniffer.sniff(ftyp("mp42"))).isEqualTo(VideoFormat.MP4);
		assertThat(VideoSniffer.sniff(ftyp("avc1"))).isEqualTo(VideoFormat.MP4);
	}

	@Test
	@DisplayName("🔴 .mov(QuickTime)는 거부한다 — ftyp 구조가 같아서 브랜드를 안 보면 통과한다")
	void rejectsQuickTime() {
		assertThatThrownBy(() -> VideoSniffer.sniff(ftyp("qt  ")))
				.isInstanceOf(VideoUploadService.UnsupportedVideoException.class);
	}

	@Test
	@DisplayName("모르는 브랜드는 거부한다 — 재 보지 않은 것을 된다고 가정하지 않는다")
	void rejectsUnknownBrand() {
		assertThatThrownBy(() -> VideoSniffer.sniff(ftyp("xxxx")))
				.isInstanceOf(VideoUploadService.UnsupportedVideoException.class);
	}

	@Test
	@DisplayName("🔴 사진을 동영상 창구로 보내면 거부한다 — 창구가 갈려 있다는 뜻이다")
	void rejectsJpeg() {
		byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0 };

		assertThatThrownBy(() -> VideoSniffer.sniff(jpeg))
				.isInstanceOf(VideoUploadService.UnsupportedVideoException.class);
	}

	@Test
	@DisplayName("앞부분이 12바이트보다 짧으면 거부한다 — 잘린 파일로 판별하지 않는다")
	void rejectsTooShort() {
		assertThatThrownBy(() -> VideoSniffer.sniff(new byte[] { 0, 0, 0, 0, 'f', 't', 'y', 'p' }))
				.isInstanceOf(VideoUploadService.UnsupportedVideoException.class);
	}

	@Test
	@DisplayName("빈 것도 거부한다")
	void rejectsEmpty() {
		assertThatThrownBy(() -> VideoSniffer.sniff(new byte[0]))
				.isInstanceOf(VideoUploadService.UnsupportedVideoException.class);
	}

	/** {@code [크기 4바이트][ftyp][브랜드 4바이트]} — 실제 MP4 파일의 앞 12바이트와 같은 모양이다. */
	private static byte[] ftyp(String brand) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { 0, 0, 0, 0x18 });
		out.writeBytes("ftyp".getBytes(StandardCharsets.US_ASCII));
		out.writeBytes(brand.getBytes(StandardCharsets.US_ASCII));
		return out.toByteArray();
	}
}
