package com.gabolle.backend.trip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.domain.TravelModes;

/** 화면의 {@code WALK/CAR/TRANSIT} 셋이 {@code trip.travel_modes} 값으로 바뀌는지 본다. */
class TravelModesTest {

    @Test
    @DisplayName("WALK 는 [WALK] 로 매핑된다")
    void walkMapsToWalk() {
        assertArrayEquals(new String[] {"WALK"}, TravelModes.fromTransportPreference("WALK"));
    }

    @Test
    @DisplayName("CAR 는 [PRIVATE_CAR] 로 매핑된다")
    void carMapsToPrivateCar() {
        assertArrayEquals(new String[] {"PRIVATE_CAR"}, TravelModes.fromTransportPreference("CAR"));
    }

    @Test
    @DisplayName("TRANSIT 은 [BUS, SUBWAY] 로 매핑된다")
    void transitMapsToBusAndSubway() {
        assertArrayEquals(new String[] {"BUS", "SUBWAY"}, TravelModes.fromTransportPreference("TRANSIT"));
    }

    @Test
    @DisplayName("소문자·공백은 정규화 뒤 매핑된다")
    void lowercaseAndWhitespaceAreNormalized() {
        assertArrayEquals(new String[] {"BUS", "SUBWAY"}, TravelModes.fromTransportPreference(" transit "));
        assertArrayEquals(new String[] {"WALK"}, TravelModes.fromTransportPreference("walk"));
    }

    @Test
    @DisplayName("모르는 값은 거부된다")
    void unknownValueIsRejected() {
        var e = assertThrows(TravelModes.UnsupportedTravelModeException.class,
                () -> TravelModes.fromTransportPreference("HELICOPTER"));
        assertEquals("HELICOPTER", e.code());
    }

    @Test
    @DisplayName("배열 JSON 이 그대로 오면 거부된다 - 지어내서 해석하지 않는다")
    void arrayJsonIsRejected() {
        assertThrows(TravelModes.UnsupportedTravelModeException.class,
                () -> TravelModes.fromTransportPreference("[\"BUS\",\"SUBWAY\"]"));
    }

    @Test
    @DisplayName("null 값은 거부된다")
    void nullValueIsRejected() {
        assertThrows(TravelModes.UnsupportedTravelModeException.class,
                () -> TravelModes.fromTransportPreference(null));
    }
}
