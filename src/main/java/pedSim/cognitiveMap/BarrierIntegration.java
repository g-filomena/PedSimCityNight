package pedSim.cognitiveMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sim.graph.EdgeGraph;
import sim.util.geo.AttributeValue;

public class BarrierIntegration {

	/**
	 * Sets the barrier information for an EdgeGraph based on attribute values. This method parses attribute strings
	 * representing different types of barriers, such as positive barriers, negative barriers, rivers, and parks, and
	 * populates the corresponding lists in the EdgeGraph. The method retrieves attribute values for positive barriers
	 * ("p_barr"), negative barriers ("n_barr"), rivers ("a_rivers"), and parks ("w_parks") from the EdgeGraph's
	 * attributes. It parses these strings to extract barrier IDs and adds them to the appropriate lists:
	 * positiveBarriers, negativeBarriers, waterBodies, and parks. Additionally, it combines positive and negative
	 * barriers into the 'barriers' list for convenient access.
	 *
	 * @param edge The EdgeGraph for which barrier information is being set.
	 */
	public static void setEdgeGraphBarriers(EdgeGraph edge) {

		List<Integer> positiveBarriers = new ArrayList<>();
		List<Integer> negativeBarriers = new ArrayList<>();
		List<Integer> barriers = new ArrayList<>(); // all the barriers
		List<Integer> waterBodies = new ArrayList<>();
		List<Integer> parks = new ArrayList<>();
		String pBarriersString = edge.attributes.get("p_barr").getString();
		String nBarriersString = edge.attributes.get("n_barr").getString();
		String riversString = edge.attributes.get("a_rivers").getString();
		String parksString = edge.attributes.get("w_parks").getString();

		if (!pBarriersString.equals("[]")) {
			String p = pBarriersString.replaceAll("[^-?0-9]+", " ");
			for (String t : Arrays.asList(p.trim().split(" ")))
				positiveBarriers.add(Integer.valueOf(t));
		}
		edge.attributes.put("positiveBarriers", new AttributeValue(positiveBarriers));

		if (!nBarriersString.equals("[]")) {
			String n = nBarriersString.replaceAll("[^-?0-9]+", " ");
			for (String t : Arrays.asList(n.trim().split(" ")))
				negativeBarriers.add(Integer.valueOf(t));
		}
		edge.attributes.put("negativeBarriers", new AttributeValue(negativeBarriers));

		if (!riversString.equals("[]")) {
			String r = riversString.replaceAll("[^-?0-9]+", " ");
			for (String t : Arrays.asList(r.trim().split(" ")))
				waterBodies.add(Integer.valueOf(t));
		}
		edge.attributes.put("waterBodies", new AttributeValue(waterBodies));
		if (!waterBodies.isEmpty())
			CommunityCognitiveMap.edgesAlongWater.add(edge);

		if (!parksString.equals("[]")) {
			final String p = parksString.replaceAll("[^-?0-9]+", " ");
			for (final String t : Arrays.asList(p.trim().split(" ")))
				parks.add(Integer.valueOf(t));
		}
		edge.attributes.put("parks", new AttributeValue(parks));
		if (!parks.isEmpty())
			CommunityCognitiveMap.edgesWithinParks.add(edge);

		barriers.addAll(positiveBarriers);
		barriers.addAll(negativeBarriers);
		edge.attributes.put("barriers", new AttributeValue(barriers));
	}

	/**
	 * It stores information about the barriers within a given SubGraph.
	 *
	 * @param region The Region for which the barrier information is being set.
	 */
	public static void setRegionBarriers(Region region) {

		for (EdgeGraph childEdge : region.primalGraph.getEdges())
			region.barriers.addAll(region.primalGraph.getParentEdge(childEdge).attributes.get("barriers").getArray());
	}
}
