package pedSim.routeChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.javatuples.Pair;
import org.locationtech.jts.planargraph.DirectedEdge;

import pedSim.agents.Agent;
import pedSim.dijkstra.DijkstraRoadDistance;
import pedSim.dijkstra.DijkstraRoadDistanceNight;
import pedSim.engine.PedSimCity;
import sim.graph.NodeGraph;
import sim.routing.Route;

/**
 * A pathfinder for road-distance based route calculations. This class extends the functionality of the base class PathFinder.
 */
public class RoadDistancePathFinder {

	Agent agent;
	Route route = new Route();
	NodeGraph originNode, destinationNode;
	List<DirectedEdge> completeSequence = new ArrayList<>();
	List<DirectedEdge> partialSequence = new ArrayList<>();

	/**
	 * Formulates a route based on road distance between the given origin and destination nodes using the provided agent properties.
	 * 
	 * @param originNode      the origin node;
	 * @param destinationNode the destination node;
	 * @param agent           The agent for which the route is computed.
	 * @return a {@code Route} object representing the road-distance shortest path.
	 */
	public Route roadDistance(NodeGraph originNode, NodeGraph destinationNode, Agent agent) {
		this.agent = agent;
		Pair<NodeGraph, NodeGraph> routeKey = Pair.with(originNode, destinationNode);

		boolean isNight = agent.getState().isDark;
		Map<Pair<NodeGraph, NodeGraph>, List<DirectedEdge>> cache = isNight
				? (agent.isVulnerable() ? PedSimCity.routesVulnerableNight : PedSimCity.routesNonVulnerableNight)
				: PedSimCity.routesDay;

		partialSequence = cache.computeIfAbsent(routeKey, key -> {
			if (isNight)
				return new DijkstraRoadDistanceNight().dijkstraAlgorithm(originNode, destinationNode, this.agent);
			else
				return new DijkstraRoadDistance().dijkstraAlgorithm(originNode, destinationNode, this.agent);
		});

		fillRoute();
		return route;
	}

	private void fillRoute() {
		route.directedEdgesSequence = partialSequence;
		route.computeRouteSequences();
	}
}
