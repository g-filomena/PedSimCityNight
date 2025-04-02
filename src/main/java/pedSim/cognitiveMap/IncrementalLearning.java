package pedSim.cognitiveMap;

public class IncrementalLearning {

//	private Agent agent;
//	private CognitiveMap cognitiveMap;
//	public ArrayList<Route> routesSoFar = new ArrayList<>();
//	RouteMeaningfulness routeMeaningfulness = new RouteMeaningfulness();
//
//	private HashMap<NodeGraph, Integer> visitedLocations = new HashMap<>();
//	private HashMap<NodeGraph, Double> visitedLocationsWeights = new HashMap<>();
//
//	public Route lastRoute;
//	Double MEMORY_PERCENTILE = 0.15;
//	int MIN_WALKED_ROUTES_SIZE = Pars.MIN_WALKED_ROUTES_SIZE;
//	private RouteProperties routeProperties;
//	private static final double PERCENTILE_THRESHOLD = 0.8;
//
//	public IncrementalLearning(Agent agent) {
//		this.agent = agent;
//		this.cognitiveMap = agent.getCognitiveMap();

//	}

//	public void updateAgentMemory(Route lastRoute) {
//
//		while (routesSoFar.size() < Pars.MIN_WALKED_ROUTES_SIZE) {
////			agent.getProperties().randomizeRouteChoiceParameters();
//			NodeGraph originNode = cognitiveMap.getHomeNode();
//			NodeGraph destinationNode = cognitiveMap.getWorkNode();
//			RoutePlanner planner = new RoutePlanner(originNode, destinationNode, agent);
//			Route route = planner.definePath();
//			RouteProperties rP = new RouteProperties(route);
//			rP.computeRouteProperties();
//			routesSoFar.add(route);
//		}
//
//		// Once the threshold is reached, recompute the meaningfulness of the initial
//		// routes
//		this.lastRoute = lastRoute;
//		routeProperties = new RouteProperties(lastRoute);
//		routeProperties.computeRouteProperties();
//
//		routeMeaningfulness.computeRouteMeaningfulness(lastRoute, routesSoFar, cognitiveMap);
//		updateVisitedLocations();
//
//		individualLearning();
//
//		// Add the last route to the list of routes walked so far
//		routesSoFar.add(lastRoute);
//	}
//
//	private void updateVisitedLocations() {
//
//		for (NodeGraph location : routeProperties.visitedLocations)
//			visitedLocations.compute(location, (k, v) -> (v == null) ? 1 : v + 1);
//		for (NodeGraph location : visitedLocations.keySet()) {
//			double count = visitedLocations.get(location);
//			visitedLocationsWeights.put(location, count / visitedLocations.size());
//		}
//
//	}
//
//	public void memoryDecay() {
//
//		if (routesSoFar.size() < MIN_WALKED_ROUTES_SIZE || cognitiveMap.collage.size() < MIN_WALKED_ROUTES_SIZE)
//			return;
//
////		adjustMemoryPercentile(); // Dynamically adjust the MEMORY_PERCENTILE
//
//		List<Geometry> cognitiveSpacesToPreserve = new ArrayList<>();
//		if (!visitedLocationsWeights.isEmpty())
//			cognitiveSpacesToPreserve = new ArrayList<>(getSpacesToPreserve());
//
//		// Create a new collage filtered by percentile
//		HashMap<Geometry, Double> newCollage = new HashMap<>(
//				Utilities.filterMapByPercentile(cognitiveMap.collage, MEMORY_PERCENTILE));
//
//		// Add the preserved entries to the new collage
//		cognitiveSpacesToPreserve.forEach(geometry -> newCollage.put(geometry, cognitiveMap.collage.get(geometry)));
//
//		// Update the cognitive map collage
//		cognitiveMap.collage = newCollage;
//		System.out.println("AgentID " + agent.agentID + " size collage " + cognitiveMap.collage.size());
//
//	}
//
////	private void adjustMemoryPercentile() {
////		// Example dynamic adjustment based on activity
////		if (agent.getActivityLevel() > HIGH_ACTIVITY_THRESHOLD) {
////			MEMORY_PERCENTILE += 0.1;
////		} else if (agent.getActivityLevel() < LOW_ACTIVITY_THRESHOLD) {
////			MEMORY_PERCENTILE -= 0.1;
////		}
////		MEMORY_PERCENTILE = Math.min(0.9, Math.max(0.1, MEMORY_PERCENTILE));
////	}
//
//	public List<Geometry> getSpacesToPreserve() {
//
//		// Calculate the 80th percentile threshold
//		int threshold = calculatePercentileThreshold(visitedLocations.values(), PERCENTILE_THRESHOLD);
//		Set<NodeGraph> mostVisitedLocation = new HashSet<>();
//
//		// Collect most visited locations
//		mostVisitedLocation = visitedLocations.entrySet().stream().filter(entry -> entry.getValue() >= threshold)
//				.map(Map.Entry::getKey).collect(Collectors.toSet());
//
//		final Set<NodeGraph> mostVisitedLocationF = new HashSet<>(mostVisitedLocation);
//
//		// Collect polygons to preserve because they contain the most visited locations
//		return cognitiveMap.collage.keySet().stream()
//				.filter(polygon -> mostVisitedLocationF.stream()
//						.anyMatch(visitedNode -> polygon.contains(visitedNode.getMasonGeometry().geometry)))
//				.collect(Collectors.toList());
//
//	}
//
//	public void individualLearning() {
//		triggerChange();
//	}
//
//	private void triggerChange() {
//		routeProperties.computeVisibilitySpace();
//		Polygon visibilitySpace = routeProperties.getVisibilitySpace();
//		cognitiveMap.collage.put(visibilitySpace, routeProperties.meaningfulness);
//		cognitiveMap.readjustCognitiveMap();
//	}
//
//	private int calculatePercentileThreshold(Collection<Integer> values, double percentile) {
//		return values.stream().sorted().skip((long) (values.size() * percentile) - 1).findFirst().orElse(0);
//	}
}
