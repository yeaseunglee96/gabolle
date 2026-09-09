import json
import sys

from ortools.constraint_solver import pywrapcp, routing_enums_pb2


def solve(payload):
    places = payload["places"]
    count = len(places)
    if count == 0:
        return {"status": "OPTIMAL", "orderedPlaceIds": [], "arrivals": []}

    manager = pywrapcp.RoutingIndexManager(count + 1, 1, 0)
    routing = pywrapcp.RoutingModel(manager)
    travel = payload["travelMinutes"]
    distances = payload["distanceMeters"]
    day_start = payload["dayStartMin"]
    day_end = payload["dayEndMin"]

    def time_callback(from_index, to_index):
        from_node = manager.IndexToNode(from_index)
        to_node = manager.IndexToNode(to_index)
        stay = 0 if from_node == 0 else places[from_node - 1]["stayMinutes"]
        return stay + travel[from_node][to_node]

    time_index = routing.RegisterTransitCallback(time_callback)
    routing.SetArcCostEvaluatorOfAllVehicles(time_index)
    # 누적값은 00:00 기준 절대 분(예: 11:30=690)이므로 capacity도 하루 종료 절대시각이어야 한다.
    routing.AddDimension(time_index, 60, day_end, False, "Time")
    time_dim = routing.GetDimensionOrDie("Time")
    time_dim.CumulVar(routing.Start(0)).SetRange(day_start, day_start)
    time_dim.CumulVar(routing.End(0)).SetRange(day_start, day_end)

    for node, place in enumerate(places, start=1):
        index = manager.NodeToIndex(node)
        latest_arrival = min(place["closeMin"] - place["stayMinutes"], day_end - place["stayMinutes"])
        if latest_arrival < place["openMin"]:
            routing.AddDisjunction([index], 0)
            continue
        time_dim.CumulVar(index).SetRange(max(day_start, place["openMin"]), latest_arrival)
        if not place["mustVisit"]:
            score_penalty = max(1_000, int(place["score"] * 100_000))
            if place["category"] == "TOURIST":
                score_penalty += 12_000
            routing.AddDisjunction([index], score_penalty)

    def cost_callback(from_index, to_index):
        to_node = manager.IndexToNode(to_index)
        return 0 if to_node == 0 else places[to_node - 1]["cost"]

    cost_index = routing.RegisterTransitCallback(cost_callback)
    routing.AddDimension(cost_index, 0, payload["dayBudget"], True, "Budget")

    def visit_callback(from_index, to_index):
        return 0 if manager.IndexToNode(to_index) == 0 else 1
    visit_index = routing.RegisterTransitCallback(visit_callback)
    routing.AddDimension(visit_index, 0, payload["maxItems"], True, "VisitCount")

    def food_callback(from_index, to_index):
        to_node = manager.IndexToNode(to_index)
        if to_node == 0:
            return 0
        return 1 if places[to_node - 1]["category"] in ("RESTAURANT", "CAFE") else 0
    food_index = routing.RegisterTransitCallback(food_callback)
    routing.AddDimension(food_index, 0, payload["maxFoodStops"], True, "FoodStops")

    def restaurant_callback(from_index, to_index):
        to_node = manager.IndexToNode(to_index)
        return 1 if to_node != 0 and places[to_node - 1]["category"] == "RESTAURANT" else 0
    restaurant_index = routing.RegisterTransitCallback(restaurant_callback)
    routing.AddDimension(restaurant_index, 0, payload["maxRestaurantStops"], True, "RestaurantStops")

    # 관광·문화·체험 장소를 가능한 한 일정의 절반 이상 포함한다. 후보나 시간창이
    # 부족할 때 전체 해가 실패하지 않도록 hard constraint가 아닌 soft lower bound다.
    def tourist_callback(from_index, to_index):
        to_node = manager.IndexToNode(to_index)
        return 1 if to_node != 0 and places[to_node - 1]["category"] == "TOURIST" else 0
    tourist_index = routing.RegisterTransitCallback(tourist_callback)
    routing.AddDimension(tourist_index, 0, payload["maxItems"], True, "TouristStops")
    tourist_dim = routing.GetDimensionOrDie("TouristStops")
    desired_tourist = min(
        sum(1 for place in places if place["category"] == "TOURIST"),
        max(1, payload["maxItems"] - payload["maxFoodStops"]),
    )
    tourist_dim.SetCumulVarSoftLowerBound(routing.End(0), desired_tourist, 30_000)

    if not payload["hasCar"]:
        def distance_callback(from_index, to_index):
            return distances[manager.IndexToNode(from_index)][manager.IndexToNode(to_index)]
        distance_index = routing.RegisterTransitCallback(distance_callback)
        routing.AddDimension(distance_index, 0, payload["maxWalkingM"], True, "WalkingDistance")

    search = pywrapcp.DefaultRoutingSearchParameters()
    search.first_solution_strategy = routing_enums_pb2.FirstSolutionStrategy.PARALLEL_CHEAPEST_INSERTION
    search.local_search_metaheuristic = routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
    search.time_limit.FromMilliseconds(payload.get("timeoutMs", 1500))
    solution = routing.SolveWithParameters(search)
    if solution is None:
        return {"status": "INFEASIBLE", "orderedPlaceIds": [], "arrivals": []}

    ids = []
    arrivals = []
    index = routing.Start(0)
    while not routing.IsEnd(index):
        node = manager.IndexToNode(index)
        if node != 0:
            ids.append(places[node - 1]["id"])
            arrivals.append(solution.Value(time_dim.CumulVar(index)))
        index = solution.Value(routing.NextVar(index))
    return {"status": "FEASIBLE", "orderedPlaceIds": ids, "arrivals": arrivals}


try:
    request = json.load(sys.stdin)
    print(json.dumps(solve(request), ensure_ascii=False))
except Exception as exc:
    print(json.dumps({"status": "ERROR", "message": str(exc)}, ensure_ascii=False))
    sys.exit(1)
