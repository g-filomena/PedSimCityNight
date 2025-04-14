package pedSim.cognitiveMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pedSim.agents.Agent;
import pedSim.engine.PedSimCity;
import sim.graph.EdgeGraph;
import sim.graph.GraphUtils;
import sim.graph.NodeGraph;
import sim.graph.NodesLookup;

/**
 * Represents an agent's cognitive map, which provides access to various map
 * attributes. In this version of PedSimCity, this is a simple structure
 * designed for further developments.
 */
public class CognitiveMap extends CommunityCognitiveMap {

	// in the community network
	private NodeGraph homeNode;
	private final NodeGraph workNode;

	protected Set<Integer> knownEdges = new HashSet<>();
	private Set<Integer> knownRegions = new HashSet<>();

	protected Agent agent;
	public boolean formed = false;

	/**
	 * Constructs an AgentCognitiveMap.
	 */
	public CognitiveMap(Agent agent) {
		this.agent = agent;

		while (homeNode == null) {
			homeNode = NodesLookup.randomNodeDMA(CommunityCognitiveMap.getNetwork(), "live");

			if (homeNode.getEdges().stream().anyMatch(CommunityCognitiveMap.edgesWithinParks::contains)) {
				homeNode = null;
				homeNode = NodesLookup.randomNode(CommunityCognitiveMap.getNetwork());
			}
		}

		workNode = NodesLookup.randomNodeBetweenDistanceIntervalDMA(CommunityCognitiveMap.getNetwork(), homeNode, 700,
				2000, "work");
	}

	// TODO, add edges connecting known regions..
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

	public void formCognitiveMap() {

		buildActivityBone();
		formed = true;
	}

	public NodeGraph getHomeNode() {
		return homeNode;
	}

	public NodeGraph getWorkNode() {
		return workNode;
	}

	public Set<Integer> getKnownEdges() {
		return new HashSet<>(knownEdges);
	}

	public Set<Integer> getKnownRegions() {
		return knownRegions;
	}

	public boolean isRegionKnown(Integer regionID) {
		return knownRegions.contains(regionID);
	}

	public boolean isEdgeKnown(EdgeGraph edgeGraph) {
		return knownEdges.contains(edgeGraph.getID());
	}
}