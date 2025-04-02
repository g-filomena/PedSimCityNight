//package pedSim.cognitiveMap;
//
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//import org.locationtech.jts.geom.Geometry;
//import org.locationtech.jts.geom.MultiPolygon;
//import org.locationtech.jts.geom.Polygon;
//import org.locationtech.jts.geom.util.GeometryCombiner;
//
//import pedSim.engine.PedSimCity;
//import pedSim.parameters.RouteChoicePars;
//import sim.graph.EdgeGraph;
//import sim.graph.GraphUtils;
//import sim.graph.NodeGraph;
//import sim.routing.Route;
//import sim.util.geo.MasonGeometry;
//
///**
// * Novelty Score: Compare the current path with previous paths taken by the
// * agent. A simple approach could involve measuring the number of unique nodes
// * or regions visited in the path. Exposure Score: Evaluate the proximity or
// * coverage of important locations within a certain distance from the path. This
// * could be done using distance-based metrics or spatial overlays with a layer
// * representing important locations. Spatial Complexity Score: Calculate a
// * measure of spatial complexity based on the number of turns, changes in
// * direction, or variety of street types encountered in the path.
// */
//public class RouteProperties {
//
//	private Route route;
//	private Geometry routeBuffer;
//	Polygon visibilitySpace;
//
//	protected Set<NodeGraph> visitedLocations = new HashSet<>();
//	List<MasonGeometry> buildingsAlong;
//	List<MasonGeometry> localLandmarksAlong;
//	List<MasonGeometry> globalLandmarksAlong;
//
//	protected int turns;
//	protected int intersections;
//	protected double ratioGlobalLandmark;
//	protected double ratioLocalLandmark;
//	protected double cumulativeGlobalLandmarkness;
//	protected double cumulativeLocalLandmarkness;
//
//	protected double novelty;
//	protected double exposure;
//	protected double complexity;
//	protected double meaningfulness;
//
//	public RouteProperties(Route route) {
//
//		this.route = route;
//		this.route.attributes.put("properties", this);
//	}
//
//	public void computeRouteProperties() {
//		countTurnsIntersections();
//		cumulativeLandmarkness();
//
//		routeBuffer = route.getLineString().buffer(20);
//		buildingsAlong = new ArrayList<>(PedSimCity.buildings.containedFeatures(routeBuffer));
//
//		if (buildingsAlong.isEmpty()) {
//			ratioGlobalLandmark = 0.0;
//			ratioLocalLandmark = 0.0;
//			return;
//		}
//
////		globalLandmarksAlong = buildingsAlong.stream()
////				.filter(building -> CommunityCognitiveMap.getGlobalLandmarks().getGeometries().contains(building))
////				.toList();
////		localLandmarksAlong = buildingsAlong.stream()
////				.filter(building -> CommunityCognitiveMap.getLocalLandmarks().getGeometries().contains(building))
////				.toList();
////
////		ratioLocalLandmark = localLandmarksAlong.size() / (double) buildingsAlong.size();
//		findVisitedLocations();
//	}
//
//	private void findVisitedLocations() {
//		// If route.visitedLocations is not empty, map and collect them
//		if (!route.getVisitedLocations().isEmpty())
//			visitedLocations = route.getVisitedLocations().stream().map(CommunityCognitiveMap.getNetwork()::findNode)
//					.collect(Collectors.toSet());
//		else {
//			// If visitedLocations is empty, filter nodesSequence for salient nodes
//			Set<NodeGraph> salientNodes = CommunityCognitiveMap.getNetwork()
//					.getSalientNodes(RouteChoicePars.salientNodesPercentile).keySet();
//			visitedLocations = route.nodesSequence.stream().filter(salientNodes::contains).collect(Collectors.toSet());
//		}
//
//		visitedLocations.add(route.originNode);
//		visitedLocations.add(route.destinationNode);
//	}
//
//	private void countTurnsIntersections() {
//		intersections = route.nodesSequence.size() - 2;
//
//		for (int i = 0; i < route.dualNodesSequence.size() - 1; i++) {
//			NodeGraph dualNode = route.dualNodesSequence.get(i);
//			NodeGraph nextDualNode = route.dualNodesSequence.get(i + 1);
//			EdgeGraph commonEdge = CommunityCognitiveMap.getDualNetwork().getEdgeBetween(dualNode, nextDualNode);
//			if (commonEdge.getDeflectionAngle() > RouteChoicePars.thresholdTurn)
//				turns++;
//		}
//	}
//
//	private void cumulativeLandmarkness() {
//
//		cumulativeGlobalLandmarkness = route.nodesSequence.parallelStream()
//				.filter(node -> !node.visibleBuildings3d.isEmpty())
//				.mapToDouble(node -> node.visibleBuildings3d.parallelStream()
//						.mapToDouble(landmark -> landmark.attributes.get("globalLandmarkness").getDouble()).max()
//						.orElse(0.0))
//				.sum();
//
//		cumulativeLocalLandmarkness = route.nodesSequence.parallelStream()
//				.filter(node -> !node.visibleBuildings3d.isEmpty())
//				.mapToDouble(node -> node.adjacentBuildings.parallelStream()
//						.mapToDouble(landmark -> landmark.attributes.get("localLandmarkness").getDouble())
//						.filter(value -> value > RouteChoicePars.localLandmarkThreshold).max().orElse(0.0))
//				.sum();
//	}
//
//	void computeVisibilitySpace() {
//		List<Geometry> visibleSpaces = route.nodesSequence.stream().flatMap(node -> {
//			int idx = route.nodesSequence.indexOf(node);
//			NodeGraph nextNode = idx < route.nodesSequence.size() - 1 ? route.nodesSequence.get(idx + 1) : null;
//			Polygon visibilityPolygon = nextNode != null
//					? GraphUtils.createVisibilityPolygon(node, nextNode, 100.0, PedSimCity.buildings, 300.0)
//					: null;
//			List<Geometry> nodeSpaces = new ArrayList<>();
//			if (visibilityPolygon != null)
//				nodeSpaces.add(visibilityPolygon);
//			nodeSpaces.add(node.getMasonGeometry().geometry.buffer(50));
//			return nodeSpaces.stream();
//		}).collect(Collectors.toList());
//
//		visibleSpaces.add(routeBuffer);
//		Geometry combinedGeometry = GeometryCombiner.combine(visibleSpaces);
//
//		if (combinedGeometry instanceof MultiPolygon multiPolygon && multiPolygon.getNumGeometries() == 1)
//			visibilitySpace = (Polygon) multiPolygon.getGeometryN(0);
//		else if (combinedGeometry instanceof Polygon)
//			visibilitySpace = (Polygon) combinedGeometry;
//		else
//			visibilitySpace = (Polygon) combinedGeometry.buffer(0);
//	}
//
//	public static RouteProperties getProperties(Route route) {
//
//		return (RouteProperties) route.attributes.get("properties");
//	}
//
//	public Polygon getVisibilitySpace() {
//		return visibilitySpace;
//	}
//
//}
