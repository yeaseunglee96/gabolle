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
 * 개인 속도 계수를 다시 계산하고 지금 판을 답한다.
 * 계산({@link PaceFactorCalculator})과 저장({@link PaceFactorRepository})을 잇는 자리다.
 * "표본이 모자라면 아예 저장하지 않는다" 는 규칙은 둘 중 어느 쪽 것도 아니라 여기서 정한다.
 * {@link PaceFactorRepository} 의 구현이 {@code db}·{@code dev} 프로필에서만 떠서, 이 서비스가
 * 프로필 없이 뜨면 그 밖의 프로필에서 문이 없는 채로 기동을 시도하다 실패한다.
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
     * 표본이 모자란다고 factor 1.0 을 대신 저장하지 않는다 — 1.0 은 "재 보니 계획대로다" 라는
     * 관측이고, 저장을 건너뛰는 것은 "아직 못 재봤다" 는 무지다.
     * 행동 개인화를 끈 사람에게는 계수를 만들지 않는다. 이 계수는 실제 도착·출발 시각으로 만든
     * 사람별 행동 프로필이라 {@code user_taste_vector} 와 같은 종류의 값이고, 따라서 같은 스위치가
     * 꺼야 한다. 끈 사람에게는 {@code null} 을 준다 — 부르는 쪽은 "표본이 모자라 계수가 없는"
     * 경우를 이미 다루므로 화면이 깨지지 않고, 지연 예측은 계수 없이 계획 그대로 그려진다.
     * 계정을 못 찾으면 만들지 않는다 — 동의를 확인할 수 없는 상태에서 모으는 것보다 안 모으는
     * 것이 맞다.
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
            // UUID 가 아닌 식별자는 이 표의 user_id(UUID NOT NULL)에 애초에 못 들어간다.
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
