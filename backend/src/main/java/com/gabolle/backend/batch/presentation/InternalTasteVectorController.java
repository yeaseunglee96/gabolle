package com.gabolle.backend.batch.presentation;

import java.time.OffsetDateTime;

import org.springframework.context.annotation.Profile;
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
 * 취향 벡터 배치 — 기계만 부른다. MLOps Phase 1.
 *
 * <pre>
 * GET  /internal/v1/batch/taste-vectors/stale?asOf=...&amp;limit=500   접을 사람 목록
 * POST /internal/v1/batch/taste-vectors/rebuild                     넘긴 사람들을 접는다
 * </pre>
 *
 * <h2>🔴 경로에 판 번호({@code /v1/})가 붙어 있다</h2>
 *
 * 이 계약은 <b>파트 브랜치 둘에 나뉘어 산다.</b> 서버는 {@code back/dev} 에, 부르는 DAG 는
 * {@code common/dev} 에 있고, 각자 다른 사다리로 올라가 서로 다른 때에 배포된다. 그래서
 * <b>둘이 잠깐 어긋나는 시간이 반드시 생긴다.</b>
 *
 * <p>판 번호가 없으면 그 시간에 옛 DAG 가 새 서버를 부르면서 <b>조용히 다른 뜻</b>으로
 * 동작한다. 번호가 있으면 모양을 바꿀 때 {@code /v2/} 를 새로 내고 한동안 둘을 같이 두면
 * 되므로, 배포 순서를 맞추지 않아도 된다.
 *
 * <p>🔴 <b>모양을 바꾸면 번호를 올린다.</b> 칸을 더하는 것은 번호를 안 올려도 되지만,
 * <b>칸 이름을 바꾸거나 없애는 것</b>은 올린다 — 부르는 쪽이 그 이름으로 읽기 때문이다.
 *
 * <h2>🔴 인가는 이 컨트롤러가 하지 않는다</h2>
 *
 * {@code SecurityConfig} 의 {@code requestMatchers("/internal/**").hasRole("INTERNAL")} 한 줄이
 * 한다. 권한은 {@code InternalTokenAuthenticationFilter} 가 {@code X-Internal-Token} 헤더를
 * 보고 심는다. {@code @PreAuthorize} 를 안 쓰는 이유는 이 저장소가 메서드 보안을 껐기
 * 때문이다 — 붙여도 <b>조용히 무시된다</b> ({@code AdminModerationController} 의 같은 설명).
 *
 * <h2>🔴 {@code asOf} 를 서버가 정하지 않는다 — 반드시 받는다</h2>
 *
 * 기본값을 "지금" 으로 두면, 8월 1일을 채우는 backfill 이 9월 데이터를 먹는다.
 * 그 오류는 값이 있기는 하므로 DB 도 검사도 안 잡는다. 그래서 <b>없으면 400</b> 이다 —
 * 배치가 시끄럽게 멈추는 것이 조용히 틀린 값을 적는 것보다 낫다.
 *
 * <h2>🔴 응답에 {@code ApiResponse} 봉투를 씌우지 않는다</h2>
 *
 * 사람이 쓰는 API 는 {@code data}/{@code error}/{@code meta} 봉투를 쓰고, 그 봉투는
 * {@code X-Request-Id} 를 요구한다. 이 경로는 우리 프로세스 둘 사이의 통로라 봉투를 씌우면
 * DAG 코드가 매번 {@code data} 를 벗기는 일만 늘어난다. 대신 <b>같은 이유로</b> 이 경로는
 * 공개 API 계약이 아니다 — 프론트가 이것을 부르면 안 된다.
 */
@RestController
@RequestMapping("/internal/v1/batch/taste-vectors")
@Profile({ "db", "dev" })
public class InternalTasteVectorController {

	private final TasteVectorBatchService batchService;

	public InternalTasteVectorController(TasteVectorBatchService batchService) {
		this.batchService = batchService;
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
		TasteVectorBatchService.StalePage page = this.batchService.staleUsers(asOf, limit);
		return new StaleUsersResponse(asOf, page.userIds().size(), page.truncated(), page.limit(), page.userIds());
	}

	/** 넘긴 사람들을 접는다. 한 사람의 실패는 배치를 멈추지 않는다. */
	@PostMapping("/rebuild")
	public TasteVectorBatchResponse rebuild(@Valid @RequestBody RebuildTasteVectorsRequest request) {
		TasteVectorBatchReport report = this.batchService.rebuild(request.userIds(), request.asOf());

		return new TasteVectorBatchResponse(report.asOf(), report.processed(),
				report.count(TasteVectorFoldOutcome.Action.REBUILT),
				report.count(TasteVectorFoldOutcome.Action.WATERMARK_ADVANCED),
				report.count(TasteVectorFoldOutcome.Action.UNCHANGED),
				report.count(TasteVectorFoldOutcome.Action.NOTHING_TO_FOLD), report.failed(), report.failedUserIds(),
				report.failures());
	}
}
