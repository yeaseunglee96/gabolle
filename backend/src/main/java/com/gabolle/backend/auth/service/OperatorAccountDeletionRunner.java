package com.gabolle.backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영자 대리 탈퇴를 서버에서 한 번 실행한다 (S15P21E201-1647). 절차와 명령은 {@code backend/docs/ACCOUNT-DELETION-BY-EMAIL.md}.
 *
 * <pre>
 * docker run -d --network local-route-personalization_data_net --env-file /tmp/load.env \
 *   -e GABOLLE_JWT_SECRET=loader-only-throwaway-value-0123456789abcdef \
 *   local-route-backend:latest \
 *   --gabolle.operator.delete-account.email=사용자@example.com \
 *   --gabolle.operator.delete-account.request-ref=2026-09-26-메일1 \
 *   --gabolle.operator.delete-account.operator=이예승 \
 *   --gabolle.operator.delete-account.execute=true
 * </pre>
 *
 * <p>{@code execute} 를 안 주면 <b>미리보기</b>다 — 무엇이 지워질지만 로그에 남기고 아무것도 바꾸지 않는다.
 *
 * <p>API 가 아니라 실행 인자인 것은 (1) 사람이 본인 확인을 마친 뒤 한 번 하는 일이지 서비스가 제공하는 기능이 아니고,
 * (2) 네트워크로 열린 삭제 경로를 새로 만들면 그것이 곧 공격 표면이라서다. 프로퍼티를 안 주면 이 빈은 만들어지지도 않아
 * 평소 기동에 영향이 없다. 장소 적재기({@code PlaceFeatureLoaderRunner})와 같은 방식이다.
 *
 * <p>🔴 이메일 주소를 로그에 남기지 않는다. 결과 줄에는 계정 번호·로그인 수단·영향 수만 나온다. 실패하면 예외를 그대로 던져
 * 기동이 죽고(로그에 {@code APPLICATION FAILED TO START}) 지워진 것은 없다 — 삭제와 기록은 한 트랜잭션이다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.operator.delete-account.email")
public class OperatorAccountDeletionRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(OperatorAccountDeletionRunner.class);

	/** 감시하는 쪽이 이 줄을 보고 컨테이너를 내린다. 바꾸면 런북의 명령도 같이 바꾼다. */
	public static final String DONE_MARKER = "대리 탈퇴 처리를 마쳤다";

	private final OperatorAccountDeletionService service;

	private final String email;

	private final String requestRef;

	private final String operator;

	private final boolean execute;

	public OperatorAccountDeletionRunner(OperatorAccountDeletionService service,
			@Value("${gabolle.operator.delete-account.email}") String email,
			@Value("${gabolle.operator.delete-account.request-ref:}") String requestRef,
			@Value("${gabolle.operator.delete-account.operator:}") String operator,
			@Value("${gabolle.operator.delete-account.execute:false}") boolean execute) {
		this.service = service;
		this.email = email;
		this.requestRef = requestRef;
		this.operator = operator;
		this.execute = execute;
	}

	@Override
	public void run(ApplicationArguments args) {
		LOGGER.info("대리 탈퇴를 시작한다 — 모드={} 요청={} 운영자={}", this.execute ? "실행" : "미리보기", this.requestRef,
				this.operator);

		OperatorAccountDeletionService.Result result = this.service.process(this.email, this.requestRef,
				this.operator, this.execute);

		String means = String.join(",", result.loginMeans());
		String impact = (result.preview() == null) ? "-"
				: "여행 " + result.preview().ownedTripCount() + " · 일정 " + result.preview().itineraryCount() + " · 기록 "
						+ result.preview().recordCount();
		LOGGER.info("{} — 결과={} 계정={} 로그인수단={} 영향=[{}] 가리킨계정수={}", DONE_MARKER, result.outcome(), result.userId(),
				means.isEmpty() ? "-" : means, impact, result.matchedAccounts());
	}

}
