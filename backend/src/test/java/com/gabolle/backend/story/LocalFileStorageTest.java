package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gabolle.backend.story.storage.LocalFileStorage;
import com.gabolle.backend.story.storage.StorageProperties;
import com.gabolle.backend.story.storage.StoragePort;

/** {@link LocalFileStorage} — 서버 디스크에 직접 쓰는 저장소 구현. */
class LocalFileStorageTest {

	@TempDir
	Path tempDir;

	private LocalFileStorage storage;

	@BeforeEach
	void setUp() {
		StorageProperties properties = new StorageProperties();
		properties.setRoot(this.tempDir);
		properties.setPublicBasePath("/api/v1/uploads/images");
		this.storage = new LocalFileStorage(properties);
	}

	@Test
	void putWritesFileToDisk() throws IOException {
		byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

		this.storage.put("story/2026/09/a.jpg", "image/jpeg", content);

		Path written = this.tempDir.resolve("story/2026/09/a.jpg");
		assertThat(Files.exists(written)).isTrue();
		assertThat(Files.readAllBytes(written)).isEqualTo(content);
	}

	@Test
	void putReturnsPublicBasePathPlusKey() {
		String url = this.storage.put("story/2026/09/b.png", "image/png", new byte[] { 1, 2, 3 });

		assertThat(url).isEqualTo("/api/v1/uploads/images/story/2026/09/b.png");
	}

	@Test
	void getReturnsWhatWasPut() {
		byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
		this.storage.put("story/x.jpg", "image/jpeg", content);

		Optional<StoragePort.StoredObject> found = this.storage.get("story/x.jpg");

		assertThat(found).isPresent();
		assertThat(found.get().bytes()).isEqualTo(content);
		assertThat(found.get().contentType()).isEqualTo("image/jpeg");
	}

	@Test
	void getReturnsEmptyWhenMissing() {
		assertThat(this.storage.get("story/nope.jpg")).isEmpty();
	}

	@Test
	void deleteRemovesFileAndIsIdempotent() {
		this.storage.put("story/y.jpg", "image/jpeg", "hi".getBytes(StandardCharsets.UTF_8));

		this.storage.delete("story/y.jpg");
		assertThat(this.storage.get("story/y.jpg")).isEmpty();

		// 두 번째도 예외 없이 성공해야 한다 — 뒷정리 재시도가 이 성질에 기댄다.
		this.storage.delete("story/y.jpg");
	}

	@Test
	void rejectsKeyWithParentDirectoryTraversal() {
		assertThatThrownBy(() -> this.storage.put("../x", "image/jpeg", new byte[] { 1 }))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsKeyContainingTraversalInMiddle() {
		assertThatThrownBy(() -> this.storage.get("story/../../etc/passwd"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
