package pedSim.cognitiveMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pedSim.agents.Agent;
import pedSim.engine.PedSimCity;
import pedSim.parameters.RouteChoicePars;
import sim.graph.EdgeGraph;
import sim.graph.GraphUtils;
import sim.graph.NodeGraph;
import sim.graph.NodesLookup;

/**
 * Represents an agent's cognitive map, which provides access to various map attributes. In this version of PedSimCity, this is a simple structure.
 */
public class CognitiveMap extends CommunityCognitiveMap {

	// in the community network
	protected NodeGraph homeNode;
	protected final NodeGraph workNode;

	protected Set<Integer> knownEdges = new HashSet<>();
	protected Set<Integer> knownRegions = new HashSet<>();

	protected Agent agent;
	public boolean formed = false;

	/**
	 * Constructs an AgentCognitiveMap.
	 */
	public CognitiveMap(Agent agent) {
		this.agent = agent;

		while (homeNode == null
				|| homeNode.getEdges().stream().anyMatch(CommunityCognitiveMap.edgesWithinParks::contains))
			homeNode = NodesLookup.randomNodeDMA(CommunityCognitiveMap.getNetwork(), "live");

		workNode = NodesLookup.randomNodeBetweenDistanceIntervalDMA(CommunityCognitiveMap.getNetwork(), homeNode,
				RouteChoicePars.minTripDistance, RouteChoicePars.maxTripDistance, "work");
	}

	/**
	 * Forms the cognitive map by constructing the activity bone, which involves adding the agent's home and work nodes and their related edges.
	 */
	public void formCognitiveMap() {
		buildActivityBone();
		formed = true;
	}

	/**
	 * Builds the activity bone, which includes the agent's home and work nodes along with edges in the known regions and from those nodes.
	 */
	private void buildActivityBone() {
		NodeGraph[] knownNodes = { homeNode, workNode };
		List<EdgeGraph> edges = new ArrayList<>();

		for (NodeGraph node : knownNodes) {
			int region = node.getRegionID();
			knownRegions.add(region);
			edges.addAll(PedSimCity.regionsMap.get(region).edges);
			edges.addAll(node.getEdges());
			knownEdges.addAll(GraphUtils.getEdgeIDs(edges));
		}
	}

	/**
	 * Gets the home node for the agent in the cognitive map.
	 * 
	 * @return The home node for the agent.
	 */
	public NodeGraph getHomeNode() {
		return homeNode;
	}

	/**
	 * Checks whether a specific region is known to the agent or the community.
	 * 
	 * @param regionID The ID of the region to check.
	 * @return True if the region is known; otherwise, false.
	 */
	public boolean isRegionKnown(Integer regionID) {
		return knownRegions.contains(regionID) || CommunityCognitiveMap.isRegionKnownByCommunity(regionID);
	}

	/**
	 * Checks if a given edge is known to the agent.
	 * 
	 * @param edgeGraph The edge to check.
	 * @return True if the edge is known; otherwise, false.
	 */
	public boolean isEdgeKnown(EdgeGraph edgeGraph) {
		return knownEdges.contains(edgeGraph.getID());
	}

	/**
	 * Return the set of edgeIDs of the street segments in the known regions.
	 * 
	 * @return The set of known edges.
	 */
	public Set<Integer> getKnownEdges() {
		return knownEdges;
	}
}