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
 * 서버 디스크에 그대로 쓰는 {@link StoragePort} 구현. 설정이 없으면 이것이 기본이고,
 * {@code gabolle.storage.provider=s3} 면 {@link S3FileStorage} 로 바뀐다.
 *
 * <p>임시 파일에 먼저 쓰고 {@link Files#move} 로 옮긴다. 대상 경로에 바로 쓰면 쓰는 도중에 읽는
 * 요청이 반쪽짜리 파일을 받는데, 같은 파일 시스템 안의 이동은 원자적이라 읽는 쪽이 「없다」와
 * 「완성된 파일」 둘 중 하나만 본다.
 *
 * <p>키 검증이 경로 탈출을 막는 마지막 자리다. {@link #KEY_PATTERN} 과 {@code ".."} 금지를 함께
 * 검사하는 것은 패턴만으로는 {@code "a/../b"} 처럼 문자 종류는 다 맞으면서 상위 디렉터리로
 * 올라가는 경로를 못 막기 때문이다.
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
	 * 확장자로만 판단한다. {@code Files.probeContentType} 은 운영체제마다 타입 목록이 달라 webp 를
	 * 못 알아보는 환경이 있고, 여기 들어오는 파일은 이미 {@code ImageSanitizer} 를 거친 셋 중 하나다.
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
