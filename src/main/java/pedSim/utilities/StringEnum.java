package pedSim.utilities;

public class StringEnum {

	public enum RouteChoiceProperty {

		ROAD_DISTANCE
	}

	public enum AgentStatus {
		WALKING_ALONE, WAITING, GOING_HOME, AT_DESTINATION, AT_GROUP_DESTINATION
	}

	public enum LandmarkType {
		LOCAL, GLOBAL
	}

	public enum BarrierType {
		ALL, POSITIVE, NEGATIVE, SEPARATING
	}

	public enum RoadType {
		PRIMARY, SECONDARY, TERTIARY, NEIGHBOURHOOD, UNKNOWN
	}

	public enum Gender {
		MALE, FEMALE, NON_BINARY
	}

	public enum TimeOfDay {
		DAY, NIGHT
	}
}
