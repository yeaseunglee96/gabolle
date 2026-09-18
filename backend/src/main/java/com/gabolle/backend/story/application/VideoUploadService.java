package com.gabolle.backend.story.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.story.domain.UploadedVideo;
import com.gabolle.backend.story.repository.UploadedVideoRepository;
import com.gabolle.backend.story.storage.StoragePort;
import com.gabolle.backend.story.storage.StorageProperties;
import com.gabolle.backend.story.video.VideoFormat;
import com.gabolle.backend.story.video.VideoSniffer;

/**
 * 동영상 업로드 — S15P21E201-1275.
 *
 * <h2>검사 순서</h2>
 * <ol>
 *   <li>비었으면 {@link EmptyVideoException}(400)</li>
 *   <li>상한을 넘으면 {@link VideoTooLargeException}(413) — 상한은 <b>설정값</b>이다</li>
 *   <li>{@link VideoSniffer} 로 <b>앞 12바이트만</b> 보고 진짜 형식을 확인한다 —
 *       파일 이름·Content-Type 은 안 본다</li>
 *   <li>{@code story-video/yyyy/MM/&lt;uuid&gt;.mp4} 키를 만들어 저장소에 <b>흘려보낸다</b></li>
 *   <li>{@link UploadedVideo} 를 저장한다</li>
 * </ol>
 *
 * <h2>🔴 사진과 다른 것 둘</h2>
 *
 * <b>하나. 파일을 힙에 통째로 안 올린다.</b> 사진은 {@code byte[]} 로 받는 것이 맞다 —
 * {@code ImageSanitizer} 가 촬영 위치를 떼려면 전체가 메모리에 있어야 한다. 동영상은 서버가
 * <b>열지 않으므로</b>(2026-09-18 결정 — 줄이기·썸네일·길이 재기를 전부 앱이 한다) 앞 12바이트로
 * 형식만 보고 나머지는 그대로 흘린다. 30MB 동영상이 동시에 셋 올라와도 힙이 안 는다.
 *
 * <p><b>둘. 촬영 위치를 여기서 안 뗀다.</b> 사진은 {@code ImageSanitizer} 가 뗐다. 동영상은
 * <b>앱이 다시 인코딩하면서 떨어진다</b>(줄이기를 앱이 하므로). 🔴 <b>그래서 앱이 안 줄이는 길이
 * 열리면 이 보호가 통째로 사라진다</b> — 웹에서 동영상 올리기를 안 여는 이유이기도 하다.
 *
 * <h2>🔴 {@code @Transactional} 을 안 둔다</h2>
 *
 * {@code ImageUploadService} 와 같은 이유다. 저장소 쓰기는 네트워크 호출이라 트랜잭션으로 묶으면
 * 그동안 DB 커넥션을 놀리며 붙잡는다. 삽입이 실패하면 방금 쓴 키를 지워 보상한다.
 */
@Service
@Profile({ "db", "dev" })
public class VideoUploadService {

	private static final Logger log = LoggerFactory.getLogger(VideoUploadService.class);

	private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

	private final StoragePort storagePort;

	private final UploadedVideoRepository uploadedVideoRepository;

	private final StorageProperties storageProperties;

	private final Clock clock;

	public VideoUploadService(StoragePort storagePort, UploadedVideoRepository uploadedVideoRepository,
			StorageProperties storageProperties, Clock clock) {
		this.storagePort = storagePort;
		this.uploadedVideoRepository = uploadedVideoRepository;
		this.storageProperties = storageProperties;
		this.clock = clock;
	}

	/**
	 * @param size 보낼 바이트 수. 클라이언트가 <b>주장한</b> 값이 아니라 서버가 실제로 받아 센 값이다
	 * (Spring 이 multipart 를 디스크로 흘리면서 센다)
	 * @param durationSec 앱이 잰 길이. 서버는 확인하지 않는다 — 파일을 열지 않기 때문이다. 없으면 {@code null}
	 */
	public UploadedVideo upload(UUID uploaderUserId, InputStream in, long size, Integer durationSec)
			throws IOException {
		if (size <= 0) {
			throw new EmptyVideoException();
		}
		long maxBytes = this.storageProperties.getVideo().getMaxBytes();
		if (size > maxBytes) {
			throw new VideoTooLargeException(size, maxBytes);
		}

		// 🔴 앞부분만 읽어서 형식을 본다. 읽은 만큼은 스트림에서 사라지므로 앞에 도로 붙여 넘긴다
		//    — 그러지 않으면 저장된 파일의 머리 12바이트가 잘린다.
		byte[] head = in.readNBytes(VideoSniffer.HEAD_BYTES);
		VideoFormat format = VideoSniffer.sniff(head);
		InputStream whole = new SequenceInputStream(new ByteArrayInputStream(head), in);

		Instant now = this.clock.instant();
		String key = buildKey(format, now);
		String videoUrl = this.storagePort.put(key, format.contentType(), whole, size);

		UploadedVideo uploadedVideo = new UploadedVideo(
				UUID.randomUUID(), uploaderUserId, key, videoUrl, format.contentType(), size, durationSec, now);
		try {
			return this.uploadedVideoRepository.save(uploadedVideo);
		}
		catch (RuntimeException e) {
			try {
				this.storagePort.delete(key);
			}
			catch (RuntimeException cleanupFailure) {
				log.warn("기록 저장에 실패한 뒤 고아가 된 동영상도 못 지웠다: key={}", key, cleanupFailure);
			}
			throw e;
		}
	}

	private String buildKey(VideoFormat format, Instant now) {
		String yearMonth = KEY_DATE_FORMAT.withZone(ZoneOffset.UTC).format(now);
		return "story-video/" + yearMonth + "/" + UUID.randomUUID() + "." + format.extension();
	}

	/** 진짜 형식이 MP4 가 아니다 — 415. {@code .mov} 도 여기로 온다. */
	public static class UnsupportedVideoException extends RuntimeException {

		public UnsupportedVideoException() {
			super("지원하지 않는 동영상 형식이다. MP4 만 올릴 수 있다.");
		}
	}

	/** 상한을 넘었다 — 413. 상한은 설정값이라 배포마다 다를 수 있다. */
	public static class VideoTooLargeException extends RuntimeException {

		private final long byteSize;

		private final long maxBytes;

		public VideoTooLargeException(long byteSize, long maxBytes) {
			super("동영상 크기가 허용치를 넘었다: " + byteSize + " > " + maxBytes);
			this.byteSize = byteSize;
			this.maxBytes = maxBytes;
		}

		public long byteSize() {
			return this.byteSize;
		}

		public long maxBytes() {
			return this.maxBytes;
		}
	}

	/** 빈 파일이다 — 400. */
	public static class EmptyVideoException extends RuntimeException {

		public EmptyVideoException() {
			super("빈 파일은 올릴 수 없다.");
		}
	}
}
