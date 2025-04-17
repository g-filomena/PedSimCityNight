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
 * The Dijkstra class provides functionality for performing Dijkstra's algorithm and related calculations for route planning in the pedestrian
 * simulation.
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

	Agent agent;
	Route route = new Route();

	protected static final double MAX_DEFLECTION_ANGLE = 180.00;
	protected static final double MIN_DEFLECTION_ANGLE = 0;

	/**
	 * Initialises the Dijkstra algorithm, setting up the origin and destination nodes and preparing the network for traversal.
	 * 
	 * This method clears any previous state, assigns the relevant nodes (origin and destination), and retrieves the agent's properties and the network
	 * from the cognitive map.
	 *
	 * @param originNode      The starting node from which the journey begins.
	 * @param destinationNode The target node to which the agent is heading.
	 * @param agent           The agent for which the pathfinding is being set up.
	 */
	protected void initialise(NodeGraph originNode, NodeGraph destinationNode, Agent agent) {

		nodeWrappersMap.clear();
		this.agentNetwork = CommunityCognitiveMap.getNetwork();
		this.agent = agent;
		this.properties = agent.getProperties();
		this.originNode = originNode;
		this.destinationNode = destinationNode;
	}

	/**
	 * Computes the cost perception error for traversing an edge based on the presence of natural elements and time of day.
	 * 
	 * The method returns a value representing how much the agent perceives the cost of traversing a specific edge. It takes into account the time of day
	 * (i.e., whether it's night) and the presence of positive barriers (such as natural barriers). The error is adjusted based on a distribution with
	 * different parameters depending on the circumstances.
	 *
	 * @param targetNode The target node for cost calculation (not used in the current implementation).
	 * @param commonEdge The edge used in the cost calculation.
	 * @return The computed cost perception error, which may vary depending on barriers and time of day.
	 */
	protected double costPerceptionError(NodeGraph targetNode, EdgeGraph commonEdge) {

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
	 * Computes the tentative cost for a given currentNode and targetNode with the specified edgeCost.
	 *
	 * @param currentNode The current node.
	 * @param targetNode  The target node.
	 * @param edgeCost    The cost of the edge between the current and target nodes.
	 */
	protected void computeTentativeCost(NodeGraph currentNode, NodeGraph targetNode, double edgeCost) {
		tentativeCost = getBest(currentNode) + edgeCost;
	}

	/**
	 * Checks if the tentative cost is the best for the currentNode and targetNode with the specified outEdge.
	 *
	 * @param currentNode The current node.
	 * @param targetNode  The target node.
	 * @param outEdge     The directed edge from the current node to the target node.
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
	 * Retrieves the best value for the specified targetNode from the nodeWrappersMap.
	 *
	 * @param targetNode The target node.
	 * @return The best value for the target node.
	 */
	protected double getBest(NodeGraph targetNode) {
		NodeWrapper nodeWrapper = nodeWrappersMap.get(targetNode);
		return nodeWrapper != null ? nodeWrapper.gx : Double.MAX_VALUE;
	}

	/**
	 * Determines whether a pedestrian agent should avoid a specific edge during the night, based on the edge's characteristics and the agent's cognitive
	 * map.
	 * 
	 * This method checks if the edge is part of a region that is either unknown to the agent (not in its or the community's cognitive map) or located
	 * within a park or near water,in which case it returns true indicating the agent should avoid it at night. If the edge leads to the agent's
	 * destination or if the region is known, the agent may proceed, and the method returns false.
	 *
	 * @param edge the edge to evaluate
	 * @return true if the agent should avoid the edge at night; false otherwise
	 */
	protected boolean shouldAvoidEdgeAtNight(EdgeGraph edge) {

		if (edge.getNodes().contains(destinationNode))
			return false;

		Integer regionID = edge.getRegionID();
		boolean isRegionKnown = agent.getCognitiveMap().isRegionKnown(regionID);
		if (CommunityCognitiveMap.getEdgesWithinParksOrAlongWater().contains(edge) || !isRegionKnown)
			return true;
		return false;
	}

}
