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

import pedSim.agents.Agent;
import pedSim.parameters.RouteChoicePars;
import pedSim.parameters.TimePars;
import pedSim.utilities.LoggerUtil;
import sim.util.geo.Utilities;

public class AgentReleaseManager {

	private static final Logger logger = LoggerUtil.getLogger();

	private LocalDateTime currentTime;
	Random random = new Random();

	private PedSimCity state;
	private double kmCurrentDay;
	private double expectedKmWalkedSoFarToday;
	private double kmWalkedSoFarToday;

	public AgentReleaseManager(PedSimCity state, Double kmCurrentDay) {
		this.state = state;
		this.kmCurrentDay = kmCurrentDay;
		System.out.println("kmExpectedTowalk day : " + kmCurrentDay / 1000);
		resetKmWalkedSoFar();
		expectedKmWalkedSoFarToday = 0.0;
		kmWalkedSoFarToday = 0.0;
	}

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

	private void releaseAgentsKm(double kmToAllocate) {

		int agentsExpectedToWalk = Math.max(1, (int) (kmToAllocate / RouteChoicePars.avgTripDistance)); // Ensure at

		Set<Agent> agentsAtHome = new HashSet<>(state.agentsAtHome);// least one
		// agent
		Set<Agent> agentsToRelease = selectRandomAgents(agentsAtHome, agentsExpectedToWalk);
		allocateKmAcrossAgents(agentsToRelease, kmToAllocate); // Allocate km accordingly

		for (Agent agent : agentsToRelease)
			agent.nextActivity();
	}

	// Uncomment and adapt if the time-based logging is needed
	private void logWalkingAgents() {
		logger.info(
				String.format("TIME: %02d:%02d | Agents walking: %d | Expected Km: %.1f vs Walked Km: %.1f | Night: %s",
						currentTime.getHour(), currentTime.getMinute(), state.agentsWalking.size(),
						expectedKmWalkedSoFarToday / 1000.0, kmWalkedSoFarToday / 1000.0, state.isDark ? "Yes" : "No"));
	}

	private void allocateKmAcrossAgents(Set<Agent> agentSet, Double kmToAllocate) {

		// Calculate the base km per agent for this step interval
		double baseKmPerAgent = kmToAllocate / agentSet.size();

		// Apply randomisation for variability (+/- 30%)
		for (Agent agent : agentSet) {
			double variabilityFactor = Utilities.fromDistribution(0.70, 0.30, null); // 0.70 as more will be walked
			double kmToWalk = baseKmPerAgent * variabilityFactor;
			if (kmToWalk < RouteChoicePars.minTripDistance)
				kmToWalk = RouteChoicePars.minTripDistance;
			else if (kmToWalk > RouteChoicePars.maxTripDistance)
				kmToWalk = RouteChoicePars.maxTripDistance;

			// this guide the selection of the destination for the agent, it is divided by
			// two as the agent will go back home

			agent.setDistanceNextDestination(kmToWalk);
			kmToAllocate -= kmToWalk;
			if (kmToAllocate < RouteChoicePars.minTripDistance)
				break;
		}
	}

	private Set<Agent> selectRandomAgents(Set<Agent> homeAgents, int nrAgents) {

		if (nrAgents >= homeAgents.size())
			return homeAgents;

		List<Agent> agents = new ArrayList<>(homeAgents);
		// Sort agents by kmWalked in ascending order (less walked first)
		agents.sort(Comparator.comparingDouble(Agent::getTotalKmWalked));

		Set<Agent> selectedAgents = new HashSet<>();
		for (int i = 0; i < nrAgents; i++) {
			// Weighted selection: lower kmWalked has a higher probability
			int weightedIndex = (int) (Math.pow(random.nextDouble(), 1.5) * agents.size());
			selectedAgents.add(agents.get(weightedIndex));
		}

		return selectedAgents;
	}

	private double computeKmWalkedSoFar() {
		return state.agentsList.stream().mapToDouble(Agent::getKmWalkedDay).sum(); // Ensure the sum is computed and
																					// returned
	}

	private void resetKmWalkedSoFar() {
		// Reset kmWalkedDay for all agents
		state.agentsList.forEach(agent -> agent.kmWalkedDay = 0.0);
	}

	private boolean isNight() {
		LocalTime now = currentTime.toLocalTime();
		return now.isAfter(LocalTime.of(18, 0)) || (now.isAfter(LocalTime.MIDNIGHT) && now.isBefore(TimePars.nightEnd));
	}

}
