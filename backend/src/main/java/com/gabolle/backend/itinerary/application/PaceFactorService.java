package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.PaceFactor;
import com.gabolle.backend.itinerary.domain.PaceFactorRepository;

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

    private final PaceFactorRepository paceFactorRepository;

    private final Clock clock;

    public PaceFactorService(PaceFactorRepository paceFactorRepository, Clock clock) {
        this.paceFactorRepository = paceFactorRepository;
        this.clock = clock;
    }

    /**
     * 그 사람의 계수를 다시 계산해 저장한다. 표본이 모자라면 저장하지 않고 빈 값을 답한다.
     *
     * <p>🔴 표본이 모자란다고 factor 1.0 을 대신 저장하지 않는다 — 1.0 은 "재 보니
     * 계획대로다" 라는 관측이고, 저장을 건너뛰는 것은 "아직 못 재봤다" 는 무지다. 이 둘을
     * 같은 값으로 남기면 화면이 틀린 안심을 그린다({@link PaceFactorCalculator} 주석).
     */
    public Optional<PaceFactor> recompute(String userId, List<ItineraryItem> items,
            List<ItineraryItemActual> actuals) {
        Optional<PaceFactorCalculator.Result> result = PaceFactorCalculator.calculate(items, actuals);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        PaceFactorCalculator.Result r = result.get();
        PaceFactor saved = this.paceFactorRepository.appendVersion(
                userId, r.factor(), r.sampleCount(), r.observedUntil(), this.clock.instant());
        return Optional.of(saved);
    }

    /** 지금 쓰는 계수. 없으면 빈 값. */
    public Optional<PaceFactor> current(String userId) {
        return this.paceFactorRepository.findCurrent(userId);
    }
}
