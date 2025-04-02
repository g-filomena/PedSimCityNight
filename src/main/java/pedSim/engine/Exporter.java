package pedSim.engine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.ArrayUtils;

import pedSim.utilities.LoggerUtil;
import pedSim.utilities.RouteData;
import pedSim.utilities.StringEnum.TimeOfDay;
import sim.field.geo.VectorLayer;
import sim.io.geo.ShapeFileExporter;
import sim.util.geo.CSVUtils;
import sim.util.geo.MasonGeometry;

/**
 * The Export class is responsible for saving the simulation results to
 * specified output directories.
 */
public class Exporter {

	private String userName = System.getProperty("user.name");
	// Constants for file paths and directories
	public String outputDirectory;
	public String outputRoutesDirectory;
	public String outputVolumesDirectory;
	public String outputCognitiveMapDirectory;

	private static final Logger logger = LoggerUtil.getLogger();
	private static int nrColumns;
	private static final int FIELD_LIMIT = 254;
	int job;
	FlowHandler flowHandler;
	private String currentDate;

	public Exporter(FlowHandler flowHandler) {
		this.flowHandler = flowHandler;
		this.job = flowHandler.job;
		outputDirectory = "C:" + File.separator + "Users" + File.separator + userName + File.separator
				+ "PedSimCityNight" + File.separator + "Output";
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		currentDate = LocalDate.now().format(formatter);
	}

	/**
	 * Saves pedestrian volumes data to a CSV file.
	 *
	 * @param job The job identifier.
	 * @throws Exception If there is an error while saving the data.
	 */
	public void savePedestrianVolumes(int day) throws Exception {

		outputVolumesDirectory = verifyOutputPath(outputVolumesDirectory, "streetVolumes");
		outputVolumesDirectory += File.separator + currentDate + "_" + job + "_" + day + ".csv";
		final FileWriter writerVolumesData = new FileWriter(outputVolumesDirectory);

		Map<Integer, Map<String, Integer>> volumesMap = new HashMap<>(flowHandler.volumesMap);
		List<String> headers = new ArrayList<>();
		headers.add("edgeID"); // Directly adding "edgeID" without specifying index

		// Adding scenario names to headers
		for (TimeOfDay timeOfday : TimeOfDay.values())
			headers.add(timeOfday.toString());

		// Writing headers to CSV
		CSVUtils.writeLine(writerVolumesData, headers);

		// Iterating through each edgeID and writing corresponding volumes
		for (Map.Entry<Integer, Map<String, Integer>> entry : volumesMap.entrySet()) {
			int edgeID = entry.getKey();
			Map<String, Integer> edgeVolumes = entry.getValue();
			List<String> row = new ArrayList<>();

			// Adding edgeID as the first column value
			row.add(Integer.toString(edgeID));

			// Adding volumes for each scenario
			for (String columnHeader : headers.subList(1, headers.size())) { // Skip "edgeID" header
				Integer volume = edgeVolumes.get(columnHeader);
				row.add(volume != null ? Integer.toString(volume) : "0"); // Handle potential null values
			}

			CSVUtils.writeLine(writerVolumesData, row);
		}
		writerVolumesData.flush();
		writerVolumesData.close();
		logger.info("Day nr " + day + ": Pedestrian volumes successfully exported.");
	}

	/**
	 * Saves pedestrian volumes data to a CSV file.
	 *
	 * @param job The job identifier.
	 * @throws Exception If there is an error while saving the data.
	 */
	public void saveRoutes(int day) throws Exception {

		outputRoutesDirectory = verifyOutputPath(outputRoutesDirectory, "routes");
		outputRoutesDirectory += File.separator + currentDate + "_" + job + "_" + day;
		VectorLayer routes = new VectorLayer();
		nrColumns = 0;

		for (final RouteData routeData : flowHandler.routesData) {
			MasonGeometry masonGeometry = new MasonGeometry(routeData.lineGeometry);
			masonGeometry.addIntegerAttribute("O", routeData.origin);
			masonGeometry.addIntegerAttribute("D", routeData.destination);
			masonGeometry.addAttribute("scenario", routeData.scenario);
			formRouteAttributes(masonGeometry, routeData);
			routes.addGeometry(masonGeometry);
		}

		// Avoid geometries without needed columns' values filled in.
		if (nrColumns > 0) {
			for (int counter = 1; counter < nrColumns; counter++) {
				List<MasonGeometry> routeGeometries = routes.getGeometries();
				for (MasonGeometry route : routeGeometries)
					if (!route.hasAttribute("edgeIDs_" + counter))
						route.addAttribute("edgeIDs_" + counter, "None");
			}
		}
		ShapeFileExporter.write(outputRoutesDirectory, routes);
	}

	/**
	 * Verifies and creates the specified output directory.
	 * 
	 * @param directory The directory path to be created.
	 * @return
	 */
	private String verifyOutputPath(String directory, String specifier) {

		directory = outputDirectory + File.separator + specifier;
		directory = String.format(directory, userName);

		File outputCheck = new File(directory);
		if (!outputCheck.exists()) {
			try {
				// Create the output path directory and its parent directories recursively
				Files.createDirectories(Paths.get(directory));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return directory;
	}

	/**
	 * Forms route attributes and handles splitting long edgeIDs strings.
	 *
	 * @param masonGeometry The MasonGeometry object representing a route.
	 * @param routeData     The route data associated with the route.
	 */
	private static void formRouteAttributes(MasonGeometry masonGeometry, RouteData routeData) {
		String edgeIDs = ArrayUtils.toString(routeData.edgeIDsSequence);

		if (edgeIDs.length() <= FIELD_LIMIT)
			masonGeometry.addAttribute("edgeIDs_0", edgeIDs);
		else {
			String remainingEdges = edgeIDs;
			for (int counter = 0; remainingEdges.length() > 0; counter++) {
				if (counter >= nrColumns)
					nrColumns += 1;

				String currentPart;
				if (remainingEdges.length() > FIELD_LIMIT) {
					currentPart = remainingEdges.substring(0, FIELD_LIMIT);
					remainingEdges = remainingEdges.substring(FIELD_LIMIT);
				} else {
					currentPart = remainingEdges;
					remainingEdges = "";
				}
				masonGeometry.addAttribute("edgeIDs_" + counter, currentPart);
			}
		}
	}
}
