package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.image.ImageFormat;
import com.gabolle.backend.story.image.ImageSanitizer;
import com.gabolle.backend.story.image.ImageSniffer;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.story.storage.StoragePort;

/**
 * 사진 업로드 — API 명세 S15P21E201-216.
 *
 * <h2>검사 순서</h2>
 * <ol>
 *   <li>비었으면 {@link EmptyImageException}(400)</li>
 *   <li>3MB 를 넘으면 {@link ImageTooLargeException}(413) — 화면 검사를 지나온 요청도 여기서 막힌다</li>
 *   <li>{@link ImageSniffer#sniff} 로 진짜 형식을 확인한다 — 파일 이름·Content-Type 은 안 본다</li>
 *   <li>{@link ImageSanitizer#strip} 으로 촬영 위치 정보(EXIF GPS) 등 메타데이터를 뺀다</li>
 *   <li>정리하며 크기가 조금 바뀌므로 3MB 검사를 한 번 더 한다</li>
 *   <li>{@code story/yyyy/MM/<uuid>.<ext>} 키를 만들어({@link Clock} 기준 UTC) 저장소에 쓴다</li>
 *   <li>{@link UploadedImage} 를 저장한다 — {@code byteSize} 는 정리한 <b>뒤</b>의 크기다</li>
 * </ol>
 */
@Service
@Profile({ "db", "dev" })
public class ImageUploadService {

	private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

	private final StoragePort storagePort;

	private final UploadedImageRepository uploadedImageRepository;

	private final Clock clock;

	public ImageUploadService(StoragePort storagePort, UploadedImageRepository uploadedImageRepository, Clock clock) {
		this.storagePort = storagePort;
		this.uploadedImageRepository = uploadedImageRepository;
		this.clock = clock;
	}

	@Transactional
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
		return this.uploadedImageRepository.save(uploadedImage);
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
