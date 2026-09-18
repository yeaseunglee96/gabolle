package com.gabolle.backend.dish.application;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.gabolle.backend.dish.adapter.GmsDishPainter;
import com.gabolle.backend.dish.config.DishProperties;
import com.gabolle.backend.dish.domain.DishImage;
import com.gabolle.backend.dish.repository.DishImageRepository;

/**
 * 이미 {@code PENDING} 으로 저장된 그림 자리를 <b>실제로 채운다</b> — S15P21E201-1272.
 *
 * <h2>🔴 {@link DishService} 와 클래스를 나눈 이유</h2>
 *
 * {@code @Async} 는 Spring AOP 프록시로 동작한다. 같은 클래스 안에서 자기 메서드를
 * 부르면({@code this.paint(...)}) 프록시를 지나지 않아 <b>조용히 동기로 실행된다.</b>
 * 그러면 사용자는 10초를 그 자리에서 기다리다 앱이 12초에 끊는 것을 보게 된다 —
 * 이 기능 전체가 그 10초를 피하려고 이렇게 생겼는데 증상만 없이 원래대로 돌아간다.
 * {@code RecommendationJobWorker} 가 같은 이유로 분리돼 있다.
 *
 * <h2>🔴 어떤 실패도 행을 PENDING 인 채로 남기지 않는다</h2>
 *
 * 남기면 화면은 <b>영원히 다시 물어본다.</b> 그래서 잡는 것이 「그림 만들기 실패」만이
 * 아니라 {@code RuntimeException} 전부다 — {@code RecommendationJobWorker} 가
 * S15P21E201-604 에서 같은 것으로 한 번 당했다.
 */
@Component
@Profile({ "db", "dev" })
public class DishImageWorker {

	private static final Logger log = LoggerFactory.getLogger(DishImageWorker.class);

	private final GmsDishPainter painter;

	private final DishImageRepository images;

	private final DishProperties properties;

	private final Clock clock;

	public DishImageWorker(GmsDishPainter painter, DishImageRepository images, DishProperties properties,
			Clock clock) {
		this.painter = painter;
		this.images = images;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param dishImageId 이미 {@code PENDING} 으로 <b>저장이 끝난</b> 행의 id. 부르는 쪽이
	 *     저장을 먼저 커밋해 둬야 한다 — 안 그러면 이 스레드가 아직 없는 행을 찾는다
	 * @param imagePrompt {@link com.gabolle.backend.dish.adapter.GmsDishDescriber} 가 만든
	 *     영어 묘사
	 */
	@Async("dishImageExecutor")
	public void paint(UUID dishImageId, String imagePrompt) {
		DishImage row = this.images.findById(dishImageId).orElse(null);
		if (row == null) {
			// 행이 사라졌다면 그릴 자리도 없다. 그림을 만들어 봐야 둘 곳이 없으므로
			// 바깥을 부르기 전에 끝낸다 — 값이 나가는 호출이다.
			log.warn("그림 자리가 없어 만들지 않는다 dishImageId={}", dishImageId);
			return;
		}

		try {
			byte[] original = this.painter.paint(imagePrompt);
			byte[] stored = DishImageShrinker.shrink(original, this.properties.getStoredImageWidth(),
					this.properties.getStoredImageQuality());
			row.markReady(stored, DishImageShrinker.CONTENT_TYPE, OffsetDateTime.now(this.clock));
			this.images.save(row);
			log.info("음식 그림을 만들었다 nameKey={} 원본={}KB 보관={}KB", row.getNameKey(),
					original.length / 1024, stored.length / 1024);
		}
		catch (IOException exception) {
			fail(row, "그림을 줄이지 못했다: " + exception.getMessage(), exception);
		}
		catch (RuntimeException exception) {
			fail(row, exception.getMessage(), exception);
		}
	}

	private void fail(DishImage row, String note, Exception exception) {
		// 🔴 행을 지우지 않고 실패로 적는다. 지우면 다음 사람이 누를 때 또 만들려 들고,
		//    그 음식이 원래 안 되는 것이면 값만 계속 나간다.
		row.markFailed(clampNote(note), OffsetDateTime.now(this.clock));
		this.images.save(row);
		log.warn("음식 그림을 못 만들었다 nameKey={}", row.getNameKey(), exception);
	}

	private static String clampNote(String note) {
		String value = (note == null) ? "알 수 없는 실패" : note;
		return (value.length() <= 300) ? value : value.substring(0, 300);
	}
}
