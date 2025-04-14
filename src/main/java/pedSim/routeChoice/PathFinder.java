package pedSim.routeChoice;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.planargraph.DirectedEdge;

import pedSim.agents.Agent;
import pedSim.cognitiveMap.CommunityCognitiveMap;
import sim.graph.EdgeGraph;
import sim.graph.Graph;
import sim.graph.NodeGraph;
import sim.routing.Route;

/**
 * The `PathFinder` class provides common functionality for computing navigation
 * paths using various algorithms and graph representations.
 */
public class PathFinder {

	Agent agent;
	Route route = new Route();

	protected Graph network = CommunityCognitiveMap.getNetwork();
	NodeGraph originNode, destinationNode;
	NodeGraph tmpOrigin, tmpDestination;
	NodeGraph previousJunction = null;

	List<NodeGraph> sequenceNodes = new ArrayList<>();
	// need order here, that's why it's not hashset
	List<NodeGraph> centroidsToAvoid = new ArrayList<>();

	List<DirectedEdge> completeSequence = new ArrayList<>();
	List<DirectedEdge> partialSequence = new ArrayList<>();

	protected boolean regionBased = false;
	boolean moveOn = false;

	protected List<DirectedEdge> sequenceOnCommunityNetwork(List<DirectedEdge> partialSequence) {

		List<DirectedEdge> newSequence = new ArrayList<>();
		for (DirectedEdge directedEdge : partialSequence) {
			EdgeGraph edge = (EdgeGraph) directedEdge.getEdge();
//			EdgeGraph parentEdge = agentNetwork.getParentEdge(edge);
			if (edge != null)
				if (edge.getDirEdge(0).getCoordinate().equals(directedEdge.getCoordinate()))
					newSequence.add(edge.getDirEdge(0));
				else
					newSequence.add(edge.getDirEdge(1));
		}
		return newSequence;
	}
}
