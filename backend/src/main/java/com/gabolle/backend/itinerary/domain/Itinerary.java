package com.gabolle.backend.itinerary.domain;

/**
 * 한 여행의 일정. 실체는 {@link ItineraryVersion} 들이고, 이 클래스는 "지금 최신이 몇 번인가" 를 안다.
 */
public class Itinerary {

    private final String itineraryId;
    private final String tripId;

    /** 409 판정의 기준값. */
    private int latestVersion;

    public Itinerary(String itineraryId, String tripId, int latestVersion) {
        this.itineraryId = itineraryId;
        this.tripId = tripId;
        this.latestVersion = latestVersion;
    }

    /**
     * 편집 요청이 최신 판을 바탕으로 하고 있는지 확인한다.
     * 이 검사만으로는 부족하다 — 확인과 저장 사이에 다른 요청이 끼어들면 두 요청이 모두 통과할 수
     * 있다. 마지막 방어선은 DB 의 {@code UNIQUE (itinerary_id, version)} 이고, 그 실패를 다시
     * 409 로 바꾼다. 이 메서드는 흔한 경우를 빨리 걸러내는 것이고 진짜 보장은 DB 가 한다.
     *
     * @param baseVersion 클라이언트가 화면에서 보고 있던 판 번호
     * @throws StaleItineraryVersionException 그 사이에 누가 고쳤을 때
     */
    public void assertEditableFrom(int baseVersion) {
        if (baseVersion != latestVersion) {
            throw new StaleItineraryVersionException(itineraryId, baseVersion, latestVersion);
        }
    }

    /**
     * 검증하고 다음 판 번호를 돌려준다.
     * 번호를 latestVersion 이 아니라 검증된 baseVersion 에서 만든다. 두 요청이 동시에 latest=5 를
     * 읽었을 때, 앞선 쪽이 6번을 저장한 뒤 뒤엣쪽이 latest 를 다시 읽으면 6이라 7번을 만들어
     * 저장에 성공한다 — 5번을 바탕으로 한 편집이 409 없이 통과하고 판이 하나 건너뛰어진다.
     * baseVersion + 1 로 고정하면 둘 다 6번을 시도하고 UNIQUE 제약이 하나만 통과시킨다.
     *
     * @throws StaleItineraryVersionException 그 사이에 누가 고쳤을 때
     */
    public int nextVersionFrom(int baseVersion) {
        assertEditableFrom(baseVersion);
        return baseVersion + 1;
    }

    /** 새 판이 저장된 뒤 포인터를 옮긴다. */
    public void moveTo(int newVersion) {
        if (newVersion != latestVersion + 1) {
            // 판 번호가 건너뛰면 그 사이 판이 사라진 것이다. 이력이 끊긴다.
            throw new IllegalStateException(
                    "판 번호는 하나씩 올라야 한다: " + latestVersion + " → " + newVersion);
        }
        this.latestVersion = newVersion;
    }

    public String itineraryId() { return itineraryId; }
    public String tripId()      { return tripId; }
    public int latestVersion()  { return latestVersion; }
}
