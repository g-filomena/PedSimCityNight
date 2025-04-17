package pedSim.engine;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import pedSim.agents.Agent;
import pedSim.parameters.RouteChoicePars;
import pedSim.parameters.TimePars;
import pedSim.utilities.LoggerUtil;
import sim.util.geo.Utilities;

/**
 * The AgentReleaseManager class handles the release of agents for the
 * pedestrian simulation, distributing the total expected walking distance for
 * agents during a given time period.
 */
public class AgentReleaseManager {

	private static final Logger logger = LoggerUtil.getLogger();

	private LocalDateTime currentTime;
	Random random = new Random();

	private PedSimCity state;
	private double kmCurrentDay;
	private double expectedKmWalkedSoFarToday;
	private double kmWalkedSoFarToday;

	/**
	 * Constructor for AgentReleaseManager.
	 * 
	 * @param state        the PedSimCity instance representing the simulation
	 *                     state.
	 * @param kmCurrentDay the current expected walking distance for the day (in
	 *                     meters).
	 */
	public AgentReleaseManager(PedSimCity state, Double kmCurrentDay) {
		this.state = state;
		this.kmCurrentDay = kmCurrentDay;
		System.out.println("kmExpectedTowalk day : " + kmCurrentDay / 1000);
		resetKmWalkedSoFar();
		expectedKmWalkedSoFarToday = 0.0;
		kmWalkedSoFarToday = 0.0;
	}

	/**
	 * Releases agents to start walking based on the calculated walking distances
	 * for the day.
	 * 
	 * @param steps the current simulation step count.
	 */
	public void releaseAgents(double steps) {

		currentTime = TimePars.getTime(steps);
		kmWalkedSoFarToday = computeKmWalkedSoFar();
		if (isNight())
			state.isDark = true;
		else
			state.isDark = false;

		double kmToAllocate = (kmCurrentDay * TimePars.computeTimeStepShare(currentTime));
		double kmAdjusted = (kmToAllocate + (expectedKmWalkedSoFarToday - kmWalkedSoFarToday)) * 0.5; // to account for
																										// return home

		if (kmAdjusted > 0)
			releaseAgentsKm(kmAdjusted);
		if (currentTime.getMinute() == 0) // Log walking agents every full hour
			logWalkingAgents();
		expectedKmWalkedSoFarToday += kmToAllocate;
	}

	/**
	 * Releases a set of agents to walk a specific distance, based on the kilometers
	 * to allocate. The number of agents to release is calculated based on the
	 * expected distance and the average trip distance. After selecting the agents,
	 * the distance is allocated to them and their activities are updated.
	 * 
	 * @param kmToAllocate the total kilometres to be allocated for the selected
	 *                     agents to walk.
	 */
	private void releaseAgentsKm(double kmToAllocate) {

		int agentsExpectedToWalk = Math.max(1, (int) (kmToAllocate / RouteChoicePars.avgTripDistance));

		Set<Agent> agentsAtHome = new HashSet<>(state.agentsAtHome);// least one
		// agent
		Set<Agent> agentsToRelease = selectRandomAgents(agentsAtHome, agentsExpectedToWalk);
		allocateKmAcrossAgents(agentsToRelease, kmToAllocate); // Allocate km accordingly

		for (Agent agent : agentsToRelease)
			agent.nextActivity();
	}

	/**
	 * Logs the current walking agent statistics, including the number of agents
	 * walking, expected versus walked kilometers, and whether it is night or not.
	 */
	private void logWalkingAgents() {
		logger.info(
				String.format("TIME: %02d:%02d | Agents walking: %d | Expected Km: %.1f vs Walked Km: %.1f | Night: %s",
						currentTime.getHour(), currentTime.getMinute(), state.agentsWalking.size(),
						expectedKmWalkedSoFarToday / 1000.0, kmWalkedSoFarToday / 1000.0, state.isDark ? "Yes" : "No"));
	}

	/**
	 * Allocates the specified walking distance across a set of agents using
	 * parallel processing. Each agent gets a random variability applied to the
	 * allocated distance, ensuring they stay within defined minimum and maximum
	 * limits.
	 * 
	 * @param agentSet     the set of agents to which the distance will be
	 *                     allocated.
	 * @param kmToAllocate the total kilometers to be allocated to agents.
	 */
	private void allocateKmAcrossAgents(Set<Agent> agentSet, Double kmToAllocate) {

		// Apply randomisation for variability (+/- 30%) using parallelStream
		agentSet.parallelStream().forEach(agent -> {
			double variabilityFactor = Utilities.fromDistribution(1.00, 0.30, null); // Variability (+/- 30%)
			double kmToWalk = RouteChoicePars.avgTripDistance * variabilityFactor;

			// Ensure kmToWalk is within the defined boundaries
			if (kmToWalk < RouteChoicePars.minTripDistance)
				kmToWalk = RouteChoicePars.minTripDistance;
			else if (kmToWalk > RouteChoicePars.maxTripDistance)
				kmToWalk = RouteChoicePars.maxTripDistance;

			// This guides the selection of the destination for the agent, divided by two as
			// the agent will go back home
			agent.setDistanceNextDestination(kmToWalk);
		});
	}

	/**
	 * Selects a specified number of agents randomly, with a weighted probability
	 * towards agents that have walked less distance. The selection is done using
	 * parallel streams to improve performance.
	 * 
	 * @param homeAgents the set of home agents to select from.
	 * @param nrAgents   the number of agents to select.
	 * @return a set of randomly selected agents.
	 */
	private Set<Agent> selectRandomAgents(Set<Agent> homeAgents, int nrAgents) {

		if (nrAgents >= homeAgents.size())
			return homeAgents;

		List<Agent> agents = new ArrayList<>(homeAgents);
		// Sort agents by kmWalked in ascending order (less walked first) using parallel
		// sort
		agents.parallelStream().sorted(Comparator.comparingDouble(Agent::getTotalKmWalked))
				.collect(Collectors.toList());

		Set<Agent> selectedAgents = agents.parallelStream().limit(nrAgents) // Select only the first 'nrAgents' after
																			// sorting
				.map(agent -> {
					// Weighted selection: lower km-Walked has a higher probability
					int weightedIndex = (int) (Math.pow(random.nextDouble(), 1.5) * agents.size());
					return agents.get(weightedIndex);
				}).collect(Collectors.toSet());

		return selectedAgents;
	}

	/**
	 * Computes the total kilometers walked by all agents in the simulation up to
	 * the current time.
	 * 
	 * @return the total kilometers walked by all agents.
	 */
	private double computeKmWalkedSoFar() {
		return state.agentsList.stream().mapToDouble(Agent::getKmWalkedDay).sum();
	}

	/**
	 * Resets the kmWalkedDay attribute for all agents in the simulation to zero.
	 */
	private void resetKmWalkedSoFar() {
		state.agentsList.forEach(agent -> agent.kmWalkedDay = 0.0);
	}

	/**
	 * Checks if the current time is considered "night" in the simulation.
	 * 
	 * @return true if the current time is night, false otherwise.
	 */
	private boolean isNight() {
		LocalTime now = currentTime.toLocalTime();
		return now.isAfter(LocalTime.of(18, 0)) || (now.isAfter(LocalTime.MIDNIGHT) && now.isBefore(TimePars.nightEnd));
	}

}
