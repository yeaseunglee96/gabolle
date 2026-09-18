package com.gabolle.backend.dish;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.dish.application.DishImageShrinker;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모델이 준 큰 그림을 보관할 크기로 줄이는가 — S15P21E201-1272.
 *
 * <p>모델이 주는 것은 1,358KB 짜리 1024 PNG 인데 화면에는 손바닥만 하게 뜬다. 그대로
 * 두면 <b>앱이 그 1MB 를 통신망으로 내려받는다</b> — 이 기능을 쓰는 자리가 여행지의 식당
 * 안이고, 데이터도 그 사람이 낸다.
 */
class DishImageShrinkerTest {

	private static byte[] png(int width, int height, Color color, boolean withAlpha) throws Exception {
		BufferedImage image = new BufferedImage(width, height,
				withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			if (!withAlpha) {
				graphics.setColor(color);
				graphics.fillRect(0, 0, width, height);
			}
		}
		finally {
			graphics.dispose();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static BufferedImage read(byte[] bytes) throws Exception {
		return ImageIO.read(new ByteArrayInputStream(bytes));
	}

	@Test
	@DisplayName("큰 그림은 정해진 길이로 줄어든다")
	void aBigImageIsScaledDown() throws Exception {
		byte[] shrunk = DishImageShrinker.shrink(png(1024, 1024, Color.ORANGE, false), 512, 0.82f);

		BufferedImage image = read(shrunk);
		assertThat(image.getWidth()).isEqualTo(512);
		assertThat(image.getHeight()).isEqualTo(512);
	}

	@Test
	@DisplayName("긴 변을 기준으로 줄인다 — 비율이 안 망가진다")
	void theLongerSideDecidesTheScale() throws Exception {
		byte[] shrunk = DishImageShrinker.shrink(png(1000, 500, Color.ORANGE, false), 500, 0.82f);

		BufferedImage image = read(shrunk);
		assertThat(image.getWidth()).isEqualTo(500);
		assertThat(image.getHeight()).isEqualTo(250);
	}

	/** 🔴 키우면 흐려지기만 하고 용량만 는다. 작은 원본은 그대로 둔다. */
	@Test
	@DisplayName("🔴 원본이 이미 작으면 키우지 않는다")
	void aSmallImageIsNotEnlarged() throws Exception {
		byte[] shrunk = DishImageShrinker.shrink(png(200, 200, Color.ORANGE, false), 512, 0.82f);

		BufferedImage image = read(shrunk);
		assertThat(image.getWidth()).isEqualTo(200);
	}

	/**
	 * 🔴 JPEG 에는 투명이 없다. 투명한 PNG 를 그대로 JPEG 로 쓰면 투명했던 자리가
	 * <b>검게</b> 나온다 — 음식 그림에 검은 얼룩이 생기는 것을 눈으로 보기 전에는 모른다.
	 */
	@Test
	@DisplayName("🔴 투명한 그림은 흰 바탕에 깔린다 — 검게 나오지 않는다")
	void transparencyBecomesWhiteNotBlack() throws Exception {
		byte[] shrunk = DishImageShrinker.shrink(png(64, 64, null, true), 512, 0.82f);

		BufferedImage image = read(shrunk);
		Color corner = new Color(image.getRGB(0, 0));
		assertThat(corner.getRed()).isGreaterThan(240);
		assertThat(corner.getGreen()).isGreaterThan(240);
		assertThat(corner.getBlue()).isGreaterThan(240);
	}

	@Test
	@DisplayName("줄인 것이 원본보다 훨씬 작다 — 줄이는 이유가 이것이다")
	void theResultIsMuchSmaller() throws Exception {
		byte[] original = png(1024, 1024, Color.ORANGE, false);
		byte[] shrunk = DishImageShrinker.shrink(original, 512, 0.82f);

		assertThat(shrunk.length).isLessThan(original.length);
	}

	@Test
	@DisplayName("그림이 아닌 바이트는 실패로 올린다 — 빈 그림을 만들지 않는다")
	void nonImageBytesFail() {
		assertThat(org.assertj.core.api.Assertions
				.catchThrowable(() -> DishImageShrinker.shrink("그림이 아니다".getBytes(), 512, 0.82f)))
				.isInstanceOf(java.io.IOException.class);
	}
}
