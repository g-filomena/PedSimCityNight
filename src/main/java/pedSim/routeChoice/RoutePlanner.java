package pedSim.routeChoice;

import pedSim.agents.Agent;
import sim.graph.NodeGraph;
import sim.routing.Route;

/**
 * The `RoutePlanner` class is responsible for calculating a route for an agent within a pedestrian simulation. It
 * considers the agent's route choice properties and strategies to determine the optimal path from an origin node to a
 * destination node.
 */
public class RoutePlanner {

	private Agent agent;
	private NodeGraph originNode;
	private NodeGraph destinationNode;
	private Route route = new Route();

	/**
	 * Constructs a `RoutePlanner` instance for calculating a route.
	 *
	 * @param originNode      The starting node of the route.
	 * @param destinationNode The destination node of the route.
	 * @param agent           The agent for which the route is being planned.
	 */
	public RoutePlanner(NodeGraph originNode, NodeGraph destinationNode, Agent agent) {
		this.originNode = originNode;
		this.destinationNode = destinationNode;
		this.agent = agent;
	}

	/**
	 * Defines the path for the agent based on route choice properties and strategies.
	 *
	 * @return A `Route` object representing the calculated route.
	 */
	public Route definePath() {

		route = new RoadDistancePathFinder().roadDistance(originNode, destinationNode, agent);
		return route;
	}
}
