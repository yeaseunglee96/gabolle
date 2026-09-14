package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.PaceFactor;
import com.gabolle.backend.itinerary.domain.PaceFactorRepository;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 개인 속도 계수를 다시 계산하고 지금 판을 답한다 — S15P21E201-304.
 *
 * <p>계산 자체({@link PaceFactorCalculator})와 저장({@link PaceFactorRepository})을 잇는
 * 자리다. 계산기는 Spring 도 DB 도 모르는 순수 함수이고 저장소는 판 체인을 다루는 것만
 * 아는데, "표본이 모자라면 아예 저장하지 않는다" 는 규칙은 둘 중 어느 쪽 것도 아니라
 * 여기서 정한다.
 *
 * <p>🔴 {@link PaceFactorRepository} 의 구현이 지금은 {@code JpaPaceFactorRepository}
 * 하나뿐이고 그 구현은 {@code db}·{@code dev} 프로필에서만 뜬다({@code @Profile}). 이 서비스가
 * 프로필 없이 뜨면 그 밖의 프로필에서 문이 없는 채로 기동을 시도하다가 실패하므로,
 * {@code ItineraryEditService} 와 같은 이유로 같은 프로필을 붙인다.
 */
@Service
@Profile({ "db", "dev" })
public class PaceFactorService {

    private static final Logger log = LoggerFactory.getLogger(PaceFactorService.class);

    private final PaceFactorRepository paceFactorRepository;

    private final AppUserRepository users;

    private final Clock clock;

    public PaceFactorService(PaceFactorRepository paceFactorRepository, AppUserRepository users, Clock clock) {
        this.paceFactorRepository = paceFactorRepository;
        this.users = users;
        this.clock = clock;
    }

    /**
     * 그 사람의 계수를 다시 계산해 저장한다. 표본이 모자라면 저장하지 않고 빈 값을 답한다.
     *
     * <p>🔴 표본이 모자란다고 factor 1.0 을 대신 저장하지 않는다 — 1.0 은 "재 보니
     * 계획대로다" 라는 관측이고, 저장을 건너뛰는 것은 "아직 못 재봤다" 는 무지다. 이 둘을
     * 같은 값으로 남기면 화면이 틀린 안심을 그린다({@link PaceFactorCalculator} 주석).
     *
     * <h2>🔴 행동 개인화를 끈 사람에게는 계수를 만들지 않는다 (S15P21E201-549 후속)</h2>
     *
     * 이 계수는 <b>행동으로 만든 사람별 프로필</b>이다 — {@code itinerary_item_actual} 에 쌓인
     * 실제 도착·출발 시각으로 "이 사람은 계획보다 30% 느리다" 를 뽑아 판으로 남긴다. 그건
     * {@code user_taste_vector} 와 같은 종류의 값이고, 따라서 같은 스위치가 꺼야 한다.
     *
     * <p>그런데 이 표({@code user_pace_factor}, S15P21E201-304)는 549 가 스위치를 실제로
     * 연결하기 <b>하루 전</b>에 다른 티켓으로 들어왔고, 아무도 두 개를 잇지 않았다. 그래서
     * 개인화를 끈 사람도 일정 화면을 열 때마다 여기서 프로필이 새로 쌓이고 있었다 —
     * {@code EventIngestService} 가 입구를 막고 {@code BehaviorPersonalizationReset} 이 벡터를
     * 지운 <b>뒤에</b>, 다른 문으로 같은 것이 다시 들어온 셈이다.
     *
     * <p>🔴 <b>끈 사람에게는 {@code null} 을 준다.</b> 부르는 쪽
     * ({@code ItineraryPaceService}·{@code ItineraryRhythmService})은 "표본이 모자라 계수가 없는"
     * 경우를 이미 다루므로({@code factorResult.map(...).orElse(null)}) 화면이 깨지지 않는다.
     * 지연 예측은 계수 없이 계획 그대로 그려진다 — 그게 "추측하지 마라" 의 뜻이다.
     *
     * <p>🔴 계정을 못 찾으면 <b>만들지 않는다.</b> {@code EventIngestService.collectsBehaviorOf}
     * 와 같은 판단이다 — 동의를 확인할 수 없는 상태에서 모으는 것보다 안 모으는 것이 맞다.
     */
    public Optional<PaceFactor> recompute(String userId, List<ItineraryItem> items,
            List<ItineraryItemActual> actuals) {
        if (!allowsBehaviorPersonalization(userId)) {
            return Optional.empty();
        }
        Optional<PaceFactorCalculator.Result> result = PaceFactorCalculator.calculate(items, actuals);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        PaceFactorCalculator.Result r = result.get();
        PaceFactor saved = this.paceFactorRepository.appendVersion(
                userId, r.factor(), r.sampleCount(), r.observedUntil(), this.clock.instant());
        return Optional.of(saved);
    }

    private boolean allowsBehaviorPersonalization(String userId) {
        UUID id;
        try {
            id = UUID.fromString(userId);
        }
        catch (IllegalArgumentException | NullPointerException ex) {
            // 🔴 UUID 가 아닌 식별자는 이 표의 user_id(UUID NOT NULL)에 애초에 못 들어간다.
            //    여기서 통과시키면 아래 appendVersion 이 DB 에서 죽는다. 조용히 안 만든다.
            log.warn("사용자 식별자가 UUID 가 아니라 속도 계수를 만들지 않는다 userId={}", userId);
            return false;
        }
        return this.users.findPersonalizationMode(id)
                .filter(PersonalizationMode.BEHAVIOR_ENABLED::equals)
                .isPresent();
    }

    /** 지금 쓰는 계수. 없으면 빈 값. */
    public Optional<PaceFactor> current(String userId) {
        return this.paceFactorRepository.findCurrent(userId);
    }
}
