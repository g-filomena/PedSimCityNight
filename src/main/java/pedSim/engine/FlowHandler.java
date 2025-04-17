package pedSim.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import pedSim.agents.Agent;
import pedSim.utilities.RouteData;
import pedSim.utilities.StringEnum.TimeOfDay;
import sim.graph.EdgeGraph;
import sim.graph.GraphUtils;
import sim.routing.Route;

/**
 * The Flow class provides methods for updating various data related to agent
 * movement and route storing in the simulation.
 */
public class FlowHandler {

	public Map<Integer, Map<String, Integer>> volumesMap = new HashMap<Integer, Map<String, Integer>>();
	public List<RouteData> routesData = new ArrayList<>();
	public int job;

	/**
	 * Constructor for the FlowHandler class.
	 *
	 * @param job   The job ID for the simulation.
	 * @param state The current simulation state.
	 */
	public FlowHandler(int job, PedSimCity state) {
		initializeEdgeVolumes();
		this.job = job;
	}

	/**
	 * Initialises the edge volumes for the simulation. This method assigns initial
	 * volume values to edges based on the selected route choice models or empirical
	 * agent groups. If the simulation is not empirical, it initialises volumes
	 * based on the route choice models. If the simulation is empirical-based, it
	 * initialises volumes based on empirical agent groups.
	 */
	private void initializeEdgeVolumes() {

		TimeOfDay[] timeOfDay = TimeOfDay.values();
		for (int edgeID : PedSimCity.edgesMap.keySet()) {
			PedSimCity.edgesMap.get(edgeID).resetAgentCount();
			Map<String, Integer> edgeVolumes = Arrays.stream(timeOfDay)
					.collect(Collectors.toMap(TimeOfDay::toString, time -> 0)); // No duplicates
			volumesMap.put(edgeID, edgeVolumes);
		}
		routesData.clear();
	}

	/**
	 * Updates the edge data on the basis of the passed agent's route and its edges
	 * sequence.
	 *
	 * @param agent The agent for which edge data is updated.
	 * @param route The route taken by the agent.
	 * @param night Boolean flag indicating whether it is night or day.
	 */
	public synchronized void updateFlowsData(Agent agent, Route route, boolean night) {

		String attribute = TimeOfDay.DAY.toString();
		if (night)
			attribute = TimeOfDay.NIGHT.toString();

		RouteData routeData = createRouteData(agent, route, attribute);
		for (EdgeGraph edgeGraph : route.edgesSequence) {
			Map<String, Integer> edgeVolume = volumesMap.get(edgeGraph.getID());
			edgeVolume.replace(attribute, edgeVolume.get(attribute) + 1);
			volumesMap.replace(edgeGraph.getID(), edgeVolume);
		}
		routeData.edgeIDsSequence = GraphUtils.getEdgeIDs(route.edgesSequence);
		routesData.add(routeData);
	}

	/**
	 * Exports the flows data for the specified day.
	 *
	 * @param day The day for which the flow data should be exported.
	 * @throws Exception if there is an error during the export process.
	 */
	public void exportFlowsData(int day) throws Exception {
		Exporter exporter = new Exporter(this);
		exporter.savePedestrianVolumes(day);
		exporter.saveRoutes(day);
		initializeEdgeVolumes();
	}

	/**
	 * Creates and initialises a new RouteData object for the given agent.
	 *
	 * @param agent     The agent for which route data is created.
	 * @param route     The route taken by the agent.
	 * @param attribute The time attribute (day or night) for the route.
	 * @return A RouteData object containing route information.
	 */
	private RouteData createRouteData(Agent agent, Route route, String attribute) {
		RouteData routeData = new RouteData();
		routeData.origin = agent.originNode.getID();
		routeData.destination = agent.destinationNode.getID();
		routeData.lineGeometry = route.getLineString();
		routeData.scenario = attribute;
		return routeData;
	}
}
