package com.gabolle.backend.transit.domain;

import java.util.List;

public record StopArrivals(NearbyBusStop stop, List<BusArrival> arrivals) {
}
