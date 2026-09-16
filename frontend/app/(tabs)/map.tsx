import { Redirect } from 'expo-router';

export default function MapHome() {
  // 지도는 반드시 실제 여행 식별자로 열어야 한다. 오래된 데모 식별자를 넣으면 사용자는
  // 자신의 여행이 아닌 빈 지도를 보게 되므로 먼저 내 여행을 고르게 한다.
  return <Redirect href="/trips" />;
}
