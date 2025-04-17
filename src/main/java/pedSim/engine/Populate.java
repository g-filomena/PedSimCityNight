package pedSim.engine;

import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import pedSim.agents.Agent;
import pedSim.parameters.Pars;
import pedSim.utilities.LoggerUtil;
import pedSim.utilities.StringEnum.Vulnerable;

/**
 * The Populate class is responsible for generating agents, building the OD
 * matrix, and populating empirical groups for pedestria the simulation.
 */
public class Populate {

	private PedSimCity state;
	final String TYPE_LIVE = "live";
	private static final Logger logger = LoggerUtil.getLogger();

	/**
	 * Populates agents, OD matrix, for the simulation. It creates a set of agents
	 * with a randomly assigned vulnerability status and updates their cognitive
	 * maps. The agents are then added to the simulation state.
	 *
	 * @param state The PedSimCity simulation state.
	 */
	public void populate(PedSimCity state) {

		this.state = state;

		// Create agents with parameter true
		int totalAgents = Pars.numAgents;
		logger.info("Creating " + totalAgents + " Agents. Building Their Cognitive Maps");
		IntStream.range(0, totalAgents).parallel().forEach(agentID -> {
			addAgent(agentID); // Must be thread-safe!
		});

		for (Agent agent : state.agentsList)
			state.agents.addGeometry(agent.getLocation());
		logger.info(state.agentsList.size() + " agents created");
	}

	/**
	 * Adds a new agent to the simulation with a randomly assigned vulnerability
	 * status. The agent is added to the list of agents and its cognitive map is
	 * initialized.
	 *
	 * @param agentID The identifier of the agent to be added.
	 */
	private void addAgent(int agentID) {

		Agent agent = new Agent(this.state);
		agent.agentID = agentID;
		agent.vulnerable = assignRandomVulernability();
		state.agentsList.add(agent);
		agent.updateAgentLists(false, true);
	}

	/**
	 * Assigns a random vulnerability status (either vulnerable or non-vulnerable)
	 * to an agent with a 55% chance of being vulnerable.
	 *
	 * @return A randomly assigned vulnerability status.
	 */
	public static Vulnerable assignRandomVulernability() {
		double p = new Random().nextDouble();
		return p < 0.55 ? Vulnerable.VULNERABLE : Vulnerable.NON_VULNERABLE;
	}
}
