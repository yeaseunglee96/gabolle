package com.gabolle.backend.dish.application;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * 모델이 준 큰 그림을 보관할 크기로 줄인다.
 *
 * <p>원본은 1MB 가 넘는 1024x1024 PNG 인데 화면에는 손바닥만 하게 뜬다. 그대로 두면 표가 그만큼
 * 늘고, 여행지 식당 안의 통신망으로 그 1MB 를 매번 내려받게 된다.
 *
 * <p>JPEG 에는 투명이 없어, 투명한 PNG 를 그대로 쓰면 투명했던 자리가 검게 나온다. 그래서 흰 바탕을
 * 먼저 깐다.
 */
public final class DishImageShrinker {

	/** 보관 형식. 사진이라 JPEG 가 맞고, 자바 기본 {@code ImageIO} 가 쓸 수 있다. */
	public static final String CONTENT_TYPE = "image/jpeg";

	private DishImageShrinker() {
	}

	/**
	 * @param original 모델이 준 그림 바이트
	 * @param longestSide 줄인 뒤 긴 변의 길이. 원본이 이미 이보다 작으면 안 키운다
	 * @param quality JPEG 품질 (0~1)
	 * @return 줄여서 JPEG 로 바꾼 바이트
	 * @throws IOException 그림으로 못 읽었다
	 */
	public static byte[] shrink(byte[] original, int longestSide, float quality) throws IOException {
		BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
		if (source == null) {
			throw new IOException("그림으로 읽을 수 없는 바이트다");
		}

		int width = source.getWidth();
		int height = source.getHeight();
		// 원본이 이미 작으면 키우지 않는다. 키워 봐야 흐려지기만 하고 용량만 는다.
		double scale = Math.min(1.0, (double) longestSide / Math.max(width, height));
		int targetWidth = Math.max(1, (int) Math.round(width * scale));
		int targetHeight = Math.max(1, (int) Math.round(height * scale));

		// TYPE_INT_RGB 는 투명 칸이 없다 — 그래서 흰 바탕을 먼저 칠하고 그 위에 그린다.
		BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setColor(java.awt.Color.WHITE);
			graphics.fillRect(0, 0, targetWidth, targetHeight);
			graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		}
		finally {
			graphics.dispose();
		}

		return toJpeg(target, quality);
	}

	private static byte[] toJpeg(BufferedImage image, float quality) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
		if (!writers.hasNext()) {
			throw new IOException("JPEG 쓰기를 할 수 없다");
		}
		ImageWriter writer = writers.next();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(stream);
			ImageWriteParam parameters = writer.getDefaultWriteParam();
			if (parameters.canWriteCompressed()) {
				parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				parameters.setCompressionQuality(quality);
			}
			writer.write(null, new IIOImage(image, null, null), parameters);
		}
		finally {
			writer.dispose();
		}
		return out.toByteArray();
	}
}
