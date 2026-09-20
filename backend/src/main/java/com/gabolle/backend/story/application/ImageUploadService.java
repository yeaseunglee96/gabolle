package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.image.ImageFormat;
import com.gabolle.backend.story.image.ImageSanitizer;
import com.gabolle.backend.story.image.ImageSniffer;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.story.storage.StoragePort;

/**
 * 사진 업로드.
 *
 * <p>형식은 {@link ImageSniffer#sniff} 가 내용을 보고 정한다 — 파일 이름과 Content-Type 은 안 본다.
 * {@link ImageSanitizer#strip} 이 촬영 위치 정보(EXIF GPS) 등 메타데이터를 뺀 뒤 크기가 바뀌므로
 * 상한 검사를 정리 전후로 두 번 한다. 저장하는 {@code byteSize} 는 정리한 뒤의 크기다.
 *
 * <p>{@code @Transactional} 을 안 둔다. 저장소 쓰기는 네트워크 호출이라 전체를 묶으면 그동안 DB
 * 커넥션 하나를 놀리며 붙잡는다. 저장소 메서드가 자기 트랜잭션을 여는 것으로 충분하다. 대신 DB 삽입이
 * 실패하면 이미 올라간 파일이 고아로 남으므로 방금 쓴 키를 지워 보상한다(그 실패는 삼킨다).
 */
@Service
@Profile({ "db", "dev" })
public class ImageUploadService {

	private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

	private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

	private final StoragePort storagePort;

	private final UploadedImageRepository uploadedImageRepository;

	private final Clock clock;

	public ImageUploadService(StoragePort storagePort, UploadedImageRepository uploadedImageRepository, Clock clock) {
		this.storagePort = storagePort;
		this.uploadedImageRepository = uploadedImageRepository;
		this.clock = clock;
	}

	public UploadedImage upload(UUID uploaderUserId, byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			throw new EmptyImageException();
		}
		if (bytes.length > UploadedImage.MAX_BYTES) {
			throw new ImageTooLargeException(bytes.length, UploadedImage.MAX_BYTES);
		}

		ImageFormat format = ImageSniffer.sniff(bytes);
		byte[] sanitized = ImageSanitizer.strip(bytes, format);
		if (sanitized.length > UploadedImage.MAX_BYTES) {
			throw new ImageTooLargeException(sanitized.length, UploadedImage.MAX_BYTES);
		}

		Instant now = this.clock.instant();
		String key = buildKey(format, now);
		String imageUrl = this.storagePort.put(key, format.contentType(), sanitized);

		UploadedImage uploadedImage = new UploadedImage(
				UUID.randomUUID(), uploaderUserId, key, imageUrl, format.contentType(), sanitized.length, now);
		try {
			return this.uploadedImageRepository.save(uploadedImage);
		}
		catch (RuntimeException e) {
			try {
				this.storagePort.delete(key);
			}
			catch (RuntimeException cleanupFailure) {
				log.warn("기록 저장에 실패한 뒤 고아가 된 파일도 못 지웠다: key={}", key, cleanupFailure);
			}
			throw e;
		}
	}

	private String buildKey(ImageFormat format, Instant now) {
		String yearMonth = KEY_DATE_FORMAT.withZone(ZoneOffset.UTC).format(now);
		return "story/" + yearMonth + "/" + UUID.randomUUID() + "." + format.extension();
	}

	/** 진짜 형식이 JPEG·PNG·WebP 가 아니다 — 415. */
	public static class UnsupportedImageException extends RuntimeException {

		public UnsupportedImageException() {
			super("지원하지 않는 이미지 형식이다. JPEG·PNG·WebP 만 올릴 수 있다.");
		}
	}

	/** 3MB 를 넘었다 — 413. */
	public static class ImageTooLargeException extends RuntimeException {

		private final int byteSize;

		private final int maxBytes;

		public ImageTooLargeException(int byteSize, int maxBytes) {
			super("사진 크기가 허용치를 넘었다: " + byteSize + " > " + maxBytes);
			this.byteSize = byteSize;
			this.maxBytes = maxBytes;
		}

		public int byteSize() {
			return this.byteSize;
		}

		public int maxBytes() {
			return this.maxBytes;
		}
	}

	/** 빈 파일이다 — 400. */
	public static class EmptyImageException extends RuntimeException {

		public EmptyImageException() {
			super("빈 파일은 올릴 수 없다.");
		}
	}
}
