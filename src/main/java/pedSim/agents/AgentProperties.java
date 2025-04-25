package pedSim.agents;

import java.util.Random;

/**
 * `EmpiricalAgentProperties` is a subclass of `AgentProperties` that represents the properties of an agent in a
 * pedestrian simulation with empirical-based parameters. It extends the base `AgentProperties` class to incorporate
 * additional parameters.
 */
public class AgentProperties {

	public Agent agent;

	// for general routing
	public boolean onlyMinimising = true;
	public boolean minimisingDistance = true;
	final Random random = new Random();

	/**
	 * Sets the route choice for the agent and updates related properties accordingly.
	 *
	 * @param agent The agent to which the properties are attached.
	 */
	public AgentProperties(Agent agent) {
		this.agent = agent;
	}

	public AgentProperties() {
	}

	/**
	 * Checks if the agent should use minimization for route planning.
	 *
	 * @return True if the agent should use minimization, otherwise false.
	 */
	public boolean shouldOnlyUseMinimization() {
		return minimisingDistance;
	}

}