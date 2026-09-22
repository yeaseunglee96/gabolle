package com.gabolle.backend.batch.presentation;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.batch.application.TasteVectorBatchReport;
import com.gabolle.backend.batch.application.TasteVectorBatchService;
import com.gabolle.backend.batch.application.TasteVectorFoldOutcome;
import com.gabolle.backend.batch.presentation.dto.RebuildTasteVectorsRequest;
import com.gabolle.backend.batch.presentation.dto.StaleUsersResponse;
import com.gabolle.backend.batch.presentation.dto.TasteVectorBatchResponse;

import jakarta.validation.Valid;

/**
 * 취향 벡터 배치 — 기계만 부른다.
 *
 * <p>경로의 판 번호({@code /v1/})는 서버와 부르는 DAG 이 다른 브랜치에서 서로 다른 때에
 * 배포되어 잠깐 어긋나기 때문에 있다. 칸을 더할 때는 안 올려도 되지만 칸 이름을 바꾸거나
 * 없앨 때는 올린다 — 부르는 쪽이 그 이름으로 읽는다.
 *
 * <p>인가는 이 컨트롤러가 하지 않는다. {@code SecurityConfig} 의
 * {@code requestMatchers("/internal/**").hasRole("INTERNAL")} 한 줄이 막고, 권한은
 * {@code InternalTokenAuthenticationFilter} 가 심는다. 이 저장소는 메서드 보안을 껐으므로
 * {@code @PreAuthorize} 를 붙여도 조용히 무시된다.
 *
 * <p>{@code asOf} 는 서버가 정하지 않고 반드시 받는다. 기본값을 "지금" 으로 두면 과거 구간을
 * 채우는 backfill 이 엉뚱한 데이터를 먹는데, 값이 있기는 해서 DB 도 검사도 못 잡는다.
 *
 * <p>응답에 {@code ApiResponse} 봉투를 씌우지 않는다. 우리 프로세스 둘 사이의 통로라 DAG 이
 * 매번 {@code data} 를 벗기는 일만 는다. 같은 이유로 이 경로는 공개 API 계약이 아니다.
 */
@RestController
@RequestMapping("/internal/v1/batch/taste-vectors")
@Profile({ "db", "dev" })
public class InternalTasteVectorController {

	/**
	 * {@code asOf} 가 "지금" 보다 이만큼까지는 앞서도 받는다. 0 이 아닌 것은 시계가 둘이기
	 * 때문이다 — {@code asOf} 를 만드는 쪽과 검사하는 이 서버의 시계가 몇 초 어긋나는 것만으로
	 * 정상 실행이 막히면 안 된다. 잡으려는 것은 연 단위로 틀린 값이라 한 시간이면 넉넉하다.
	 */
	private static final Duration MAX_FUTURE_SKEW = Duration.ofHours(1);

	private final TasteVectorBatchService batchService;

	private final Clock clock;

	public InternalTasteVectorController(TasteVectorBatchService batchService, Clock clock) {
		this.batchService = batchService;
		this.clock = clock;
	}

	/**
	 * 미래의 {@code asOf} 를 막는다.
	 *
	 * <p>표시는 앞으로만 가고 되돌리는 문이 없다. {@code fold} 가 {@code observed_until} 을
	 * {@code asOf} 로 밀어 놓으면 다음부터는 그보다 이른 {@code asOf} 가 {@code UNCHANGED} 로
	 * 넘어가고, 컬럼에 CHECK 도 없다. 한 번 먼 미래를 적으면 그 사람은 손으로 {@code UPDATE}
	 * 하기 전까지 영영 안 접히는데, 목록이 비어 나오므로 아무 데도 빨간 것이 없다.
	 *
	 * <p>과거는 얼마든지 받는다. 여기서 하는 것은 시각을 고르는 것이 아니라 말이 안 되는 값을
	 * 거절하는 것이고, backfill 은 전부 과거라 이 검사에 안 걸린다.
	 *
	 * <p>이 경로는 봉투를 안 쓰므로 advice 를 더 두는 대신 {@link ResponseStatusException} 으로
	 * 그대로 400 을 낸다.
	 */
	private void requireSaneAsOf(OffsetDateTime asOf) {
		OffsetDateTime limit = OffsetDateTime.now(this.clock).plus(MAX_FUTURE_SKEW);
		if (asOf.isAfter(limit)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"asOf 가 미래다: " + asOf + " (허용 상한 " + limit + "). 표시는 되돌릴 수 없어서 "
							+ "미래 시각을 한 번 적으면 그 사람은 영영 안 접힌다");
		}
	}

	/**
	 * 표시가 뒤처진 사람 목록.
	 *
	 * @param asOf 이 시각까지 도착한 것을 본다. ISO-8601 (예 {@code 2026-09-07T15:00:00Z})
	 * @param limit 최대 몇 명. {@link TasteVectorBatchService#MAX_BATCH} 로 잘린다
	 */
	@GetMapping("/stale")
	public StaleUsersResponse stale(@RequestParam("asOf") OffsetDateTime asOf,
			@RequestParam(name = "limit", defaultValue = "500") int limit) {
		// 읽기만 하는 경로지만 같이 막는다 — 미래 asOf 로 부르면 "전부 뒤처졌다" 는 목록이
		// 나오고, 부르는 쪽은 그것을 그대로 rebuild 에 넘긴다.
		requireSaneAsOf(asOf);
		TasteVectorBatchService.StalePage page = this.batchService.staleUsers(asOf, limit);
		return new StaleUsersResponse(asOf, page.userIds().size(), page.truncated(), page.limit(), page.userIds());
	}

	/** 넘긴 사람들을 접는다. 한 사람의 실패는 배치를 멈추지 않는다. */
	@PostMapping("/rebuild")
	public TasteVectorBatchResponse rebuild(@Valid @RequestBody RebuildTasteVectorsRequest request) {
		// 표시를 실제로 미는 자리다. 여기를 지나면 되돌릴 방법이 없다.
		requireSaneAsOf(request.asOf());
		TasteVectorBatchReport report = this.batchService.rebuild(request.userIds(), request.asOf());

		return new TasteVectorBatchResponse(report.asOf(), report.processed(),
				report.count(TasteVectorFoldOutcome.Action.REBUILT),
				report.count(TasteVectorFoldOutcome.Action.WATERMARK_ADVANCED),
				report.count(TasteVectorFoldOutcome.Action.UNCHANGED),
				report.count(TasteVectorFoldOutcome.Action.NOTHING_TO_FOLD), report.failed(), report.failedUserIds(),
				report.failures());
	}
}
