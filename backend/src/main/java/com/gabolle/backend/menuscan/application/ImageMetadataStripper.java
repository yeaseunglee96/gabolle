package com.gabolle.backend.menuscan.application;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * 사진에 딸려 오는 보이지 않는 정보(EXIF 의 좌표·시각·기기)를 지운다.
 *
 * <p>개인정보 처리방침에 «보내기 전에 촬영 위치 정보를 지운다»가 들어 있다. 화면도 보내기 전에
 * 지우지만 서버가 마지막 문이라 여기서 한 번 더 지운다 — 다른 클라이언트가 붙으면 그 약속이 조용히
 * 깨진다.
 *
 * <p>칸을 하나씩 찾아 지우지 않고 픽셀만 읽어 새 파일로 다시 쓴다. 지울 목록을 관리하는 방식은 새
 * 칸이 생기면 조용히 새어 나간다.
 *
 * <p>대가로 화질이 조금 떨어지고 회전 정보가 사라져 세로로 찍은 사진이 눕는다. 이 API 는 사람에게
 * 보여주는 것이 아니라 모델이 글자를 읽게 하는 것이라 그 대가가 더 싸다.
 */
final class ImageMetadataStripper {

	private ImageMetadataStripper() {
	}

	/**
	 * @return 픽셀만 남은 JPEG 바이트
	 * @throws IOException 이미지로 읽을 수 없다. 원본을 그대로 내보내지 않는다 — «지우지 못했으니
	 *     그냥 보내자»는 방침을 어기는 쪽으로 기우는 선택이다
	 */
	static byte[] toCleanJpeg(byte[] original) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
		if (image == null) {
			throw new IOException("이미지로 읽을 수 없는 파일이다");
		}

		// 투명도가 있는 PNG 를 그대로 JPEG 로 쓰면 색이 깨진다. 흰 바탕에 얹어 평평하게
		// 만든다 — 메뉴판은 대부분 흰 바탕이라 글자 읽기에 불리하지 않다.
		BufferedImage flattened = new BufferedImage(image.getWidth(), image.getHeight(),
				BufferedImage.TYPE_INT_RGB);
		var graphics = flattened.createGraphics();
		try {
			graphics.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
		}
		finally {
			graphics.dispose();
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(flattened, "jpg", out)) {
			throw new IOException("JPEG 로 다시 쓰지 못했다");
		}
		return out.toByteArray();
	}
}
