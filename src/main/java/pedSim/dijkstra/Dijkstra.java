package pedSim.dijkstra;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.locationtech.jts.planargraph.DirectedEdge;

import pedSim.agents.Agent;
import pedSim.agents.AgentProperties;
import pedSim.cognitiveMap.CommunityCognitiveMap;
import pedSim.parameters.RouteChoicePars;
import sim.graph.EdgeGraph;
import sim.graph.Graph;
import sim.graph.NodeGraph;
import sim.routing.NodeWrapper;
import sim.routing.Route;
import sim.util.geo.Utilities;

/**
 * The Dijkstra class provides functionality for performing Dijkstra's algorithm
 * and related calculations for route planning in the pedestrian simulation.
 */
public class Dijkstra {

	NodeGraph originNode, destinationNode;
	protected Set<NodeGraph> visitedNodes;
	protected PriorityQueue<NodeGraph> unvisitedNodes;
	Map<NodeGraph, NodeWrapper> nodeWrappersMap = new HashMap<>();

	AgentProperties properties;
	double tentativeCost;

	protected boolean secondAttempt;
	protected Graph agentNetwork;

//	protected Set<EdgeGraph> knownEdges;
//	protected Set<EdgeGraph> knownDualEdges;
//	protected Set<NodeGraph> knownNodes;
//	protected Set<NodeGraph> knownDualNodes;

	Agent agent;
	Route route = new Route();

	protected static final double MAX_DEFLECTION_ANGLE = 180.00;
	protected static final double MIN_DEFLECTION_ANGLE = 0;

	protected void initialise(NodeGraph originNode, NodeGraph destinationNode, Agent agent) {

		nodeWrappersMap.clear();
		this.agentNetwork = CommunityCognitiveMap.getNetwork();
		this.agent = agent;
		this.properties = agent.getProperties();
		this.originNode = originNode;
		this.destinationNode = destinationNode;
	}

	/**
	 * Computes the cost perception error based on the role of barriers.
	 *
	 * @param targetNode The target node for cost calculation.
	 * @param commonEdge The common edge used in cost calculation.
	 * @param dual       Indicates whether it is a dual graph.
	 * @return The computed cost perception error.
	 */
	protected double costPerceptionError(NodeGraph targetNode, EdgeGraph commonEdge, boolean dual) {

		// avoid parks/rivers at night
		if (agent.getState().isDark)
			return Utilities.fromDistribution(1.0, 0.10, null);
		double error = Utilities.fromDistribution(1.0, 0.10, null);

		List<Integer> pBarriers = commonEdge.attributes.get("positiveBarriers").getArray();
		if (!pBarriers.isEmpty())
			error = Utilities.fromDistribution(RouteChoicePars.naturalBarriers, RouteChoicePars.naturalBarriersSD,
					"left");

		return error;
	}

	/**
	 * Computes the tentative cost for a given currentNode and targetNode with the
	 * specified edgeCost.
	 *
	 * @param currentNode The current node.
	 * @param targetNode  The target node.
	 * @param edgeCost    The cost of the edge between the current and target nodes.
	 */
	protected void computeTentativeCost(NodeGraph currentNode, NodeGraph targetNode, double edgeCost) {

		tentativeCost = getBest(currentNode) + edgeCost;
	}

	/**
	 * Checks if the tentative cost is the best for the currentNode and targetNode
	 * with the specified outEdge.
	 *
	 * @param currentNode The current node.
	 * @param targetNode  The target node.
	 * @param outEdge     The directed edge from the current node to the target
	 *                    node.
	 */
	protected void isBest(NodeGraph currentNode, NodeGraph targetNode, DirectedEdge outEdge) {
		if (getBest(targetNode) > tentativeCost) {
			NodeWrapper nodeWrapper = nodeWrappersMap.computeIfAbsent(targetNode, NodeWrapper::new);
			nodeWrapper.nodeFrom = currentNode;
			nodeWrapper.directedEdgeFrom = outEdge;
			nodeWrapper.gx = tentativeCost;
			unvisitedNodes.add(targetNode);
		}
	}

	/**
	 * Retrieves the best value for the specified targetNode from the
	 * nodeWrappersMap.
	 *
	 * @param targetNode The target node.
	 * @return The best value for the target node.
	 */
	protected double getBest(NodeGraph targetNode) {
		NodeWrapper nodeWrapper = nodeWrappersMap.get(targetNode);
		return nodeWrapper != null ? nodeWrapper.gx : Double.MAX_VALUE;
	}

	protected boolean shouldAvoidEdgeAtNight(EdgeGraph edge) {

		if (edge.getNodes().contains(destinationNode))
			return false;

		Integer regionID = edge.getRegionID();
		boolean isRegionKnown = agent.getCognitiveMap().isRegionKnown(regionID);
		if (CommunityCognitiveMap.edgesAlongWater.contains(edge)
				|| CommunityCognitiveMap.edgesWithinParks.contains(edge) || !isRegionKnown)
			return true;
		return false;
	}

}
