import { EmptyTabScreen } from '@/components/EmptyTabScreen';
export default function Trips() { return <EmptyTabScreen active="schedule" eyebrow="MY TRIPS" title="내 여행" description="완성한 여행 일정이 여기에 모여요. 먼저 여행 조건을 알려주고 부산 일정을 만들어 보세요." actionLabel="새 여행 만들기" actionPath="/plan/basic" />; }
