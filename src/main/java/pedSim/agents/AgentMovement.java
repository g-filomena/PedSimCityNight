package pedSim.agents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.javatuples.Pair;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.planargraph.DirectedEdge;

import pedSim.cognitiveMap.CommunityCognitiveMap;
import pedSim.engine.PedSimCity;
import pedSim.parameters.Pars;
import sim.graph.EdgeGraph;
import sim.graph.Graph;
import sim.graph.GraphUtils;
import sim.graph.NodeGraph;
import sim.routing.Astar;
import sim.routing.Route;

/**
 * The AgentMovement class is responsible for handling the movement of agents within the simulation. It manages the
 * agent's movement along the path, including transitions between edges, speed adjustments, and the handling of various
 * conditions such as night-time speed increase and vulnerable agent behaviour.
 */
public class AgentMovement {

	// How much to move the agent by in each step()
	double reach = 0.0;

	// start, current, end position along current line
	DirectedEdge firstDirectedEdge = null;
	private EdgeGraph currentEdge = null;
	private DirectedEdge currentDirectedEdge = null;
	double currentIndex = 0.0;
	double endIndex = 0.0;

	// used by agent to walk along line segment
	int indexOnSequence = 0;
	protected LengthIndexedLine indexedSegment = null;
	protected List<DirectedEdge> directedEdgesSequence = new ArrayList<>();
	private Agent agent;
	private List<DirectedEdge> edgesWalkedSoFar = new ArrayList<>();

	boolean originalRoute = true;
	boolean increaseSpeedAtNight = false;
	Set<EdgeGraph> edgesToAvoid;

	PedSimCity state;
	Random random = new Random();

	private NodeGraph currentNode;
	private Graph network;

	private boolean avoidParksWater;

	public AgentMovement(Agent agent) {
		this.agent = agent;
		this.state = agent.getState();
		this.network = CommunityCognitiveMap.getNetwork();
	}

	/**
	 * Initialises the path (directed edges sequence) for the agent.
	 * 
	 * @param route The route that defines the path the agent should follow.
	 */
	public void initialisePath(Route route) {

		indexOnSequence = 0;
		this.directedEdgesSequence = route.directedEdgesSequence;
		edgesToAvoid = new HashSet<>();

		// set up how to traverse this first link
		firstDirectedEdge = directedEdgesSequence.get(indexOnSequence);
		currentNode = (NodeGraph) firstDirectedEdge.getFromNode();
		agent.updateAgentPosition(currentNode.getCoordinate());
		// Sets the Agent up to proceed along an Edge
		setupEdge(firstDirectedEdge);
	}

	/**
	 * Sets the agent up to proceed along a specified edge.
	 * 
	 * @param directedEdge The DirectedEdge to traverse next.
	 */
	void setupEdge(DirectedEdge directedEdge) {

		avoidParksWater = false;
		increaseSpeedAtNight = false; // removing potential increases
		currentDirectedEdge = directedEdge;
		currentEdge = (EdgeGraph) currentDirectedEdge.getEdge();

		if (state.isDark && currentDirectedEdge != firstDirectedEdge)
			checkLightLevel();

		updateCounts();

		if (PedSimCity.indexedEdgeCache.containsKey(currentDirectedEdge))
			indexedSegment = PedSimCity.indexedEdgeCache.get(currentDirectedEdge);
		else {
			addIndexedSegment(currentEdge);
			indexedSegment = PedSimCity.indexedEdgeCache.get(currentDirectedEdge);
		}

		currentIndex = indexedSegment.getStartIndex();
		endIndex = indexedSegment.getEndIndex();
		return;
	}

	/**
	 * Moves the agent along the current path.
	 */
	protected void keepWalking() {

		resetReach(); // as the segment might have changed level of crowdness
		updateReach();
		// move along the current segment
		currentIndex += reach;

		// check to see if the progress has taken the current index beyond its goal
		// If so, proceed to the next edge
		if (currentIndex > endIndex) {
			final Coordinate currentPos = indexedSegment.extractPoint(endIndex);
			agent.updateAgentPosition(currentPos);
			double residualMove = currentIndex - endIndex;
			transitionToNextEdge(residualMove);
		} else {
			// just update the position!
			final Coordinate currentPos = indexedSegment.extractPoint(currentIndex);
			agent.updateAgentPosition(currentPos);
		}
	}

	/**
	 * Transitions the agent to the next edge in the sequence if the agent has finished walking the current edge.
	 * 
	 * @param residualMove The remaining distance to travel on the current edge.
	 */
	void transitionToNextEdge(double residualMove) {

		// update the counter for where the index on the directedEdgesSequence is
		indexOnSequence += 1;
		currentEdge.decrementAgentCount(); // Leave current edge

		// check to make sure the Agent has not reached the end of the
		// directedEdgesSequence already
		if (indexOnSequence >= directedEdgesSequence.size()) {
			agent.reachedDestination.set(true);
			indexOnSequence -= 1; // make sure index is correct
			updateData();
			return;
		}

		// prepare to setup to the next edge
		DirectedEdge nextDirectedEdge = directedEdgesSequence.get(indexOnSequence);
		setupEdge(nextDirectedEdge);

		reach = residualMove;
		updateReach(); // slowed down or speeded up
		currentIndex += reach;

		// check to see if the progress has taken the current index beyond its goal
		// given the direction of movement. If so, proceed to the next edge
		if (currentIndex > endIndex) {
			residualMove = currentIndex - endIndex;
			transitionToNextEdge(residualMove);
		}
	}

	/**
	 * Resets the agent's movement reach to the base move rate.
	 */
	private void resetReach() {
		reach = Pars.moveRate;
	}

	/**
	 * Increases the agent's movement reach based on the speed factor for night time.
	 */
	private void increaseReach() {
		reach = reach + (Pars.moveRate * Pars.SPEED_INCREMENT_FACTOR);
	}

	/**
	 * Updates the agent's reach if the agent is moving faster at night.
	 */
	private void updateReach() {
		if (increaseSpeedAtNight)
			increaseReach();
	}

	/**
	 * Computes an alternative route for the agent to avoid dangerous or unsuitable edges, reusing a cached route if
	 * available.
	 */
	private void computeAlternativeRoute() {
		NodeGraph currentNode = (NodeGraph) edgesWalkedSoFar.get(edgesWalkedSoFar.size() - 1).getToNode();
		Pair<NodeGraph, NodeGraph> routeKey = Pair.with(currentNode, agent.destinationNode);
		Map<Pair<NodeGraph, NodeGraph>, List<DirectedEdge>> cache = (agent.isVulnerable() || avoidParksWater)
				? PedSimCity.altRoutesVulnerable
				: PedSimCity.altRoutesNonVulnerable;

		// Check if a cached route already exists
		if (cache.containsKey(routeKey)) {
			resetPath(new ArrayList<>(cache.get(routeKey)));
			originalRoute = false;
			return;
		}

		setEdgesToAvoid();
		Astar aStar = new Astar();
		Route alternativeRoute = aStar.astarRoute(currentNode, agent.destinationNode, network, edgesToAvoid);

		int iteration = 0;
		while (alternativeRoute == null) {
			switch (iteration) {
			case 0 -> {
				// Add secondary roads, still try avoiding non-lit and parks/water
				edgesToAvoid.removeAll(CommunityCognitiveMap.getNeighbourhoodEdges());
				edgesToAvoid.addAll(CommunityCognitiveMap.getEdgesNonLitNonCommunityKnown());
				if (agent.isVulnerable() || avoidParksWater)
					edgesToAvoid.addAll(CommunityCognitiveMap.getEdgesWithinParksOrAlongWater());
			}
			// give up park avoidance
			case 1 -> edgesToAvoid.removeAll(CommunityCognitiveMap.getEdgesWithinParks());
			// give up water avoidance
			case 2 -> edgesToAvoid.removeAll(CommunityCognitiveMap.getEdgesAlongWater());
			// give up non-lit avoidance
			case 3 -> edgesToAvoid.removeAll(CommunityCognitiveMap.getEdgesNonLitNonCommunityKnown());
			default -> {
				edgesToAvoid.clear();
			}
			}
			alternativeRoute = aStar.astarRoute(currentNode, agent.destinationNode, CommunityCognitiveMap.getNetwork(),
					edgesToAvoid);
			iteration++;
		}

		// Cache and apply the new route
		cache.put(routeKey, new ArrayList<>(alternativeRoute.directedEdgesSequence));
		resetPath(alternativeRoute.directedEdgesSequence);
		originalRoute = false;
	}

	/**
	 * Gets the set of edges that the agent should avoid during movement.
	 * 
	 * @return The set of edges to avoid.
	 */
	private void setEdgesToAvoid() {

		// the disregarded one
		edgesToAvoid.add(currentEdge);

		// non-lit roads
		edgesToAvoid.addAll(CommunityCognitiveMap.getEdgesNonLitNonCommunityKnown());

		// for vulnerable:
		if (agent.isVulnerable()) {
			// 1) add everything,
			edgesToAvoid.addAll(CommunityCognitiveMap.getCommunityNetwork().getEdges());
			// 2) remove primary,
			edgesToAvoid.removeAll(CommunityCognitiveMap.getCommunityKnownEdges());
			// 3) remove known
			Set<EdgeGraph> knownEdges = new HashSet<>(
					GraphUtils.getEdgesFromEdgeIDs(agent.getCognitiveMap().getKnownEdges(), PedSimCity.edgesMap));
			edgesToAvoid.removeAll(knownEdges);
		}

		// for vulnerable and non-vulnerable agents who do not feel like water and parks
		if (agent.isVulnerable() || avoidParksWater)
			edgesToAvoid.addAll(CommunityCognitiveMap.getEdgesWithinParksOrAlongWater());

		edgesToAvoid.removeAll(agent.destinationNode.getEdges());
	}

	/**
	 * Adds an indexed segment to the indexed edge cache.
	 *
	 * @param edge The edge to add to the indexed edge cache.
	 */
	private void addIndexedSegment(EdgeGraph edge) {

		LineString line = edge.getLine();
		double distanceToStart = line.getStartPoint().distance(agent.getLocation().geometry);
		double distanceToEnd = line.getEndPoint().distance(agent.getLocation().geometry);

		if (distanceToEnd < distanceToStart)
			line = line.reverse();

		final LineString finalLine = line;
		PedSimCity.indexedEdgeCache.put(currentDirectedEdge, new LengthIndexedLine(finalLine));
	}

	/**
	 * Checks the light level of the current edge.
	 */
	private void checkLightLevel() {

		// edge is lit but night
		if (CommunityCognitiveMap.getLitEdges().contains(currentEdge))
			whenLit(currentEdge);
		else
			whenNonLit(currentEdge);
	}

	/**
	 * Handles the case when the edge is lit.
	 *
	 * @param edge The edge to be approached.
	 */
	private void whenLit(EdgeGraph edge) {

		// vulnerable agents avoid parks at night at planning phase.
		if (isParkWaterNonVulnerable(edge))
			whenParkWater(edge);
		else
			whenLitVulernable(edge);
	}

	/**
	 * Checks if the edge is next to a park or water and the agent is non-vulnerable.
	 *
	 * @param edge The edge to check.
	 * @return true if the edge is next to a park or water and non-vulnerable, false otherwise.
	 */
	private boolean isParkWaterNonVulnerable(EdgeGraph edge) {
		return isEdgeNextToParkOrWater(edge) && !agent.isVulnerable();
	}

	/**
	 * Handles the case when the agent is approaching an edge in proximity or parks or water.
	 *
	 * @param edge The edge to be approached.
	 */
	private void whenParkWater(EdgeGraph edge) {
		// vulnerable avoid parks at night at planning phase.
		if (canReroute()) {
			avoidParksWater = true;
			computeAlternativeRoute();
		} else
			increaseSpeedAtNight = true;
	}

	/**
	 * Handles the case when a vulnerable agent is approaching a lit edge.
	 *
	 * @param edge The lit edge to be approached.
	 */
	private void whenLitVulernable(EdgeGraph edge) {

		// not known, not main road, not busy -> recompute
		if (!isEdgeKnown(edge) && !isEdgeMainRoad(edge) && !isEdgeCrowded(edge)) {
			if (canReroute())
				computeAlternativeRoute();
			else
				increaseSpeedAtNight = true;
		}
		// not main road and not crowded -> reroute or increase speed
		else if (!isEdgeMainRoad(edge) && !isEdgeCrowded(edge)) {
			if (random.nextDouble() < 0.5 && canReroute())
				computeAlternativeRoute();
			else
				increaseSpeedAtNight = true;
		}
	}

	/**
	 * Determines what to do, based on the agent vulnerability, when an agent is approaching a non-lit edge.
	 *
	 * @param edge The lit edge to be approached.
	 */
	private void whenNonLit(EdgeGraph edge) {

		if (agent.isVulnerable())
			nonLitVulnerable(edge);
		else
			nonLit(edge);
	}

	/**
	 * Handles the case when a non-vulnerable agent is approaching a non-lit edge.
	 *
	 * @param edge The lit edge to be approached.
	 */
	private void nonLit(EdgeGraph edge) {

		if (isParkWaterNonVulnerable(edge)) {
			whenParkWater(edge);
			return;
		}
		// crowded -> ok
		else if (isEdgeCrowded(edge))
			return;
		// not known, not main road, not crowded -> reroute or increase speed
		else if (!isEdgeKnown(edge) && !isEdgeMainRoad(edge))
			rerouteOrIncreaseSpeed();
		// main road or known, not crowded -> ok
		else if (isEdgeMainRoad(edge) || isEdgeKnown(edge))
			return;
	}

	/**
	 * Handles the case when a vulnerable agent is approaching a non-lit edge.
	 *
	 * @param edge The lit edge to be approached.
	 */
	private void nonLitVulnerable(EdgeGraph edge) {

		// not known, not main road, not crowded -> reroute
		if (!isEdgeKnown(edge) && !isEdgeMainRoad(edge) && !isEdgeCrowded(edge)) {
			if (canReroute())
				computeAlternativeRoute();
			else
				increaseSpeedAtNight = true;
		}
		// not known but crowded -> OK
		else if (!isEdgeKnown(edge) && isEdgeCrowded(edge))
			return;
		// main road but not crowded -> increase speed
		else if (isEdgeMainRoad(edge) && !isEdgeCrowded(edge))
			increaseSpeedAtNight = true;
		// known, not main road, not crowded -> > reroute or increase speed
		else if (isEdgeKnown(edge) && !isEdgeCrowded(edge))
			rerouteOrIncreaseSpeed();
	}

	/**
	 * Determines whether to reroute the agent or increase its speed.
	 */
	private void rerouteOrIncreaseSpeed() {
		if (random.nextDouble() < 0.5 && canReroute())
			computeAlternativeRoute();
		else
			increaseSpeedAtNight = true;
	}

	/**
	 * Checks whether the edge is overcrowded based on the agent count.
	 *
	 * @param edge The edge to check.
	 * @return true if the edge is overcrowded, false otherwise.
	 */
	private boolean isEdgeCrowded(EdgeGraph edge) {
		double volumePercentile = calculateVolumesPercentile(20);
		return edge.getAgentCount() >= volumePercentile;
	}

	/**
	 * Calculates the volume percentile for a given percentile.
	 *
	 * @param percentile The percentile to calculate.
	 * @return The volume at the given percentile.
	 */
	private double calculateVolumesPercentile(int percentile) {
		// Collect volumes from edges (Set to List)
		List<Integer> volumes = PedSimCity.edges.stream().map(EdgeGraph::getAgentCount) // Map each edge to its
																						// agentCount
				.filter(agentCount -> agentCount > 0) // Only keep agent counts greater than 0
				.sorted() // Sort the agent counts
				.collect(Collectors.toList()); // Collect to a List

		// Calculate the index for the percentile
		int index = (int) Math.ceil(percentile / 100.0 * volumes.size()) - 1;
		index = Math.max(0, index); // Ensure the index is within bounds

		// Return the value at the calculated index

		if (!volumes.isEmpty())
			return volumes.get(index);
		else
			return Double.MAX_VALUE;
	}

	/**
	 * Checks if the edge is a main road.
	 *
	 * @param edge The edge to check.
	 * @return true if the edge is primary (or secondary, tertiary, if included), false otherwise.
	 */
	private boolean isEdgeMainRoad(EdgeGraph edge) {
		return CommunityCognitiveMap.getCommunityKnownEdges().contains(edge);
	}

	/**
	 * Checks if the agent can reroute based on the current edge and destination.
	 *
	 * @return true if the agent can reroute, false otherwise.
	 */
	private boolean canReroute() {

		if (currentEdge.getNodes().contains(agent.destinationNode) || indexOnSequence == 0 || !originalRoute)
			return false;
		return true;
	}

	/**
	 * Checks if the edge is known by the agent.
	 *
	 * @param edge The edge to check.
	 * @return true if the edge is known by the agent, false otherwise.
	 */
	private boolean isEdgeKnown(EdgeGraph edge) {
		return agent.getCognitiveMap().isEdgeKnown(edge);
	}

	/**
	 * Checks if the edge is next to a park or water.
	 *
	 * @param edge The edge to check.
	 * @return true if the edge is next to a park or water, false otherwise.
	 */
	private boolean isEdgeNextToParkOrWater(EdgeGraph edge) {
		return CommunityCognitiveMap.getEdgesWithinParksOrAlongWater().contains(edge);
	}

	/**
	 * Updates the counts for the current edge the agent is walking on.
	 */
	private void updateCounts() {
		edgesWalkedSoFar.add(currentDirectedEdge);
		currentEdge.incrementAgentCount();
		agent.metersWalkedTot += currentEdge.getLength();
		agent.metersWalkedDay += currentEdge.getLength();
	}

	/**
	 * Updates the data related to the agent's ruote to derive pedestrian volumes.
	 */
	public void updateData() {
		agent.route.resetRoute(new ArrayList<>(edgesWalkedSoFar));
		state.flowHandler.updateFlowsData(agent, agent.route, state.isDark);
	}

	/**
	 * Resets the path for the agent to follow a new sequence of directed edges.
	 *
	 * @param directedEdgesSequence The new sequence of directed edges.
	 */
	private void resetPath(List<DirectedEdge> directedEdgesSequence) {

		avoidParksWater = false;
		indexOnSequence = 0;
		this.directedEdgesSequence = directedEdgesSequence;
		currentDirectedEdge = directedEdgesSequence.get(0);

		// set up how to traverse this first link
		currentDirectedEdge = directedEdgesSequence.get(indexOnSequence);
		currentEdge = (EdgeGraph) currentDirectedEdge.getEdge();
		currentNode = (NodeGraph) firstDirectedEdge.getFromNode();
		edgesToAvoid.clear();
		agent.updateAgentPosition(currentNode.getCoordinate());
	}
}
