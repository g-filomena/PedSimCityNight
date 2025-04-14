package pedSim.routeChoice;

import pedSim.agents.Agent;
import pedSim.dijkstra.DijkstraRoadDistance;
import pedSim.dijkstra.DijkstraRoadDistanceNight;
import sim.graph.NodeGraph;
import sim.routing.Route;

/**
 * A pathfinder for road-distance based route calculations. This class extends
 * the functionality of the base class PathFinder.
 */
public class RoadDistancePathFinder extends PathFinder {

	/**
	 * Formulates a route based on road distance between the given origin and
	 * destination nodes using the provided agent properties.
	 * 
	 * @param originNode      the origin node;
	 * @param destinationNode the destination node;
	 * @param agent           The agent for which the route is computed.
	 * @return a {@code Route} object representing the road-distance shortest path.
	 */
	public Route roadDistance(NodeGraph originNode, NodeGraph destinationNode, Agent agent) {

		this.agent = agent;

		if (agent.getState().isDark) {
			final DijkstraRoadDistanceNight pathfinder = new DijkstraRoadDistanceNight();
			partialSequence = pathfinder.dijkstraAlgorithm(originNode, destinationNode, this.agent);
		} else {
			final DijkstraRoadDistance pathfinder = new DijkstraRoadDistance();
			partialSequence = pathfinder.dijkstraAlgorithm(originNode, destinationNode, this.agent);
		}

		partialSequence = sequenceOnCommunityNetwork(partialSequence);
		route.directedEdgesSequence = partialSequence;
		route.computeRouteSequences();
		return route;
	}
}
