package pedSim.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import pedSim.cognitiveMap.CognitiveMap;
import pedSim.cognitiveMap.CommunityCognitiveMap;
import pedSim.engine.PedSimCity;
import pedSim.parameters.TimePars;
import pedSim.routeChoice.RoutePlanner;
import pedSim.utilities.StringEnum;
import pedSim.utilities.StringEnum.AgentStatus;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.graph.Graph;
import sim.graph.NodeGraph;
import sim.graph.NodesLookup;
import sim.routing.Route;
import sim.util.geo.MasonGeometry;

/**
 * This class represents an agent in the pedestrian simulation. Agents move
 * along paths between origin and destination nodes.
 */
public class Agent implements Steppable {

	private static final long serialVersionUID = 1L;
	PedSimCity state;
	public Integer agentID;

	public AgentStatus status;

	// Initial Attributes
	public NodeGraph originNode = null;
	public NodeGraph destinationNode = null;

	final AtomicBoolean reachedDestination = new AtomicBoolean(false);

	private AgentProperties agentProperties;
	private CognitiveMap cognitiveMap;

	Stoppable killAgent;
	private MasonGeometry currentLocation;

	public Route route;
	protected double timeAtDestination = Double.MAX_VALUE;
	NodeGraph lastDestination;
	Random random = new Random();
	protected AgentMovement agentMovement;
	double kmWalkedTot;
	private double distanceNextDestination = 0.0;
	public double kmWalkedDay = 0.0;

	public StringEnum.Gender gender;

	/**
	 * Constructor Function. Creates a new agent with the specified agent
	 * properties.
	 *
	 * @param state the PedSimCity simulation state.
	 */
	public Agent(PedSimCity state) {

		this.state = state;
		cognitiveMap = new CognitiveMap(this);
		initialiseAgentProperties();
		status = AgentStatus.WAITING;
		final GeometryFactory fact = new GeometryFactory();
		currentLocation = new MasonGeometry(fact.createPoint(new Coordinate(10, 10)));
		currentLocation.isMovable = true;
		updateAgentPosition(cognitiveMap.getHomeNode().getCoordinate());
	}

	public Agent() {
	}

	/**
	 * Initialises the agent properties.
	 */
	protected void initialiseAgentProperties() {

		agentProperties = new AgentProperties(this);
	}

	/**
	 * This is called every tick by the scheduler. It moves the agent along the
	 * path.
	 *
	 * @param state the simulation state.
	 */
	@Override
	public void step(SimState state) {
		final PedSimCity stateSchedule = (PedSimCity) state;

		if (isWaiting())
			return;

		if (isWalkingAlone() && destinationNode == null)
			planNewTrip();
		else if (reachedDestination.get())
			handleReachedDestination(stateSchedule);
		else if (isAtDestination() && timeAtDestination <= state.schedule.getSteps())
			goHome();
		else if (isAtDestination())
			;
		else
			agentMovement.keepWalking();
	}

	private synchronized void planNewTrip() {

		defineOriginDestination();
		if (destinationNode.getID() == originNode.getID()) {
			reachedDestination.set(true);
			return;
		}

		planRoute();
		agentMovement = new AgentMovement(this);
		agentMovement.initialisePath(route);
	}

	public void nextActivity() {

		if (!cognitiveMap.formed)
			getCognitiveMap().formCognitiveMap();
		startWalkingAlone();

	}

	private void startWalkingAlone() {

		destinationNode = null;
		status = AgentStatus.WALKING_ALONE;
		updateAgentLists(true, false);

	}

	protected synchronized void defineOriginDestination() {
		defineOrigin();
		defineDestination();
	}

	private void defineOrigin() {// Define origin based on agent status

		if (isWalkingAlone())
			originNode = cognitiveMap.getHomeNode();
		else if (isGoingHome()) {
			if (currentLocation.getGeometry().getCoordinate() != lastDestination.getCoordinate()) {
				currentLocation.geometry = lastDestination.getMasonGeometry().geometry;
			}
			originNode = lastDestination;
		}
	}

	private void defineDestination() {
		// Define destination based on agent status
		if (isWalkingAlone())
			randomDestination();
		else if (isGoingHome())
			destinationNode = cognitiveMap.getHomeNode();
	}

	private void randomDestination() {

		double lowerLimit = distanceNextDestination * 0.90;
		double upperLimit = distanceNextDestination;
		Graph network = CommunityCognitiveMap.getNetwork();

		List<NodeGraph> candidates = new ArrayList<NodeGraph>();
		while (destinationNode == null) {
			candidates = NodesLookup.getNodesBetweenDistanceInterval(network, originNode, lowerLimit, upperLimit);
//			candidates.retainAll(getCognitiveMap().getKnownNodes());
			lowerLimit = lowerLimit * 0.90;
			upperLimit = upperLimit * 1.10;

//		List<NodeGraph> DMAcandidates = new ArrayList<>(candidates);
//		DMAcandidates.retainAll(CommunityCognitiveMap.destinationCandidates);
//		if (DMAcandidates.isEmpty())
//			DMAcandidates = candidates;
			if (candidates.isEmpty())
				continue;
			destinationNode = NodesLookup.randomNodeFromList(candidates);
			if (destinationNode.getEdges().stream().anyMatch(CommunityCognitiveMap.edgesWithinParks::contains))
				destinationNode = null;
		}
	}

	private void handleReachedDestination(PedSimCity stateSchedule) {

		reachedDestination.set(false);
		updateAgentPosition(destinationNode.getCoordinate());

		if ((destinationNode.getID() != originNode.getID()) && route.getLineString().getLength() > 10) {
//			learning.updateAgentMemory(route);
		}

		updateAgentLists(false, destinationNode == cognitiveMap.getHomeNode());
		originNode = null;
		lastDestination = destinationNode;
		destinationNode = null;
		switch (status) {
		case WALKING_ALONE:
			handleReachedSoloDestination();
			break;
		case GOING_HOME:
			handleReachedHome();
			break;
		default:
			break;
		}
	}

	/**
	 * Moves the agent to the given coordinates.
	 *
	 * @param c the coordinates.
	 */
	public void updateAgentPosition(Coordinate coordinate) {
		GeometryFactory geometryFactory = new GeometryFactory();
		Point newLocation = geometryFactory.createPoint(coordinate);
		state.agents.setGeometryLocation(currentLocation, newLocation);
		currentLocation.geometry = newLocation;
	}

	private void handleReachedSoloDestination() {
		status = AgentStatus.AT_DESTINATION;
		calculateTimeAtDestination(state.schedule.getSteps());
	}

	private void handleReachedHome() {
		status = AgentStatus.WAITING;
	}

	protected void calculateTimeAtDestination(long steps) {
		// Generate a random number between 15 (inclusive) and 120 (inclusive)
		int randomMinutes = 15 + random.nextInt(106);
		// Multiply with MINUTES_IN_STEPS
		timeAtDestination = (randomMinutes * TimePars.MINUTE_TO_STEPS) + steps;
	}

	protected void goHome() {

		state.agentsWalking.add(this);
		status = AgentStatus.GOING_HOME;
		planNewTrip();
	}

	public void updateAgentLists(boolean isWalking, boolean reachedHome) {

		if (isWalking) {
			state.agentsWalking.add(this);
			state.agentsAtHome.remove(this);
		} else {
			if (reachedHome)
				state.agentsAtHome.add(this);
			state.agentsWalking.remove(this);
		}
	}

	/**
	 * Plans the route for the agent.
	 * 
	 * @throws Exception
	 */
	protected void planRoute() {

		RoutePlanner planner = new RoutePlanner(originNode, destinationNode, this);
		route = planner.definePath();
	}

	/**
	 * Sets the stoppable reference for the agent.
	 *
	 * @param a The stoppable reference.
	 */
	public void setStoppable(Stoppable a) {
		this.killAgent = a;
	}

	/**
	 * Removes the agent from the simulation.
	 *
	 * @param stateSchedule the simulation state.
	 */
	protected void removeAgent() {
		state.agentsList.remove(this);
		killAgent.stop();
		if (state.agentsList.isEmpty())
			state.finish();
	}

	/**
	 * Updates data related to the volumes on the segments traversed.
	 */
//	public void updateData() {
//		state.flowHandler.updateFlowsData(this, route);
//	}

	/**
	 * Gets the geometry representing the agent's location.
	 *
	 * @return The geometry representing the agent's location.
	 */
	public MasonGeometry getLocation() {
		return currentLocation;
	}

	public AgentProperties getProperties() {
		return agentProperties;
	}

	public CognitiveMap getCognitiveMap() {
		return cognitiveMap;
	}

	private boolean isWaiting() {
		return status.equals(AgentStatus.WAITING);
	}

	public boolean isWalkingAlone() {
		return status.equals(AgentStatus.WALKING_ALONE);
	}

	public boolean isGoingHome() {
		return status.equals(AgentStatus.GOING_HOME);
	}

	private boolean isAtDestination() {
		return status.equals(AgentStatus.AT_DESTINATION) || status.equals(AgentStatus.AT_GROUP_DESTINATION);
	}

	public double getTotalKmWalked() {
		return kmWalkedTot;
	}

	public double getKmWalkedDay() {
		return kmWalkedDay;
	}

	public void setDistanceNextDestination(double distanceNextDestination) {
		this.distanceNextDestination = distanceNextDestination;
	}

	public PedSimCity getState() {
		return state;
	}

}