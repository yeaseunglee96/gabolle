package com.gabolle.backend.story.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * 사진 파일이 실제로 놓이는 곳 — 이 인터페이스 뒤에 있다.
 *
 * <p>S15P21E201-174 는 "S3 호환 오브젝트 스토리지, 무엇을 쓸지는 아직 정하지 않았다" 고 적었다.
 * 그래서 코드는 저장소가 무엇인지 모르게 만든다. M1 에서는 서버의 디스크에 두는 구현
 * ({@code LocalFileStorage})을 쓰고, 버킷이 정해지면 같은 인터페이스의 구현 하나를 더 만들어 설정으로
 * 바꾼다. 도메인 코드({@code StoryService}·{@code ImageUploadService})는 그 변경을 모른다.
 *
 * <p>🔴 이 인터페이스는 <b>바이트</b>만 다룬다. 형식 검사(JPEG·PNG·WebP 인가)와 촬영 위치 정보 제거는
 * 저장하기 전에 {@code ImageSanitizer} 가 끝낸다 — 저장소는 받은 것을 그대로 둔다.
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
	 * 같은 일을 <b>스트림으로</b> 한다 — 파일이 힙에 통째로 올라가지 않는다. S15P21E201-1275.
	 *
	 * <h2>🔴 왜 더했나 (바꾸지 않고)</h2>
	 *
	 * 사진은 지금 방식이 맞다. {@code ImageSanitizer} 가 촬영 위치를 떼려면 <b>파일 전체가
	 * 메모리에 있어야</b> 하므로, 스트림으로 받아도 어차피 통째로 담게 된다 — 힙은 그대로인데
	 * 코드만 복잡해진다. 그래서 <b>사진 길은 건드리지 않고</b> 통로를 하나 더 낸다.
	 *
	 * <p>동영상은 다르다. 서버가 파일을 <b>열지 않으므로</b>(2026-09-18 결정 — 줄이기·썸네일·길이
	 * 재기를 전부 앱이 한다) 앞 12바이트로 형식만 보고 나머지는 <b>그대로 흘려보내면</b> 된다.
	 *
	 * <h2>🔴 기본 구현이 있는 이유</h2>
	 *
	 * 이 인터페이스를 시험 안에서 {@code new StoragePort() &#123;…&#125;} 로 흉내내는 자리가
	 * <b>넷</b> 있다. 기본 구현 없이 메서드를 더하면 <b>그 넷이 전부 컴파일이 깨진다</b> — 동영상과
	 * 아무 상관 없는 시험들이다. 기본 구현은 읽어서 {@link #put(String, String, byte[])} 로
	 * 넘기므로 <b>동작이 같고</b>, 진짜로 흘려보내는 것은 실제 구현 둘이 재정의한다.
	 *
	 * @param size 보낼 바이트 수. S3 호환 저장소가 <b>미리 알아야</b> 한다 — 모르면 SDK 가 내부에서
	 * 버퍼를 잡아 결국 메모리를 쓴다. 호출자가 이미 알고 있는 값이다(업로드 상한을 재면서 센다)
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
