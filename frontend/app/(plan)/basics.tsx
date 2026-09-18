// 🔴 이 화면은 조건 한 페이지로 합쳐졌다 (S15P21E201-1233).
//
// 시안 p1 이 기본 정보·취향·제약을 한 페이지로 합쳤다. 이 화면(4단계 중 1단계)은
// 그 안에 흡수됐고, 날짜·인원·출발지는 홈의 시작 바가 받는다.
//
// 지우지 않고 넘겨보내는 이유: 이 주소로 가는 링크가 앱 안팎에 남아 있을 수 있고,
// 지우면 그 링크가 「화면을 찾을 수 없어요」로 떨어진다.
import { Redirect } from 'expo-router';

export default function PlanBasicsMoved() {
  return <Redirect href="/plan" />;
}
