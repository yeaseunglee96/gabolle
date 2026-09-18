package com.gabolle.backend.dish.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.gabolle.backend.dish.adapter.GmsDishDescriber;
import com.gabolle.backend.dish.config.DishProperties;
import com.gabolle.backend.dish.domain.DishDescription;
import com.gabolle.backend.dish.domain.DishImage;
import com.gabolle.backend.dish.domain.DishNameKey;
import com.gabolle.backend.dish.presentation.dto.DishResponse;
import com.gabolle.backend.dish.repository.DishDescriptionRepository;
import com.gabolle.backend.dish.repository.DishImageRepository;

/**
 * 메뉴에서 읽은 음식 하나에 <b>설명과 그림</b>을 붙인다 — S15P21E201-1272.
 *
 * <h2>왜 메뉴판을 읽을 때 같이 안 주나</h2>
 *
 * 2026-09-18 에 잰 값이다.
 *
 * <ul>
 *   <li>설명까지 한 번에 받으면 <b>10.63초</b> — 메뉴판 읽기 제한 8초를 넘는다</li>
 *   <li>그림은 가장 빠른 설정으로도 <b>10.9초</b> — 앱이 끊는 12초 안에 못 들어온다</li>
 * </ul>
 *
 * 메뉴 한 장에는 줄이 열 개 넘게 나오는데, <b>사용자는 그중 한둘만 궁금하다.</b> 그래서
 * 음식 하나를 눌렀을 때 그 하나만 만든다. 설명은 그 자리에서 주고(1.3~1.9초), 그림은
 * 만들기 시작만 하고 화면이 조금 뒤에 다시 물어본다.
 *
 * <h2>🔴 이 클래스는 트랜잭션을 걸지 않는다</h2>
 *
 * 그림 자리를 <b>저장한 뒤에</b> 다른 스레드를 깨우는데, 이 메서드가 트랜잭션 안이면
 * 그 저장이 아직 커밋 전이라 <b>깨어난 스레드가 없는 행을 찾는다.</b> 한도를 세는
 * {@link DishImageRateLimiter} 는 자기 트랜잭션을 따로 갖는다.
 *
 * <h2>같은 음식을 두 번 만들지 않는다</h2>
 *
 * 두 사람이 같은 음식을 동시에 누르면 둘 다 「행이 없다」를 보고 둘 다 넣으려 한다.
 * 마지막 관문은 표의 {@code UNIQUE(name_key)} 다 — 진 쪽은 여기서 그것을 받아
 * <b>이긴 쪽의 행을 다시 읽는다.</b> 「먼저 확인」으로는 절대 못 막는 자리다.
 */
@Service
@Profile({ "db", "dev" })
public class DishService {

	/**
	 * 실패한 그림을 다시 만들어 보기까지 기다리는 시간.
	 *
	 * <p>바로 다시 만들면 중계가 잠깐 아픈 동안 누를 때마다 값이 나간다. 영영 안 만들면
	 * 그날 한 번 아팠던 음식이 <b>영원히 그림 없는 음식</b>으로 남는다. 하루가 그 사이다.
	 */
	private static final Duration RETRY_FAILED_AFTER = Duration.ofDays(1);

	private final GmsDishDescriber describer;

	private final DishImageWorker worker;

	private final DishImageRateLimiter rateLimiter;

	private final DishDescriptionRepository descriptions;

	private final DishImageRepository images;

	private final DishProperties properties;

	private final Clock clock;

	public DishService(GmsDishDescriber describer, DishImageWorker worker,
			DishImageRateLimiter rateLimiter, DishDescriptionRepository descriptions,
			DishImageRepository images, DishProperties properties, Clock clock) {
		this.describer = describer;
		this.worker = worker;
		this.rateLimiter = rateLimiter;
		this.descriptions = descriptions;
		this.images = images;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param userId 누가 물어봤나 — 그림 한도를 이 사람 앞으로 센다
	 * @param rawName 사진에서 읽은 음식 이름
	 * @param language 앱 언어. 설명이 이 언어로 온다
	 */
	public DishResponse describe(UUID userId, String rawName, String language) {
		String name = clampName(rawName);
		String nameKey = DishNameKey.of(name);
		if (nameKey.isEmpty()) {
			throw new IllegalArgumentException("음식 이름이 없습니다");
		}
		if (!this.describer.isConfigured()) {
			// 🔴 «설정이 없어 못 물었다» 와 «모델이 모르는 음식이다» 는 완전히 다른 뜻이다.
			//    조용히 빈 설명을 주면 둘이 같아진다.
			throw new DishUnavailableException("음식 설명이 아직 준비되지 않았습니다");
		}

		String languageKey = (language == null) ? "" : language;
		GmsDishDescriber.Described described = describedFor(nameKey, languageKey, name);

		ImageState image = imageStateFor(userId, nameKey, described);
		return DishResponse.of(name, described.description(), image.status(), image.imageId());
	}

	/** 저장해 둔 설명이 있으면 그것을 쓰고, 없으면 한 번 물어보고 저장한다. */
	private GmsDishDescriber.Described describedFor(String nameKey, String languageKey, String name) {
		Optional<DishDescription> saved = this.descriptions.findByNameKeyAndLanguage(nameKey, languageKey);
		if (saved.isPresent()) {
			return new GmsDishDescriber.Described(saved.get().getDescription(),
					saved.get().getImagePrompt());
		}

		GmsDishDescriber.Described described = this.describer.describe(name, languageKey);
		try {
			this.descriptions.save(DishDescription.of(UUID.randomUUID(), nameKey, languageKey,
					described.description(), described.imagePrompt(), OffsetDateTime.now(this.clock)));
		}
		catch (DataIntegrityViolationException exception) {
			// 다른 요청이 방금 같은 것을 넣었다. 우리가 받은 설명과 내용이 거의 같으므로
			// 그대로 쓴다 — 굳이 다시 읽지 않는다.
		}
		return described;
	}

	/**
	 * 그림이 어디까지 왔나 보고, 없으면 만들기 시작한다.
	 *
	 * <p>🔴 <b>한도는 실제로 만들기 시작할 때만 센다.</b> 저장해 둔 그림을 꺼내 주는 것은
	 * 바깥을 안 부르므로 값이 안 나간다.
	 */
	private ImageState imageStateFor(UUID userId, String nameKey, GmsDishDescriber.Described described) {
		if (described.isEmpty() || described.imagePrompt().isBlank()) {
			// 🔴 모르는 음식은 그리지 않는다. 묘사 없이 이름만 주고 그리게 하면 모델이
			//    그럴듯한 <b>다른 음식</b>을 그린다 — 그것이 화면에서는 「이 음식이 이렇게
			//    생겼다」로 읽힌다.
			return new ImageState(DishResponse.IMAGE_NONE, null);
		}

		Optional<DishImage> existing = this.images.findByNameKey(nameKey);
		if (existing.isPresent()) {
			DishImage row = existing.get();
			if (DishImage.READY.equals(row.getStatus())) {
				return new ImageState(DishResponse.IMAGE_READY, row.getId());
			}
			if (DishImage.PENDING.equals(row.getStatus())) {
				return new ImageState(DishResponse.IMAGE_PENDING, row.getId());
			}
			return retryOrKeepFailed(userId, row, described);
		}

		return startPainting(userId, nameKey, described);
	}

	private ImageState startPainting(UUID userId, String nameKey, GmsDishDescriber.Described described) {
		this.rateLimiter.takeOrThrow(userId);

		DishImage row = DishImage.pending(UUID.randomUUID(), nameKey, OffsetDateTime.now(this.clock));
		try {
			this.images.save(row);
		}
		catch (DataIntegrityViolationException exception) {
			// 다른 요청이 한발 빨랐다. 그 행을 그대로 쓴다 — 같은 음식을 두 번 만들지 않는다.
			return this.images.findByNameKey(nameKey)
					.map((winner) -> new ImageState(statusOf(winner), winner.getId()))
					.orElse(new ImageState(DishResponse.IMAGE_FAILED, null));
		}

		// 🔴 저장이 커밋된 뒤에 깨운다. 이 클래스에 트랜잭션이 없는 이유가 이 한 줄이다.
		this.worker.paint(row.getId(), described.imagePrompt());
		return new ImageState(DishResponse.IMAGE_PENDING, row.getId());
	}

	private ImageState retryOrKeepFailed(UUID userId, DishImage row,
			GmsDishDescriber.Described described) {
		OffsetDateTime now = OffsetDateTime.now(this.clock);
		if (row.getUpdatedAt().isAfter(now.minus(RETRY_FAILED_AFTER))) {
			return new ImageState(DishResponse.IMAGE_FAILED, row.getId());
		}

		this.rateLimiter.takeOrThrow(userId);
		row.markPendingAgain(now);
		this.images.save(row);
		this.worker.paint(row.getId(), described.imagePrompt());
		return new ImageState(DishResponse.IMAGE_PENDING, row.getId());
	}

	private static String statusOf(DishImage row) {
		return switch (row.getStatus()) {
			case DishImage.READY -> DishResponse.IMAGE_READY;
			case DishImage.FAILED -> DishResponse.IMAGE_FAILED;
			default -> DishResponse.IMAGE_PENDING;
		};
	}

	/**
	 * 만들어 둔 그림을 꺼낸다.
	 *
	 * <p>🔴 아직 안 된 것과 못 만든 것을 <b>같은 답으로 주지 않는다.</b> 앞은 화면이 다시
	 * 물어볼 일이고 뒤는 그만 물어볼 일이다. 같으면 화면이 영원히 다시 묻는다.
	 */
	public StoredImage image(UUID imageId) {
		DishImage row = this.images.findById(imageId)
				.orElseThrow(() -> new DishImageNotFoundException("그런 그림이 없습니다"));

		if (DishImage.READY.equals(row.getStatus()) && row.getImageBytes() != null) {
			return new StoredImage(row.getImageBytes(), row.getContentType());
		}
		if (DishImage.PENDING.equals(row.getStatus())) {
			throw new DishImageNotReadyException("아직 만들고 있습니다");
		}
		throw new DishImageNotFoundException("그림을 만들지 못했습니다");
	}

	private String clampName(String name) {
		String trimmed = (name == null) ? "" : name.trim();
		int max = this.properties.getMaxNameLength();
		return (trimmed.length() <= max) ? trimmed : trimmed.substring(0, max);
	}

	private record ImageState(String status, UUID imageId) {
	}

	/** 화면에 그대로 내보낼 그림 한 장. */
	public record StoredImage(byte[] bytes, String contentType) {
	}

	/** 설정이 없어 지금은 설명을 못 받는다. 🔴 「모델이 모르는 음식」이 아니다. */
	public static class DishUnavailableException extends RuntimeException {

		public DishUnavailableException(String message) {
			super(message);
		}
	}

	/** 아직 만드는 중이다 — 화면은 조금 뒤에 다시 물어본다. */
	public static class DishImageNotReadyException extends RuntimeException {

		public DishImageNotReadyException(String message) {
			super(message);
		}
	}

	/** 그런 그림이 없거나, 만들지 못했다. */
	public static class DishImageNotFoundException extends RuntimeException {

		public DishImageNotFoundException(String message) {
			super(message);
		}
	}
}
