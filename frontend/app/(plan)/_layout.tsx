import { Slot } from 'expo-router';
import { View } from 'react-native';
import { PlanWebNav } from '@/plan/PlanWebNav';

// 여행 입력 draft는 게스트도 작성할 수 있다. 서버 저장 직전에만 인증을 요구한다.
export default function PlanLayout() { return <View style={{ flex: 1 }}><PlanWebNav /><Slot /></View>; }
