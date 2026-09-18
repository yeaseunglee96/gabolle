package com.gabolle.backend.menuscan.application;

import java.io.IOException;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.menuscan.adapter.GmsMenuReader;
import com.gabolle.backend.menuscan.config.MenuScanProperties;
import com.gabolle.backend.menuscan.presentation.dto.MenuScanResponse;

/**
 * 메뉴판 사진에서 글자를 읽는다 — S15P21E201-1025.
 *
 * <h2>🔴 사진은 저장하지 않는다</h2>
 *
 * 요청에 실려 오고, 깨끗이 다시 그려지고, 모델에게 가고, <b>사라진다.</b> 저장 안 하는 것이
 * 정책이자 구현이다 — 개인정보 처리방침에 그렇게 적혀 있고, 메뉴판 사진에는 <b>얼굴·영수증·
 * 카드번호</b>가 같이 찍힌다.
 *
 * <p>🔴 기록 사진 업로드 경로({@code /api/v1/uploads/story-image})를 <b>재활용하지 않는다.</b>
 * 그쪽에 올라간 것은 {@code SecurityConfig} 가 <b>주소만 알면 로그인 없이 열리게</b> 해 뒀다
 * (피드가 {@code <img>} 로 부르기 때문이다). 주소가 UUID 라 추측이 어렵다는 것은
 * <b>접근 제어가 아니다.</b>
 *
 * <h2>🔴 실패를 빈 결과로 바꾸지 않는다</h2>
 *
 * 이 API 에서 빈 결과는 사용자에게 <b>「알레르기 낱말이 없구나」</b> 로 읽힌다. 그래서
 * 설정이 없을 때·한도를 넘겼을 때·못 읽었을 때 모두 <b>명확한 실패</b>로 올린다.
 * 조용한 실패가 특히 위험한 자리다.
 */
@Service
@Profile({ "db", "dev" })
public class MenuScanService {

	private final GmsMenuReader reader;

	private final MenuScanRateLimiter rateLimiter;

	private final MenuScanProperties properties;

	public MenuScanService(GmsMenuReader reader, MenuScanRateLimiter rateLimiter,
			MenuScanProperties properties) {
		this.reader = reader;
		this.rateLimiter = rateLimiter;
		this.properties = properties;
	}

	public MenuScanResponse scan(UUID userId, byte[] image, String language) {
		if (image == null || image.length == 0) {
			throw new IllegalArgumentException("사진이 없습니다");
		}
		if (image.length > this.properties.getMaxImageBytes()) {
			throw new IllegalArgumentException("사진이 너무 큽니다");
		}
		if (!this.reader.isConfigured()) {
			// 🔴 «설정이 없어 못 읽었다» 와 «읽었는데 못 찾았다» 는 사용자에게 완전히
			//    다른 뜻이다. 조용히 빈 결과를 주면 둘이 같아진다.
			throw new MenuScanUnavailableException("메뉴판 읽기가 아직 준비되지 않았습니다");
		}

		// 🔴 세는 것을 호출 **앞**에 둔다. 실패한 호출도 크레딧을 쓰고, 실패를 반복하는 것도
		//    고갈 경로다. «성공한 것만 센다» 로 두면 그 길이 열린다.
		this.rateLimiter.takeOrThrow(userId);

		byte[] clean;
		try {
			// 🔴 위치 정보를 지우고 보낸다. 방침에 적힌 약속이고, 화면이 한 번 지워도
			//    서버가 마지막 문이라 여기서 한 번 더 한다.
			clean = ImageMetadataStripper.toCleanJpeg(image);
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("사진을 읽을 수 없습니다");
		}

		GmsMenuReader.Result result = this.reader.read(clean, language);
		return MenuScanResponse.of(result.lines(), result.unreadLineCount());
	}

	/** 설정이 없어 지금은 못 읽는다. 🔴 빈 결과가 아니다. */
	public static class MenuScanUnavailableException extends RuntimeException {

		public MenuScanUnavailableException(String message) {
			super(message);
		}
	}
}
