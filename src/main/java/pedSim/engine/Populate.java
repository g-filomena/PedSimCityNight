package pedSim.engine;

import java.util.Random;
import java.util.logging.Logger;

import pedSim.agents.Agent;
import pedSim.parameters.Pars;
import pedSim.utilities.LoggerUtil;
import pedSim.utilities.StringEnum.Gender;

/**
 * The Populate class is responsible for generating test agents, building the OD
 * matrix, and populating empirical groups for pedestrian simulation.
 */
public class Populate {

	private PedSimCity state;
	final String TYPE_LIVE = "live";
	private static final Logger logger = LoggerUtil.getLogger();

	/**
	 * Populates test agents, OD matrix, and empirical groups for pedestrian
	 * simulation.
	 *
	 * @param state The PedSimCity simulation state.
	 */
	public void populate(PedSimCity state) {

		logger.info("Creating Agents. Building Their Cognitive Maps");
		this.state = state;

		// Create agents with parameter true
		int totalAgents = Pars.numAgents;

		for (int agentID = 0; agentID < totalAgents; agentID++) {
			addAgent(agentID);
		}

		logger.info("Agents created");
	}

	/**
	 * Adds an agent to the simulation.
	 *
	 * @param agent        The agent to be added.
	 * @param agentID      The identifier of the agent.
	 * @param thisAgentODs The OD matrix for this agent.
	 */
	private void addAgent(int agentID) {

		Agent agent = new Agent(this.state);
//		MasonGeometry agentGeometry = agent.getGeometry();
//		agentGeometry.isMovable = true;
		agent.agentID = agentID;
		agent.gender = assignRandomGender();
		state.agents.addGeometry(agent.getLocation());
		state.agentsList.add(agent);
		agent.updateAgentLists(false, true);
	}

	public static Gender assignRandomGender() {
		double p = new Random().nextDouble();
		return p < 0.45 ? Gender.FEMALE : (p < 0.95 ? Gender.MALE : Gender.NON_BINARY);
	}

}
