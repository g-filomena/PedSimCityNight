package pedSim.agents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.planargraph.DirectedEdge;

import pedSim.cognitiveMap.CommunityCognitiveMap;
import pedSim.engine.PedSimCity;
import pedSim.parameters.Pars;
import pedSim.utilities.StringEnum.Gender;
import sim.graph.EdgeGraph;
import sim.graph.NodeGraph;
import sim.routing.Astar;
import sim.routing.Route;

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

	PedSimCity state;
	Random random = new Random();

	private NodeGraph currentNode;

	public AgentMovement(Agent agent) {
		this.agent = agent;
		this.state = agent.getState();
	}

	/**
	 * Initialises the directedEdgesSequence (the path) for the agent.
	 */
	public void initialisePath(Route route) {

		indexOnSequence = 0;
		this.directedEdgesSequence = route.directedEdgesSequence;

		// set up how to traverse this first link
		firstDirectedEdge = directedEdgesSequence.get(indexOnSequence);
		currentNode = (NodeGraph) firstDirectedEdge.getFromNode();
		agent.updateAgentPosition(currentNode.getCoordinate());
		// Sets the Agent up to proceed along an Edge
		setupEdge(firstDirectedEdge);
	}

	/**
	 * Sets the agent up to proceed along an edge.
	 *
	 * @param edge The EdgeGraph to traverse next.
	 */
	void setupEdge(DirectedEdge directedEdge) {

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
	 * Moves the agent along the computed route.
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
	 * Transitions to the next edge in the {@code directedEdgesSequence}.
	 *
	 * @param residualMove The amount of distance the agent can still travel this
	 *                     step.
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
	 * Updates the agent's speed based on the move rate and link direction.
	 * 
	 * @param pedestrianDensity
	 */

	private void resetReach() {
		reach = Pars.moveRate;
	}

	private void increaseReach() {
		reach = reach + (Pars.moveRate * Pars.SPEED_INCREMENT_FACTOR);
	}

	private void updateReach() {
		if (increaseSpeedAtNight)
			increaseReach();
	}

	private void computeAlternativeRoute() {

		// get the one before the "bad" edge
		NodeGraph currentNode = (NodeGraph) edgesWalkedSoFar.get(edgesWalkedSoFar.size() - 1).getToNode();

		Set<EdgeGraph> edgesToAvoid = getEdgesToAvoid();
		Route alternativeRoute = Astar.astarRoute(currentNode, agent.destinationNode,
				CommunityCognitiveMap.getNetwork(), edgesToAvoid);

//		while (alternativeRoute == null) {
//			edgesToAvoid.clear();
//			edgesToAvoid.removeAll(CommunityCognitiveMap.nonLitNonPrimary);
//			alternativeRoute = Astar.astarRoute(currentNode, agent.destinationNode, CommunityCognitiveMap.getNetwork(),
//					edgesToAvoid);
//		}

		originalRoute = false;
		resetPath(alternativeRoute.directedEdgesSequence);
	}

	private Set<EdgeGraph> getEdgesToAvoid() {

		Set<EdgeGraph> edgesToAvoid = new HashSet<>();
		edgesToAvoid.add(currentEdge);
		edgesToAvoid.addAll(CommunityCognitiveMap.nonLitNonPrimary);
		if (!isMale())
			edgesToAvoid.addAll(CommunityCognitiveMap.edgesWithinParks);

		return edgesToAvoid;
	}

	private void addIndexedSegment(EdgeGraph edge) {

		LineString line = edge.getLine();
		double distanceToStart = line.getStartPoint().distance(agent.getLocation().geometry);
		double distanceToEnd = line.getEndPoint().distance(agent.getLocation().geometry);

		if (distanceToEnd < distanceToStart)
			line = line.reverse();

		final LineString finalLine = line;
		PedSimCity.indexedEdgeCache.put(currentDirectedEdge, new LengthIndexedLine(finalLine));
	}

	private void checkLightLevel() {

		// edge is lit but night
		if (CommunityCognitiveMap.litEdges.contains(currentEdge))
			whenLit(currentEdge);
		else
			whenNotLit(currentEdge);

	}

	private void whenLit(EdgeGraph edge) {

		// female avoid parks at night at planning phase.
		if (isEdgeInPark(edge) && isMale()) {
			increaseSpeedAtNight = true;
			return;
		}

		// If not male, evaluate possible rerouting or speed adjustments
		if (!isMale()) {

			// If the edge is neither known nor a primary road, recompute the route
			if (!isEdgeKnown(edge) && !isEdgePrimary(edge)) {
				if (canReroute())
					computeAlternativeRoute();
				else
					increaseSpeedAtNight = true;
				return;
			}

			// If the edge is known and not busy/crowded, randomly choose to reroute or
			// increase speed
			if (!isEdgeBusyOrCrowded(edge)) {
				if (random.nextDouble() < 0.5 && canReroute())
					computeAlternativeRoute();
				else
					increaseSpeedAtNight = true;
				return;
			}
		}
		// Otherwise (male or busy/crowded known edge), continue as normal (no action
		// needed)
	}

	private void whenNotLit(EdgeGraph edge) {

		// major and primary roads are always known
		// narrow, not known, not main road, not busy -> non-male reroute; male reroute
		// or increase
		if (!isEdgeKnown(edge) && !isEdgePrimary(edge) && !isEdgeBusyOrCrowded(edge)) {
			if (isMale())
				rerouteOrIncreaseSpeed();
			else if (canReroute()) // female
				computeAlternativeRoute();
			else
				increaseSpeedAtNight = true;
			return;
		}

		// female avoid parks at night at planning phase.
		if (isEdgeInPark(edge) && isMale()) {
			if (canReroute())
				computeAlternativeRoute();
			else
				increaseSpeedAtNight = true;
			return;
		}

		// not known but busy -> keep walking
		if (!isEdgeKnown(edge) && isEdgeBusyOrCrowded(edge))
			return;

		// primary (primary always known)
		if (isEdgePrimary(edge)) {
			// if not busy, non-males increase speed
			if (!isMale() && !isEdgeBusyOrCrowded(edge))
				increaseSpeedAtNight = true;
			// otherways -> keep walking
			return;
		}

		// known and NOT primary
		if (isEdgeKnown(edge)) {
			// if not busy, non-males increase speed or reroute
			if (!isMale() && !isEdgeBusyOrCrowded(edge))
				rerouteOrIncreaseSpeed();
			return;
		}
	}

	private void rerouteOrIncreaseSpeed() {
		if (random.nextDouble() < 0.5 && canReroute())
			computeAlternativeRoute();
		else
			increaseSpeedAtNight = true;
		return;
	}

	// TODO improve COMPUTATION
	private boolean isEdgeBusyOrCrowded(EdgeGraph edge) {
		if (edge.getAgentCount() >= calculateVolumesPercentile(80))
			return true;
		else
			return false;
	}

	private double calculateVolumesPercentile(int percentile) {
		// Collect volumes from edges (Set to List)
		List<Integer> volumes = PedSimCity.edges.stream().map(EdgeGraph::getAgentCount) // Map each edge to its agent
																						// count
				.sorted() // Sort the agent counts
				.collect(Collectors.toList()); // Collect to a List

		// Calculate the index for the percentile
		int index = (int) Math.ceil(percentile / 100.0 * volumes.size()) - 1;
		index = Math.max(0, index); // Ensure the index is within bounds

		// Return the value at the calculated index
		return volumes.get(index);
	}

	private boolean isEdgePrimary(EdgeGraph edge) {
		return CommunityCognitiveMap.communityKnownEdges.contains(edge);
	}

	private boolean canReroute() {

		if (currentEdge.getNodes().contains(agent.destinationNode) || indexOnSequence == 0 || !originalRoute)
			return false;
		return true;

	}

	private boolean isEdgeKnown(EdgeGraph edge) {
		return agent.getCognitiveMap().isEdgeKnown(edge);
	}

	private boolean isEdgeInPark(EdgeGraph edge) {
		return !CommunityCognitiveMap.edgesWithinParks.contains(edge);
	}

	private boolean isMale() {
		return agent.gender.equals(Gender.MALE);
	}

	private void updateCounts() {

		edgesWalkedSoFar.add(currentDirectedEdge);
		currentEdge.incrementAgentCount();
		agent.kmWalkedTot += currentEdge.getLength();
		agent.kmWalkedDay += currentEdge.getLength();
	}

	public void updateData() {
		agent.route.resetRoute(new ArrayList<>(edgesWalkedSoFar));
		state.flowHandler.updateFlowsData(agent, agent.route, state.isDark);
	}

	private void resetPath(List<DirectedEdge> directedEdgesSequence) {

		indexOnSequence = 0;
		this.directedEdgesSequence = directedEdgesSequence;
		currentDirectedEdge = directedEdgesSequence.get(0);

		// set up how to traverse this first link
		currentDirectedEdge = directedEdgesSequence.get(indexOnSequence);
		currentEdge = (EdgeGraph) currentDirectedEdge.getEdge();
		currentNode = (NodeGraph) firstDirectedEdge.getFromNode();
		agent.updateAgentPosition(currentNode.getCoordinate());
	}

}
