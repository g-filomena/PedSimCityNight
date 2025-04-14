
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
import pedSim.utilities.StringEnum.Gender;
import sim.graph.EdgeGraph;
import sim.graph.NodeGraph;
import sim.routing.NodeWrapper;

/**
 * The class allows computing the road distance shortest route by employing the
 * Dijkstra shortest-path algorithm on a primal graph representation of the
 * street network.
 *
 * It furthermore supports combined navigation strategies based on landmark and
 * urban subdivisions (regions, barriers).
 **/
public class DijkstraRoadDistanceNight extends Dijkstra {

	public Set<NodeGraph> disregardedNodes = new HashSet<>();

	/**
	 * Performs the Dijkstra's algorithm to find the shortest path from the origin
	 * node to the destination node.
	 *
	 * This method calculates the shortest path in the network graph from the
	 * specified origin node to the destination node while considering optional
	 * segments to avoid and agent properties.
	 *
	 * @param originNode           The starting node for the path.
	 * @param destinationNode      The destination node to reach.
	 * @param finalDestinationNode The final destination node (primal graph) for the
	 *                             path.
	 * @param segmentsToAvoid      A set of directed edges (segments) to avoid
	 *                             during the path calculation.
	 * @param agent                The agent for which the route is computed.
	 * 
	 * @return An ArrayList of DirectedEdges representing the shortest path from the
	 *         origin to the destination.
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
	 * Finds the minimum distances for adjacent nodes of the given current node in
	 * the primal graph.
	 *
	 * @param currentNode The current node in the primal graph for which to find
	 *                    adjacent nodes.
	 */
	private void findMinDistances(NodeGraph currentNode) {

		List<NodeGraph> adjacentNodes = currentNode.getAdjacentNodes();
		List<NodeGraph> validNeighbors;

		if (!secondAttempt) {
			validNeighbors = adjacentNodes.stream().filter(targetNode -> {
				EdgeGraph edge = agentNetwork.getEdgeBetween(currentNode, targetNode);
				return (agent.gender == Gender.MALE || !shouldAvoidEdgeAtNight(edge))
						&& !disregardedNodes.contains(targetNode); // Exclude disregarded nodes
			}).collect(Collectors.toList());

			if (validNeighbors.isEmpty()) {
				// Flag currentNode as dead-end
				disregardedNodes.add(currentNode); // Assume deadEndNodes is a Set<NodeGraph>
				return; // Skip further processing of this node
			}
		} else
			validNeighbors = adjacentNodes;

		for (NodeGraph targetNode : validNeighbors) {
			EdgeGraph commonEdge = agentNetwork.getEdgeBetween(currentNode, targetNode);
			DirectedEdge outEdge = agentNetwork.getDirectedEdgeBetween(currentNode, targetNode);
			tentativeCost = 0.0;
			double error = costPerceptionError(targetNode, commonEdge, false);
			double edgeCost = commonEdge.getLength() * error;
			computeTentativeCost(currentNode, targetNode, edgeCost);
			isBest(currentNode, targetNode, outEdge);
		}
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
		if (nodeWrappersMap.get(destinationNode) == null || nodeWrappersMap.size() <= 1) {
			directedEdgesSequence.clear();
		} else
			while (nodeWrappersMap.get(step).nodeFrom != null) {
				DirectedEdge directedEdge;
				directedEdge = nodeWrappersMap.get(step).directedEdgeFrom;
				step = nodeWrappersMap.get(step).nodeFrom;
				directedEdgesSequence.add(0, directedEdge);
			}

		if (directedEdgesSequence.isEmpty()) {
			secondAttempt = true;
			return dijkstraAlgorithm(originNode, destinationNode, agent);
		}
		return directedEdgesSequence;
	}
}
