-- 페이스 러닝: 일정 항목에 실측 도착/출발/체류시간을 기록한다.
ALTER TABLE "ItineraryItem" ADD COLUMN "actualArrival" TEXT;
ALTER TABLE "ItineraryItem" ADD COLUMN "actualDeparture" TEXT;
ALTER TABLE "ItineraryItem" ADD COLUMN "actualStayMinutes" INTEGER;
