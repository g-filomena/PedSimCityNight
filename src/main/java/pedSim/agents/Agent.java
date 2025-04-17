package pedSim.agents;

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
import pedSim.utilities.StringEnum.Vulnerable;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.graph.Graph;
import sim.graph.NodeGraph;
import sim.graph.NodesLookup;
import sim.routing.Route;
import sim.util.geo.MasonGeometry;

/**
 * This class represents an agent in the pedestrian simulation. Agents move along paths between origin and destination nodes.
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

	public StringEnum.Vulnerable vulnerable;
	private Graph agentNetwork;

	/**
	 * Constructor Function. Creates a new agent with the specified agent properties.
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
		this.agentNetwork = CommunityCognitiveMap.getNetwork();
	}

	/**
	 * Initialises the agent properties.
	 */
	protected void initialiseAgentProperties() {

		agentProperties = new AgentProperties(this);
	}

	/**
	 * This is called every tick by the scheduler. It moves the agent along the path.
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

	/**
	 * Plans a new trip for the agent based on its current status.
	 */
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

	/**
	 * Define next activity.
	 */
	public void nextActivity() {

		if (!cognitiveMap.formed)
			getCognitiveMap().formCognitiveMap();
		startWalkingAlone();

	}

	/**
	 * Starts walking alone and sets the agent's status to WALKING_ALONE.
	 */
	private void startWalkingAlone() {
		destinationNode = null;
		status = AgentStatus.WALKING_ALONE;
		updateAgentLists(true, false);

	}

	/**
	 * Defines the origin and destination of the agent.
	 */
	protected synchronized void defineOriginDestination() {
		defineOrigin();
		defineDestination();
	}

	/**
	 * Defines the destination node based on the agent's status.
	 */
	private void defineOrigin() {// Define origin based on agent status

		if (isWalkingAlone())
			originNode = cognitiveMap.getHomeNode();
		else if (isGoingHome()) {
			if (currentLocation.getGeometry().getCoordinate() != lastDestination.getCoordinate())
				currentLocation.geometry = lastDestination.getMasonGeometry().geometry;
			originNode = lastDestination;
		}
	}

	/**
	 * Defines the destination node based on the agent's status.
	 */
	private void defineDestination() {
		// Define destination based on agent status
		if (isWalkingAlone())
			randomDestination();
		else if (isGoingHome())
			destinationNode = cognitiveMap.getHomeNode();
	}

	/**
	 * Randomly selects a destination node within a specified distance range.
	 */
	private void randomDestination() {

		// Initialise limits for distance calculation
		double lowerLimit = distanceNextDestination * 0.90;
		double upperLimit = distanceNextDestination * 1.10;

		// Get the network graph from the cognitive map

		// Loop until a valid destination is found
		while (destinationNode == null) {

			// Get candidate nodes between the current distance range
			List<NodeGraph> destinationCandidates = NodesLookup.getNodesBetweenDistanceInterval(agentNetwork,
					originNode, lowerLimit, upperLimit);

			if (destinationCandidates.isEmpty()) {
				// Skip this iteration and adjust the limits if no candidates found
				lowerLimit *= 0.90;
				upperLimit *= 1.10;
				continue; // Continue with the next loop iteration
			}

			// Select a random destination node from the list of candidates
			destinationNode = NodesLookup.randomNodeFromList(destinationCandidates);

			// If it's dark, filter out destination nodes that lie in parks or along rivers
			if (state.isDark && destinationNode.getEdges().stream()
					.anyMatch(CommunityCognitiveMap.getEdgesWithinParksOrAlongWater()::contains)) {
				destinationNode = null; // Set destination to null and try again

				// Adjust the distance range for the next iteration
				lowerLimit *= 0.90;
				upperLimit *= 1.10;
			}
		}
	}

	/**
	 * Handles actions when the agent reaches its destination.
	 *
	 * @param stateSchedule the simulation state.
	 */
	private void handleReachedDestination(PedSimCity stateSchedule) {

		reachedDestination.set(false);
		updateAgentPosition(destinationNode.getCoordinate());

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
	 * @param coordinate the coordinates.
	 */
	public void updateAgentPosition(Coordinate coordinate) {
		GeometryFactory geometryFactory = new GeometryFactory();
		Point newLocation = geometryFactory.createPoint(coordinate);
		state.agents.setGeometryLocation(currentLocation, newLocation);
		currentLocation.geometry = newLocation;
	}

	/**
	 * Handles the agent's status when it reaches its solo destination.
	 */
	private void handleReachedSoloDestination() {
		status = AgentStatus.AT_DESTINATION;
		calculateTimeAtDestination(state.schedule.getSteps());
	}

	/**
	 * Handles the agent's status when it reaches home.
	 */
	private void handleReachedHome() {
		status = AgentStatus.WAITING;
	}

	/**
	 * Calculates the time the agent will stay at its destination.
	 *
	 * @param steps the current simulation step.
	 */
	protected void calculateTimeAtDestination(long steps) {
		// Generate a random number between 15 (inclusive) and 120 (inclusive)
		int randomMinutes = 15 + random.nextInt(106);
		// Multiply with MINUTES_IN_STEPS
		timeAtDestination = (randomMinutes * TimePars.MINUTE_TO_STEPS) + steps;
	}

	/**
	 * The agent goes home after reaching its destination.
	 */
	protected void goHome() {

		state.agentsWalking.add(this);
		status = AgentStatus.GOING_HOME;
		planNewTrip();
	}

	/**
	 * Updates the agent's status in the agent lists.
	 *
	 * @param isWalking   indicates whether the agent is walking or not.
	 * @param reachedHome indicates whether the agent has reached home.
	 */
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
	 * @throws Exception if the route cannot be planned.
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
	 * Gets the geometry representing the agent's location.
	 *
	 * @return The geometry representing the agent's location.
	 */
	public MasonGeometry getLocation() {
		return currentLocation;
	}

	/**
	 * Gets the agent's properties.
	 *
	 * @return The agent's properties.
	 */
	public AgentProperties getProperties() {
		return agentProperties;
	}

	/**
	 * Gets the agent's cognitive map.
	 *
	 * @return The cognitive map.
	 */
	public CognitiveMap getCognitiveMap() {
		return cognitiveMap;
	}

	/**
	 * Checks if the agent is waiting.
	 *
	 * @return true if the agent is waiting, false otherwise.
	 */
	private boolean isWaiting() {
		return status.equals(AgentStatus.WAITING);
	}

	/**
	 * Checks if the agent is walking alone.
	 *
	 * @return true if the agent is walking alone, false otherwise.
	 */
	public boolean isWalkingAlone() {
		return status.equals(AgentStatus.WALKING_ALONE);
	}

	/**
	 * Checks if the agent is going home.
	 *
	 * @return true if the agent is going home, false otherwise.
	 */
	public boolean isGoingHome() {
		return status.equals(AgentStatus.GOING_HOME);
	}

	/**
	 * Checks if the agent is at its destination.
	 *
	 * @return true if the agent is at its destination, false otherwise.
	 */
	private boolean isAtDestination() {
		return status.equals(AgentStatus.AT_DESTINATION) || status.equals(AgentStatus.AT_GROUP_DESTINATION);
	}

	/**
	 * Gets the total distance the agent has walked.
	 *
	 * @return The total distance the agent has walked in kilometers.
	 */
	public double getTotalKmWalked() {
		return kmWalkedTot;
	}

	/**
	 * Gets the distance the agent has walked in the current day.
	 *
	 * @return The distance walked by the agent today in kilometers.
	 */
	public double getKmWalkedDay() {
		return kmWalkedDay;
	}

	/**
	 * Sets the distance to the next destination for the agent.
	 *
	 * @param distanceNextDestination The distance to the next destination.
	 */
	public void setDistanceNextDestination(double distanceNextDestination) {
		this.distanceNextDestination = distanceNextDestination;
	}

	/**
	 * Gets the simulation state of the agent.
	 *
	 * @return The PedSimCity simulation state.
	 */
	public PedSimCity getState() {
		return state;
	}

	/**
	 * Checks if the agent is vulnerable.
	 *
	 * @return true if the agent is vulnerable, false otherwise.
	 */
	public boolean isVulnerable() {
		return vulnerable.equals(Vulnerable.VULNERABLE);
	}
}