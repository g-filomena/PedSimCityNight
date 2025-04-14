package pedSim.engine;

import java.util.logging.Logger;

import pedSim.parameters.Pars;
import pedSim.parameters.TimePars;
import pedSim.utilities.LoggerUtil;
import sim.util.geo.Utilities;

public class Engine {

	PedSimCity state;
	private double kmCurrentDay;
	AgentReleaseManager currentDayReleaseManager;
	private int currentDay;

	private static final Logger logger = LoggerUtil.getLogger();

	public Engine() {

	}

	public void executeJob(int job) throws Exception {

		currentDay = 0;
		state = new PedSimCity(System.currentTimeMillis(), job);
		state.start();
		handleNewWeek();

		double nextAgentRelease = 1.0;

		while (continueSimulation()) {
			double steps = state.schedule.getSteps();

			if (isNextDay(steps, currentDay)) {
				state.flowHandler.exportFlowsData(currentDay + 1);
				currentDay++;
				if (currentDay % 6 == 0)
					handleEndWeek(state);
				else
					handleNewDay();
			}

			if (nextAgentRelease == steps) {
				currentDayReleaseManager.releaseAgents(steps);
				nextAgentRelease += TimePars.releaseAgentsEverySteps;
			}
		}
		state.flowHandler.exportFlowsData(currentDay + 1);
		state.finish();
	}

	private boolean continueSimulation() {
		return state.schedule.step(state) && (state.schedule.getSteps() <= TimePars.simulationDurationInSteps);
	}

	private boolean isNextDay(double steps, int currentDay) {
		return getDays(steps) > currentDay && (currentDay + 1 < TimePars.numberOfDays);
	}

	public static long getDays(double totalSteps) {

		long totalMinutes = (long) (totalSteps * (TimePars.STEP_DURATION / 60)); // Convert steps to minutes based on
																					// // the stepTimeUnit
		return totalMinutes / (24 * 60); // Calculate days
	}

	private void handleNewWeek() {

		handleNewDay();
	}

	private void handleNewDay() {
		kmCurrentDay = calculateKmCurrentDay();
		logger.info("---------- Beginning day Nr " + (currentDay + 1));
		currentDayReleaseManager = new AgentReleaseManager(state, kmCurrentDay);
	}

	private void handleEndWeek(PedSimCity state) {

//		for (Agent agent : state.agentsList) {
//
////			if (agent.isLearner())
////				agent.learning.memoryDecay();
//		}
	}

	private double calculateKmCurrentDay() {
		return Pars.getKmPerDay() * Utilities.fromDistribution(1.0, 0.10, null);
	}

}
