package com.gabolle.backend.menuscan.application;

import java.io.IOException;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.menuscan.adapter.GmsMenuReader;
import com.gabolle.backend.menuscan.config.MenuScanProperties;
import com.gabolle.backend.menuscan.presentation.dto.MenuScanResponse;

/**
 * 메뉴판 사진에서 글자를 읽는다.
 *
 * <p>사진은 저장하지 않는다. 요청에 실려 와서 다시 그려지고 모델에게 간 뒤 사라진다 — 개인정보
 * 처리방침에 그렇게 적혀 있고, 메뉴판 사진에는 얼굴·영수증·카드번호가 같이 찍힌다. 기록 사진 업로드
 * 경로를 재활용하지 않는 것도 그래서다 — 그쪽은 주소만 알면 로그인 없이 열린다.
 *
 * <p>실패를 빈 결과로 바꾸지 않는다. 이 API 에서 빈 결과는 «알레르기 낱말이 없구나»로 읽힌다.
 * 설정이 없을 때·한도를 넘겼을 때·못 읽었을 때 모두 명확한 실패로 올린다.
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
			// «설정이 없어 못 읽었다»와 «읽었는데 못 찾았다»는 다른 뜻이다. 조용히 빈
			// 결과를 주면 둘이 같아진다.
			throw new MenuScanUnavailableException("메뉴판 읽기가 아직 준비되지 않았습니다");
		}

		// 세는 것을 호출 앞에 둔다. 실패한 호출도 크레딧을 쓰므로 «성공한 것만 센다»로
		// 두면 고갈 경로가 열린다.
		this.rateLimiter.takeOrThrow(userId);

		byte[] clean;
		try {
			// 위치 정보를 지우고 보낸다. 화면이 한 번 지워도 서버가 마지막 문이다.
			clean = ImageMetadataStripper.toCleanJpeg(image);
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("사진을 읽을 수 없습니다");
		}

		GmsMenuReader.Result result = this.reader.read(clean, language);
		return MenuScanResponse.of(result.lines(), result.unreadLineCount());
	}

	/** 설정이 없어 지금은 못 읽는다. 빈 결과가 아니다. */
	public static class MenuScanUnavailableException extends RuntimeException {

		public MenuScanUnavailableException(String message) {
			super(message);
		}
	}
}
