// 🔴 이 화면은 조건 한 페이지로 합쳐졌다 (S15P21E201-1233).
//
// 지우지 않고 넘겨보내는 이유: 이 주소로 가는 링크가 앱 안팎에 남아 있을 수 있고,
// 지우면 그 링크가 「화면을 찾을 수 없어요」로 떨어진다. 넘겨보내면 사람은 원래
// 하려던 일을 그대로 한다.
import { Redirect } from 'expo-router';

export default function PlanTasteMoved() {
  return <Redirect href="/plan" />;
}
