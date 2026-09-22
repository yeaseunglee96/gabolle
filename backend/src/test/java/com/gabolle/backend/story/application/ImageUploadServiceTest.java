package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.story.storage.StoragePort;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code upload} 는 {@code @Transactional} 없이 돈다. DB 저장이 실패하면 이미 올라간 파일을
 * 직접 지워야 고아가 안 남는다.
 */
class ImageUploadServiceTest {

	private final StoragePort storagePort = mock(StoragePort.class);
	private final UploadedImageRepository uploadedImageRepository = mock(UploadedImageRepository.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC);
	private final ImageUploadService service = new ImageUploadService(this.storagePort,
			this.uploadedImageRepository, this.clock);

	// PNG 서명(8바이트) + 빈 IEND 청크(길이 4 + 타입 4 + 데이터 0 + CRC 4) — ImageSanitizer.stripPng 가
	// 요구하는 최소 유효 구조다.
	private static final byte[] PNG_BYTES = {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
			0, 0, 0, 0, 'I', 'E', 'N', 'D', 0, 0, 0, 0,
	};

	@Test
	@DisplayName("🔴 DB 저장이 실패하면 방금 올린 파일을 보상 삭제한다")
	void compensatesStorageWriteWhenDbSaveFails() {
		when(this.storagePort.put(anyString(), anyString(), any())).thenReturn("https://cdn.example/x.png");
		when(this.uploadedImageRepository.save(any())).thenThrow(new RuntimeException("DB 저장 실패"));

		assertThatThrownBy(() -> this.service.upload(UUID.randomUUID(), PNG_BYTES))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("DB 저장 실패");

		verify(this.storagePort, times(1)).delete(anyString());
	}

	@Test
	@DisplayName("보상 삭제 자체가 실패해도 원래 예외가 그대로 올라간다")
	void originalExceptionSurvivesCompensationFailure() {
		when(this.storagePort.put(anyString(), anyString(), any())).thenReturn("https://cdn.example/x.png");
		when(this.uploadedImageRepository.save(any())).thenThrow(new RuntimeException("DB 저장 실패"));
		org.mockito.Mockito.doThrow(new StoragePort.StorageException("삭제도 실패"))
				.when(this.storagePort).delete(anyString());

		assertThatThrownBy(() -> this.service.upload(UUID.randomUUID(), PNG_BYTES))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("DB 저장 실패");
	}

	@Test
	@DisplayName("저장이 성공하면 보상 삭제를 부르지 않는다")
	void doesNotCompensateOnSuccess() {
		when(this.storagePort.put(anyString(), anyString(), any())).thenReturn("https://cdn.example/x.png");
		when(this.uploadedImageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		UploadedImage result = this.service.upload(UUID.randomUUID(), PNG_BYTES);

		org.assertj.core.api.Assertions.assertThat(result).isNotNull();
		verify(this.storagePort, never()).delete(anyString());
	}
}
