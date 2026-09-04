import { EmptyTabScreen } from '@/components/EmptyTabScreen';
export default function MapHome() { return <EmptyTabScreen active="map" eyebrow="TRIP MAP" title="여행 지도" description="일정이 만들어지면 방문 순서와 이동 경로를 지도에서 확인할 수 있어요." actionLabel="내 여행 확인하기" actionPath="/trips" />; }
