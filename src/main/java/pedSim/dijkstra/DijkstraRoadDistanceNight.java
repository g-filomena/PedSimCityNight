
package pedSim.dijkstra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.jts.planargraph.DirectedEdge;

import pedSim.agents.Agent;
import pedSim.cognitiveMap.CommunityCognitiveMap;
import pedSim.utilities.StringEnum.Vulnerable;
import sim.graph.EdgeGraph;
import sim.graph.NodeGraph;
import sim.routing.NodeWrapper;

/**
 * The class allows computing the road distance shortest route by employing the Dijkstra shortest-path algorithm on a
 * primal graph representation of the street network.
 *
 * It furthermore supports combined navigation strategies based on landmark and urban subdivisions (regions, barriers).
 **/
public class DijkstraRoadDistanceNight extends Dijkstra {

	public Set<NodeGraph> disregardedNodes = new HashSet<>();

	/**
	 * Performs the Dijkstra's algorithm to find the shortest path from the origin node to the destination node.
	 *
	 * This method calculates the shortest path in the network graph from the specified origin node to the destination
	 * node while considering optional segments to avoid and agent properties.
	 *
	 * @param originNode The starting node for the path.
	 * @param agent      The agent for which the route is computed.
	 * 
	 * @return An ArrayList of DirectedEdges representing the shortest path from the origin to the destination.
	 */
	public List<DirectedEdge> dijkstraAlgorithm(NodeGraph originNode, NodeGraph destinationNode, Agent agent) {

		initialise(originNode, destinationNode, agent);
		visitedNodes = new HashSet<>();
		unvisitedNodes = new PriorityQueue<>(Comparator.comparingDouble(this::getBest));
		unvisitedNodes.add(this.originNode);

		// NodeWrapper = container for the metainformation about a Node
		NodeWrapper nodeWrapper = new NodeWrapper(originNode);
		nodeWrapper.gx = 0.0;
		nodeWrappersMap.put(originNode, nodeWrapper);
		runDijkstra();

		return reconstructSequence();
	}

	/**
	 * Runs the Dijkstra algorithm to find the shortest path.
	 */
	private void runDijkstra() {
		while (!unvisitedNodes.isEmpty()) {
			NodeGraph currentNode = unvisitedNodes.poll();
			findMinDistances(currentNode);
		}
	}

	/**
	 * Finds the minimum distances for adjacent nodes of the given current node in the primal graph.
	 *
	 * @param currentNode The current node in the primal graph for which to find adjacent nodes.
	 */
	private void findMinDistances(NodeGraph currentNode) {

		List<NodeGraph> adjacentNodes = currentNode.getAdjacentNodes();
		List<NodeGraph> validNeighbors;

		if (!secondAttempt) {
			validNeighbors = adjacentNodes.stream().filter(targetNode -> {
				EdgeGraph edge = agentNetwork.getEdgeBetween(currentNode, targetNode);
				return (agent.vulnerable == Vulnerable.NON_VULNERABLE || !shouldAvoidEdgeAtNight(edge, secondAttempt))
						&& !disregardedNodes.contains(targetNode); // Exclude disregarded nodes
			}).collect(Collectors.toList());

			if (validNeighbors.isEmpty()) {
				// Flag currentNode as "dead-end"
				disregardedNodes.add(currentNode);
				return;
			}
		} else
			validNeighbors = adjacentNodes;

		for (NodeGraph targetNode : validNeighbors) {
			EdgeGraph commonEdge = agentNetwork.getEdgeBetween(currentNode, targetNode);
			DirectedEdge outEdge = agentNetwork.getDirectedEdgeBetween(currentNode, targetNode);
			tentativeCost = 0.0;
			double error = costPerceptionError(targetNode, commonEdge);
			double edgeCost = commonEdge.getLength() * error;
			computeTentativeCost(currentNode, targetNode, edgeCost);
			isBest(currentNode, targetNode, outEdge);
		}
	}

	/**
	 * Determines whether a pedestrian agent should avoid a specific edge during the night, based on the edge's
	 * characteristics and the agent's cognitive map.
	 * 
	 * This method checks if the edge belongs to a region that is either unknown to the agent (i.e., not in its
	 * cognitive map or the community's cognitive map) or is located within a park or near water. If either condition is
	 * true, the method returns true, indicating that the agent should avoid this edge at night. If the edge leads
	 * directly to the agent's destination or if the region is known (to the agent or the community), the agent may
	 * proceed, and the method returns false.
	 * 
	 * The second attempt parameter relaxes the avoidance criteria. If the first attempt fails to find a suitable edge,
	 * setting `secondAttempt` to true allows the agent to consider edges that it would otherwise avoid, such as edges
	 * in unknown regions or near parks/water.
	 *
	 * @param edge          The edge to evaluate for avoidance.
	 * @param secondAttempt If true, the method applies a more lenient check on the edge characteristics (e.g., it may
	 *                      allow edges in unknown regions or near parks/water). *
	 * @return true if the agent should avoid the edge at night; false otherwise.
	 */
	protected boolean shouldAvoidEdgeAtNight(EdgeGraph edge, boolean secondAttempt) {

		// Avoid if the edge leads to the destination
		if (edge.getNodes().contains(destinationNode))
			return false;

		Integer regionID = edge.getRegionID();
		boolean isRegionKnown = agent.getCognitiveMap().isRegionKnown(regionID)
				|| CommunityCognitiveMap.isRegionKnownByCommunity(regionID);

		// If the edge is in a park/water or if the region is unknown and it's not the second attempt, avoid it
		if (CommunityCognitiveMap.getEdgesWithinParksOrAlongWater().contains(edge)
				|| (!isRegionKnown && !secondAttempt))
			return true;

		return false;
	}

	/**
	 * Reconstructs the sequence of directed edges composing the path.
	 *
	 * @return An ArrayList of DirectedEdges representing the path sequence.
	 */
	private List<DirectedEdge> reconstructSequence() {
		List<DirectedEdge> directedEdgesSequence = new ArrayList<>();
		NodeGraph step = destinationNode;

		// Check that the route has been formulated properly
		// No route
		if (nodeWrappersMap.get(destinationNode) == null || nodeWrappersMap.size() <= 1)
			directedEdgesSequence.clear();
		else
			while (nodeWrappersMap.get(step).nodeFrom != null) {
				DirectedEdge directedEdge;
				directedEdge = nodeWrappersMap.get(step).directedEdgeFrom;
				step = nodeWrappersMap.get(step).nodeFrom;
				directedEdgesSequence.add(0, directedEdge);
			}

		// If the sequence is empty, attempt the second approach (dijkstraAlgorithm)
		if (directedEdgesSequence.isEmpty()) {
			secondAttempt = true;
			directedEdgesSequence = dijkstraAlgorithm(originNode, destinationNode, agent);
			// If the second attempt also fails, use the fallback method the class with no avoidance;
			if (directedEdgesSequence.isEmpty())
				directedEdgesSequence = new DijkstraRoadDistance().dijkstraAlgorithm(originNode, destinationNode,
						this.agent);
		}
		return directedEdgesSequence;
	}
}
