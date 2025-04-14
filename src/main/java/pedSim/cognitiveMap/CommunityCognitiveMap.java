package pedSim.cognitiveMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.javatuples.Pair;

import pedSim.engine.PedSimCity;
import pedSim.parameters.Pars;
import pedSim.parameters.RouteChoicePars;
import pedSim.utilities.StringEnum.RoadType;
import sim.field.geo.VectorLayer;
import sim.graph.Building;
import sim.graph.EdgeGraph;
import sim.graph.Graph;
import sim.graph.GraphUtils;
import sim.graph.NodeGraph;
import sim.routing.Route;
import sim.util.geo.MasonGeometry;

/**
 * This class represent a community share cognitive map (or Image of the City)
 * used for storing meaningful information about the environment and, in turn,
 * navigating.
 */
public class CommunityCognitiveMap {

//	/**
//	 * Stores global landmarks as a VectorLayer.
//	 */
//	private static VectorLayer globalLandmarks = new VectorLayer();

	/**
	 * Maps pairs of nodes to gateways.
	 */
	public Map<Pair<NodeGraph, NodeGraph>, Gateway> gatewaysMap = new HashMap<>();

	/**
	 * Stores barriers as a VectorLayer.
	 */
	protected static VectorLayer barriers;
	static Graph communityNetwork;
	static Graph communityDualNetwork;

	private static HashMap<EdgeGraph, RoadType> roadTypeMap = new HashMap<>();

	public static Set<EdgeGraph> communityKnownEdges = new HashSet<>();
	protected static Set<NodeGraph> communityKnownNodes = new HashSet<>();
	private static List<EdgeGraph> cityCenterEdges;
	public static Set<EdgeGraph> secondaryEdges = new HashSet<>();
	protected static Set<EdgeGraph> tertiaryEdges = new HashSet<>();
	protected static Set<EdgeGraph> neighbourhoodEdges = new HashSet<>();
	protected static Set<EdgeGraph> unknownEdges = new HashSet<>();
	public static Set<EdgeGraph> edgesWithinParks = new HashSet<>();
	public static Set<EdgeGraph> edgesAlongWater = new HashSet<>();
	public static Set<EdgeGraph> litEdges = new HashSet<>();
	public static Set<EdgeGraph> nonLitNonPrimary = new HashSet<>();

	public static List<NodeGraph> unknownNodes = new ArrayList<>();
	public static Map<Pair<NodeGraph, NodeGraph>, Route> routesSubNetwork = new ConcurrentHashMap<>();
	public static Map<Pair<NodeGraph, NodeGraph>, Route> forcedRoutesSubNetwork = new ConcurrentHashMap<>();
	public static Map<Pair<NodeGraph, NodeGraph>, Double> cachedHeuristics = new ConcurrentHashMap<>();

	public static List<NodeGraph> destinationCandidates;

	/**
	 * Singleton instance of the CognitiveMap.
	 */
	private static final CommunityCognitiveMap instance = new CommunityCognitiveMap();

	Building buildingsHandler = new Building();

	/**
	 * Sets up the community cognitive map.
	 */
	public static void setCommunityCognitiveMap() {

		setCommunityNetwork(PedSimCity.network, PedSimCity.dualNetwork);
		setBuildingsAtJunctions();
		barriers = PedSimCity.barriers;
		setOtherNetworks();
	}

	/**
	 * Gets the singleton instance of CognitiveMap.
	 *
	 * @return The CognitiveMap instance.
	 */
	public static CommunityCognitiveMap getInstance() {
		return instance;
	}

	private static void setCommunityNetwork(Graph network, Graph dualNetwork) {

		communityNetwork = network;
		communityDualNetwork = dualNetwork;

		communityNetwork.getEdges().forEach(edge -> Pars.roadTypes.entrySet().stream()
				.filter(entry -> Arrays.asList(entry.getValue()).contains(edge.attributes.get("roadType").getString()))
				.findFirst().ifPresent(entry -> roadTypeMap.put(edge, entry.getKey())));

		communityKnownEdges = new HashSet<>(
				roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.PRIMARY)
						.map(Map.Entry::getKey).collect(Collectors.toSet()));

		cityCenterEdges = new ArrayList<>();

		for (int regionID : RouteChoicePars.cityCentreRegionsID)
			cityCenterEdges.addAll(PedSimCity.regionsMap.get(regionID).edges);

		communityKnownEdges.addAll(cityCenterEdges);

		communityKnownNodes = new HashSet<>(
				communityNetwork.getSalientNodes(RouteChoicePars.salientNodesPercentile).keySet());
		communityKnownEdges.addAll(GraphUtils.edgesFromNodes(communityKnownNodes));

//		Islands islands = new Islands(communityNetwork);
//		communityKnownEdges = islands.mergeConnectedIslands(communityKnownEdges);

		for (NodeGraph node : communityNetwork.getNodes()) {
			if (proportionUnknownEdges(node) == 1.0)
				unknownNodes.add(node);
		}
	}

	private static void setOtherNetworks() {

		secondaryEdges = roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.SECONDARY)
				.map(Map.Entry::getKey).collect(Collectors.toSet());

		tertiaryEdges = roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.TERTIARY)
				.map(Map.Entry::getKey).collect(Collectors.toSet());

		neighbourhoodEdges = roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.NEIGHBOURHOOD)
				.map(Map.Entry::getKey).collect(Collectors.toSet());

		unknownEdges = roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.UNKNOWN)
				.map(Map.Entry::getKey).collect(Collectors.toSet());

		// Filter lit edges
		litEdges = communityNetwork.getEdges().stream().filter(edge -> edge.attributes.get("lit").getBoolean())
				.collect(Collectors.toSet());

		nonLitNonPrimary = communityNetwork.getEdges().stream()
				.filter(edge -> !litEdges.contains(edge) && !communityKnownEdges.contains(edge))
				.collect(Collectors.toSet());

//		destinationCandidates = NodesLookup.getCandidatesByDMA(communityNetwork.getNodes(), "workOrVisit");
	}

	private static double proportionUnknownEdges(NodeGraph node) {

		List<EdgeGraph> edges = node.getEdges();
		long unknownEdgesCount = edges.stream().filter(CommunityCognitiveMap.unknownEdges::contains).count();
		long totalEdgesCount = edges.size();
		return (double) unknownEdgesCount / totalEdgesCount;
	}

	public static Graph getNetwork() {
		return communityNetwork;
	}

	public static Graph getDualNetwork() {
		return communityDualNetwork;
	}

	public static VectorLayer getBarriers() {
		return barriers;
	}

	public static Route getRoutes(Pair<NodeGraph, NodeGraph> nodePair) {
		NodeGraph node = nodePair.getValue0();
		NodeGraph otherNode = nodePair.getValue1();
		Pair<NodeGraph, NodeGraph> reversePair = new Pair<>(otherNode, node);
		return routesSubNetwork.getOrDefault(nodePair, routesSubNetwork.get(reversePair));
	}

	public static Route getForcedRoutes(Pair<NodeGraph, NodeGraph> nodePair) {
		NodeGraph node = nodePair.getValue0();
		NodeGraph otherNode = nodePair.getValue1();
		Pair<NodeGraph, NodeGraph> reversePair = new Pair<>(otherNode, node);
		return forcedRoutesSubNetwork.getOrDefault(nodePair, forcedRoutesSubNetwork.get(reversePair));
	}

	/**
	 * It assigns to each node in the graph a list of surrounding buildings.
	 *
	 */
	private static void setBuildingsAtJunctions() {

		List<NodeGraph> nodes = communityNetwork.getNodes();
		nodes.forEach((node) -> {
			List<MasonGeometry> buildingsAround = PedSimCity.buildings
					.featuresWithinDistance(node.getMasonGeometry().geometry, RouteChoicePars.distanceNodeBuilding);
			for (MasonGeometry masonGeometry : buildingsAround)
				node.adjacentBuildings.add(PedSimCity.buildingsMap.get((int) masonGeometry.getUserData()));
		});
	}

}
