package pedSim.cognitiveMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.javatuples.Pair;

import pedSim.engine.PedSimCity;
import pedSim.parameters.Pars;
import pedSim.parameters.RouteChoicePars;
import pedSim.utilities.StringEnum.RoadType;
import sim.field.geo.VectorLayer;
import sim.graph.EdgeGraph;
import sim.graph.Graph;
import sim.graph.NodeGraph;
import sim.util.geo.MasonGeometry;

/**
 * This class represent a community share cognitive map (or Image of the City) used for storing meaningful information
 * about the environment and, in turn, navigating.
 */
public class CommunityCognitiveMap {

	/**
	 * Maps pairs of nodes to gateways.
	 */
	public Map<Pair<NodeGraph, NodeGraph>, Gateway> gatewaysMap = new HashMap<>();
	private static List<NodeGraph> potentialDestinationNodes;

	/**
	 * Stores barriers as a VectorLayer.
	 */
	protected static VectorLayer barriers;
	static Graph communityNetwork;

	private static HashMap<EdgeGraph, RoadType> roadTypeMap = new HashMap<>();

	// known street segments
	protected static Set<EdgeGraph> communityKnownEdges = new HashSet<>();
	protected static Set<EdgeGraph> cityCenterEdges;
	protected static Set<Integer> communityKnownRegions = new HashSet<>();

	// road classification
	protected static Set<EdgeGraph> primaryEdges = new HashSet<>();
	protected static Set<EdgeGraph> secondaryEdges = new HashSet<>();
	protected static Set<EdgeGraph> tertiaryEdges = new HashSet<>();
	protected static Set<EdgeGraph> neighbourhoodEdges = new HashSet<>();
	protected static Set<EdgeGraph> unknownEdges = new HashSet<>();

	// night relevant sets
	protected static Set<EdgeGraph> edgesWithinParks = new HashSet<>();
	protected static Set<EdgeGraph> edgesAlongWater = new HashSet<>();
	protected static Set<EdgeGraph> litEdges = new HashSet<>();
	protected static Set<EdgeGraph> nonLitNonKnown = new HashSet<>();

	/**
	 * Singleton instance of the CognitiveMap.
	 */
	private static final CommunityCognitiveMap instance = new CommunityCognitiveMap();

	/**
	 * Sets up the community cognitive map.
	 */
	public static void setCommunityCognitiveMap() {

		setCommunityNetwork(PedSimCity.network, PedSimCity.dualNetwork);
		setBuildingsAtJunctions();
		barriers = PedSimCity.barriers;
		setCommunityKnownEdges();
	}

	/**
	 * Gets the singleton instance of the CognitiveMap.
	 *
	 * @return The CognitiveMap instance.
	 */
	public static CommunityCognitiveMap getInstance() {
		return instance;
	}

	/**
	 * Sets the community network, which includes the road type classification and road classification.
	 *
	 * @param network     The primary network.
	 * @param dualNetwork The dual network.
	 */
	private static void setCommunityNetwork(Graph network, Graph dualNetwork) {

		communityNetwork = network;
		// Iterates over each edge in the community network and maps its road type to a corresponding entry from
		// Pars.roadTypes.
		getCommunityNetwork().getEdges().forEach(edge -> Pars.roadTypes.entrySet().stream()
				.filter(entry -> Arrays.asList(entry.getValue()).contains(edge.attributes.get("roadType").getString()))
				.findFirst().ifPresent(entry -> roadTypeMap.put(edge, entry.getKey())));
		buildRoadClassification();
	}

	/**
	 * Builds the road classification by categorising edges into primary, secondary, tertiary, neighbourhood, or unknown
	 * road types.
	 */
	private static void buildRoadClassification() {

		primaryEdges = roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.PRIMARY)
				.map(Map.Entry::getKey).collect(Collectors.toSet());

		secondaryEdges = roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.SECONDARY)
				.map(Map.Entry::getKey).collect(Collectors.toSet());

		tertiaryEdges = roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.TERTIARY)
				.map(Map.Entry::getKey).collect(Collectors.toSet());

		neighbourhoodEdges = roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.NEIGHBOURHOOD)
				.map(Map.Entry::getKey).collect(Collectors.toSet());

		unknownEdges = roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.UNKNOWN)
				.map(Map.Entry::getKey).collect(Collectors.toSet());

		setLitNonLitEdges();
	}

	/**
	 * Sets the community-known edges based on road classification and city centre edges.
	 */
	private static void setCommunityKnownEdges() {

		communityKnownEdges = new HashSet<>(primaryEdges);
		communityKnownEdges.addAll(secondaryEdges);
		communityKnownEdges.addAll(tertiaryEdges);

		cityCenterEdges = new HashSet<>();
		for (int regionID : RouteChoicePars.cityCentreRegionsID) {
			communityKnownRegions.add(regionID);
			cityCenterEdges.addAll(PedSimCity.regionsMap.get(regionID).edges);
		}

		communityKnownEdges.addAll(cityCenterEdges);
	}

	/**
	 * Sets the lit and non-lit edges based on their attributes.
	 */
	private static void setLitNonLitEdges() {
		// Filter lit edges
		litEdges = getCommunityNetwork().getEdges().stream().filter(edge -> edge.attributes.get("lit").getBoolean())
				.collect(Collectors.toSet());

		nonLitNonKnown = getCommunityNetwork().getEdges().stream()
				.filter(edge -> !getLitEdges().contains(edge) && !getCommunityKnownEdges().contains(edge))
				.collect(Collectors.toSet());
	}

	/**
	 * Sets the buildings at junctions by associating each junction with nearby buildings within a given distance.
	 */
	private static void setBuildingsAtJunctions() {

		List<NodeGraph> nodes = getCommunityNetwork().getNodes();
		nodes.forEach((node) -> {
			List<MasonGeometry> buildingsAround = PedSimCity.buildings
					.featuresWithinDistance(node.getMasonGeometry().geometry, RouteChoicePars.distanceNodeBuilding);
			for (MasonGeometry masonGeometry : buildingsAround)
				node.adjacentBuildings.add(PedSimCity.buildingsMap.get((int) masonGeometry.getUserData()));
		});
	}

	/**
	 * Checks if a region is known by the community.
	 *
	 * @param regionID The ID of the region.
	 * @return True if the region is known, otherwise false.
	 */
	public static boolean isRegionKnownByCommunity(int regionID) {
		return CommunityCognitiveMap.communityKnownRegions.contains(regionID);
	}

	/**
	 * Gets the community network.
	 *
	 * @return The community network.
	 */
	public static Graph getCommunityNetwork() {
		return communityNetwork;
	}

	/**
	 * Gets the set of regions known by the community.
	 *
	 * @return The set of known regions.
	 */
	public Set<Integer> getRegionsKnownByCommunity() {
		return communityKnownRegions;
	}

	/**
	 * Gets the set of community-known edges.
	 *
	 * @return The set of known edges in the community.
	 */
	public static Set<EdgeGraph> getCommunityKnownEdges() {
		return communityKnownEdges;
	}

	/**
	 * Gets the set of edges within parks or along water.
	 *
	 * @return The set of edges located in parks or along water.
	 */
	public static Set<EdgeGraph> getEdgesWithinParksOrAlongWater() {
		Set<EdgeGraph> edges = new HashSet<>(edgesWithinParks);
		edges.addAll(edgesAlongWater);
		return edges;
	}

	/**
	 * Gets the set of edges within parks.
	 *
	 * @return The set of edges located in parks.
	 */
	public static Set<EdgeGraph> getEdgesWithinParks() {
		return edgesWithinParks;
	}

	/**
	 * Gets the set of edges along water.
	 *
	 * @return The set of edges located along water.
	 */
	public static Set<EdgeGraph> getEdgesAlongWater() {
		return edgesAlongWater;
	}

	/**
	 * Gets the set of non-lit, non-community-known edges.
	 *
	 * @return A set of non-lit, non-community-known edges.
	 */
	public static Set<EdgeGraph> getEdgesNonLitNonCommunityKnown() {
		return nonLitNonKnown;
	}

	/**
	 * Gets the set of lit edges.
	 *
	 * @return A set of lit edges.
	 */
	public static Set<EdgeGraph> getLitEdges() {
		return litEdges;
	}

	/**
	 * Gets the community network.
	 *
	 * @return The community network.
	 */
	public static Graph getNetwork() {
		return getCommunityNetwork();
	}

	/**
	 * Gets the barriers in the community.
	 *
	 * @return The barriers as a VectorLayer.
	 */
	public static VectorLayer getBarriers() {
		return barriers;
	}

	/**
	 * Returns secondary edges.
	 * 
	 * @return the neighbourhoodEdges
	 */
	public static Set<EdgeGraph> getNeighbourhoodEdges() {
		return neighbourhoodEdges;
	}
}
