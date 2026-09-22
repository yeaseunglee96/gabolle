package com.gabolle.backend.story.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * 사진 파일이 실제로 놓이는 곳 — 이 인터페이스 뒤에 있다.
 *
 * <p>어느 저장소를 쓰는지는 설정으로 고른다 — 서버 디스크({@code LocalFileStorage})든 S3 호환
 * 오브젝트 스토리지든 도메인 코드는 그 차이를 모른다.
 *
 * <p>이 인터페이스는 바이트만 다룬다. 형식 검사와 촬영 위치 정보 제거는 저장하기 전에
 * {@code ImageSanitizer} 가 끝낸다.
 */
public interface StoragePort {

	/**
	 * 저장하고 공개 주소를 돌려준다.
	 *
	 * @param key 저장소 안의 위치. 호출자가 만든다(예: {@code story/2026/09/<uuid>.jpg}). 같은 키로 두 번 넣으면 덮어쓴다
	 * @param contentType {@code image/jpeg} · {@code image/png} · {@code image/webp}
	 * @param bytes 이미 검사·정리가 끝난 파일 내용
	 * @return 그 파일을 열 수 있는 주소
	 * @throws StorageException 저장소에 쓸 수 없다
	 */
	String put(String key, String contentType, byte[] bytes);

	/**
	 * 같은 일을 스트림으로 한다 — 파일이 힙에 통째로 올라가지 않는다. 동영상이 이 길을 쓴다.
	 * 사진은 {@code ImageSanitizer} 가 파일 전체를 메모리에 올려야 해서 바이트 배열 쪽이 맞다.
	 *
	 * <p>기본 구현은 읽어서 {@link #put(String, String, byte[])} 로 넘기므로 동작이 같다. 진짜로
	 * 흘려보내는 것은 실제 구현들이 재정의한다.
	 *
	 * @param size 보낼 바이트 수. S3 호환 저장소가 미리 알아야 한다 — 모르면 SDK 가 내부에서
	 * 버퍼를 잡아 결국 메모리를 쓴다
	 */
	default String put(String key, String contentType, InputStream in, long size) {
		try {
			return put(key, contentType, in.readAllBytes());
		}
		catch (IOException e) {
			throw new StorageException("스트림을 끝까지 읽지 못했다: " + key, e);
		}
	}

	/**
	 * 지운다. 없는 키를 지우는 것은 성공이다 — 두 번 지워도 같은 결과여야 뒷정리 작업이 재시도할 수 있다.
	 *
	 * @throws StorageException 저장소에 닿을 수 없거나 지우기가 거부됐다
	 */
	void delete(String key);

	/** 읽는다. 없으면 빈 값. 저장소가 직접 서빙하는 구현(S3 공개 주소)에서는 쓰지 않을 수 있다. */
	Optional<StoredObject> get(String key);

	/** 저장한 파일 하나. */
	record StoredObject(String contentType, byte[] bytes) {
	}

	/** 저장소 자체의 실패. 도메인이 잡아 "못 지운 목록" 으로 바꾸거나 업로드 실패로 답한다. */
	class StorageException extends RuntimeException {

		public StorageException(String message, Throwable cause) {
			super(message, cause);
		}

		public StorageException(String message) {
			super(message);
		}
	}
}
