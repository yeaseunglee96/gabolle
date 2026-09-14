import { Slot } from 'expo-router';
import { View } from 'react-native';
import { PlanWebNav } from '@/plan/PlanWebNav';

export default function PublicPlanLayout() { return <View style={{ flex: 1 }}><PlanWebNav /><Slot /></View>; }
