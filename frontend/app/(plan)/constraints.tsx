// 🔴 이 화면은 조건 한 페이지로 합쳐졌다 (S15P21E201-1233).
//    같은 이유로 지우지 않고 넘겨보낸다 — app/(plan)/taste.tsx 주석 참고.
import { Redirect } from 'expo-router';

export default function PlanConstraintsMoved() {
  return <Redirect href="/plan" />;
}
