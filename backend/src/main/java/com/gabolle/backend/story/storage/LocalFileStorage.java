package com.gabolle.backend.story.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * M1 의 {@link StoragePort} 구현 — 서버 디스크에 그대로 쓴다(S15P21E201-174, S15P21E201-367 로
 * 버킷이 정해진 뒤에는 {@code gabolle.storage.provider=s3} 환경에서 {@link S3FileStorage} 로
 * 바뀐다 — 기본값은 여전히 이 구현이다).
 *
 * <h2>🔴 임시 파일에 쓴 뒤 옮긴다</h2>
 * {@code root/<key>} 에 바로 쓰면, 쓰는 도중에 같은 키를 읽는 요청이 반쪽짜리 파일을 받을 수
 * 있다. 그래서 같은 디렉터리에 임시 파일을 먼저 쓰고 {@link Files#move} 로 옮긴다 — 같은 파일
 * 시스템 안의 이동은 원자적이라 읽는 쪽은 "없다" 또는 "완성된 파일" 둘 중 하나만 본다.
 *
 * <h2>🔴 키 검증이 없으면 서버 파일을 읽거나 덮어쓸 수 있다</h2>
 * {@code key} 는 호출자(사실상 {@code ImageUploadService})가 만들지만, 이 클래스 하나가
 * "여기까지만 허용한다" 를 지키는 마지막 자리다. {@code ../../etc/passwd} 같은 키를 그대로
 * {@code root.resolve(key)} 에 넘기면 저장소 바깥의 파일을 가리킬 수 있다. 그래서
 * {@link #KEY_PATTERN}(첫 글자는 영숫자, 이후 영숫자·{@code /_-.})과 {@code ".."} 부분 문자열
 * 금지를 함께 검사한다 — 패턴만으로는 {@code "a/../b"} 처럼 문자 종류는 다 허용되지만 상위
 * 디렉터리로 올라가는 경로를 못 막는다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(prefix = "gabolle.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements StoragePort {

	private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9/_\\-.]*$");

	private final StorageProperties properties;

	public LocalFileStorage(StorageProperties properties) {
		this.properties = properties;
	}

	@Override
	public String put(String key, String contentType, byte[] bytes) {
		validateKey(key);
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Path temp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
			try {
				Files.write(temp, bytes);
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException | RuntimeException e) {
				Files.deleteIfExists(temp);
				throw e;
			}
		}
		catch (IOException e) {
			throw new StorageException("사진 파일을 저장하지 못했다: " + key, e);
		}
		return properties.getPublicBasePath() + "/" + key;
	}

	/**
	 * 스트림으로 저장한다 — {@code Files.write(byte[])} 대신 {@code Files.copy} 한 줄이다.
	 * 임시 파일에 쓰고 원자적으로 옮기는 것은 위와 같다: 반쯤 쓰인 파일이 보이면 안 된다.
	 */
	@Override
	public String put(String key, String contentType, InputStream in, long size) {
		validateKey(key);
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Path temp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
			try {
				Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException | RuntimeException e) {
				Files.deleteIfExists(temp);
				throw e;
			}
		}
		catch (IOException e) {
			throw new StorageException("동영상 파일을 저장하지 못했다: " + key, e);
		}
		return properties.getPublicBasePath() + "/" + key;
	}

	@Override
	public void delete(String key) {
		validateKey(key);
		Path target = resolve(key);
		try {
			// 없는 키를 지우는 것도 성공이다 — deleteIfExists 가 이미 그렇게 동작한다(멱등).
			Files.deleteIfExists(target);
		}
		catch (IOException e) {
			throw new StorageException("사진 파일을 지우지 못했다: " + key, e);
		}
	}

	@Override
	public Optional<StoredObject> get(String key) {
		validateKey(key);
		Path target = resolve(key);
		if (!Files.isRegularFile(target)) {
			return Optional.empty();
		}
		try {
			byte[] bytes = Files.readAllBytes(target);
			return Optional.of(new StoredObject(guessContentType(key), bytes));
		}
		catch (IOException e) {
			throw new StorageException("사진 파일을 읽지 못했다: " + key, e);
		}
	}

	private Path resolve(String key) {
		return this.properties.getRoot().resolve(key).normalize();
	}

	/**
	 * 🔴 확장자로만 판단한다. 파일 시스템의 MIME 추정({@code Files.probeContentType})은 운영체제마다
	 * 설치된 타입 목록이 달라 webp 같은 최신 형식을 못 알아보는 환경이 있다. 이 저장소에 들어오는
	 * 파일은 이미 {@code ImageSanitizer} 를 거친 셋 중 하나뿐이라 확장자 판단으로 충분하다.
	 */
	private String guessContentType(String key) {
		String lower = key.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		if (lower.endsWith(".png")) {
			return "image/png";
		}
		if (lower.endsWith(".webp")) {
			return "image/webp";
		}
		return "application/octet-stream";
	}

	private void validateKey(String key) {
		if (key == null || !KEY_PATTERN.matcher(key).matches() || key.contains("..")) {
			throw new IllegalArgumentException("올바르지 않은 저장 키다: " + key);
		}
	}
}
