interface Props {
  language?: "KO" | "EN";
}

/** 여행 준비 탭의 "부산 여행 팁" 카드. 특정 일정 데이터에 의존하지 않는 고정 안내문이다. */
export function RegionalTipCard({ language = "KO" }: Props) {
  const en = language === "EN";
  return (
    <div className="info-card regional-tip-card">
      <div className="info-card-title">{en ? "Busan travel notes" : "부산 여행 팁"}</div>
      <p className="regional-tip-intro">{en ? "Small details that make moving through Busan easier." : "부산은 바다와 산 사이로 지역이 길게 이어져 있어, 동네별 이동 시간을 넉넉히 잡는 것이 중요합니다."}</p>
      <dl className="regional-tip-list">
        <div><dt>{en ? "Getting around" : "이동"}</dt><dd>{en ? "Group stops by neighborhood. Crossing between Haeundae and the old downtown can take longer than expected." : "해운대·광안리와 남포동·영도는 생활권이 다릅니다. 하루에 먼 권역을 여러 번 오가지 말고 같은 동네를 묶어 이동하세요."}</dd></div>
        <div><dt>{en ? "Busy hours" : "혼잡 시간"}</dt><dd>{en ? "Allow extra time around major stations during weekday commuting hours and beach districts on weekends." : "평일 07:30–09:00, 17:30–19:30에는 주요 환승역과 도로가 혼잡합니다. 주말 해운대·광안리 이동은 20–30분 여유를 두세요."}</dd></div>
        <div><dt>{en ? "Food" : "식사"}</dt><dd>{en ? "Popular local restaurants often have queues and short break times between lunch and dinner." : "돼지국밥·밀면·회처럼 유명한 식당은 점심 12시 전후 대기가 깁니다. 11시대 또는 14시 이후 방문하고 재료 소진 여부를 확인하세요."}</dd></div>
        <div><dt>{en ? "Weather" : "해안 날씨"}</dt><dd>{en ? "Coastal wind can feel cooler than the forecast. Keep a light outer layer and check rain radar." : "바닷가는 예보보다 바람이 강하고 체감온도가 낮을 수 있습니다. 얇은 겉옷을 챙기고 여름에는 짧은 소나기 가능성을 함께 확인하세요."}</dd></div>
        <div><dt>{en ? "Before visiting" : "방문 전 확인"}</dt><dd>{en ? "Markets and small independent shops may change closing days or stop taking orders early." : "전통시장과 개인 운영 매장은 휴무일·마지막 주문 시간이 자주 달라집니다. 당일 영업 여부와 주차 가능 여부를 한 번 더 확인하세요."}</dd></div>
      </dl>
    </div>
  );
}
