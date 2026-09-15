package com.gabolle.backend.menuscan.application;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * 사진에 딸려 오는 <b>보이지 않는 정보</b>를 지운다 — S15P21E201-1025.
 *
 * <h2>🔴 왜 필요한가 — 개인정보 처리방침에 적힌 약속이다</h2>
 *
 * 방침에 <b>"보내기 전에 촬영 위치 정보를 지운다"</b> 가 들어갔다. 사진 파일에는 찍은
 * <b>좌표·시각·기기 정보</b>가 같이 들어 있고(EXIF), 그대로 바깥 모델로 보내면
 * <b>방침이 거짓이 된다.</b>
 *
 * <p>화면도 보내기 전에 지운다. 그래도 여기서 한 번 더 지우는 이유는 <b>서버가 마지막
 * 문</b>이기 때문이다 — 화면이 한 곳에서 빠뜨리거나, 나중에 다른 클라이언트가 붙으면
 * 그 약속이 조용히 깨진다. 한 겹이 아니라 두 겹이어야 한다.
 *
 * <h2>어떻게 지우나 — 다시 그린다</h2>
 *
 * EXIF 칸을 하나씩 찾아 지우지 않고, <b>픽셀만 읽어 새 파일로 다시 쓴다.</b> 그러면
 * 우리가 모르는 칸까지 전부 사라진다 — 지울 목록을 관리하는 방식은 <b>새 칸이 생기면
 * 조용히 새어 나간다.</b>
 *
 * <p>🔴 대가가 있다. 다시 쓰면서 화질이 조금 떨어지고, 회전 정보(Orientation)도 함께
 * 사라져 <b>세로로 찍은 사진이 눕는 경우</b>가 있다. 이 API 는 사람에게 사진을 보여주는
 * 것이 아니라 <b>모델이 글자를 읽게 하는 것</b>이라, 그 대가가 위치 정보를 흘리는 것보다
 * 훨씬 싸다. 사람에게 보여 줄 일이 생기면 그때 회전만 따로 살린다.
 */
final class ImageMetadataStripper {

	private ImageMetadataStripper() {
	}

	/**
	 * @return 픽셀만 남은 JPEG 바이트
	 * @throws IOException 이미지로 읽을 수 없다 — 🔴 <b>원본을 그대로 내보내지 않는다.</b>
	 *     «지우지 못했으니 그냥 보내자» 는 방침을 어기는 쪽으로 기우는 선택이다
	 */
	static byte[] toCleanJpeg(byte[] original) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
		if (image == null) {
			throw new IOException("이미지로 읽을 수 없는 파일이다");
		}

		// 🔴 투명도가 있는 PNG 를 그대로 JPEG 로 쓰면 색이 깨진다. 흰 바탕에 얹어 평평하게
		//    만든다 — 메뉴판은 대부분 흰 바탕이라 글자 읽기에 불리하지 않다.
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
