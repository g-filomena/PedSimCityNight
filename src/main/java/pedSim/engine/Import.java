package pedSim.engine;

import java.io.File;
import java.net.URL;
import java.util.logging.Logger;

import pedSim.parameters.Pars;
import pedSim.utilities.LoggerUtil;
import sim.field.geo.VectorLayer;

/**
 * This class is responsible for importing various data files required for the
 * simulation based on the selected simulation parameters. It includes methods
 * for importing distances, barriers, landmarks and sight lines, road network
 * graphs, and empirical agent groups data.
 */
public class Import {

	/**
	 * The base data directory path for the simulation data files.
	 */
	String resourcePath;
	private static final Logger logger = LoggerUtil.getLogger();
	ClassLoader CLASSLOADER = getClass().getClassLoader();

	/**
	 * Imports various data files required for the simulation based on the selected
	 * simulation parameters.
	 *
	 * @throws Exception If an error occurs during the import process.
	 */
	public void importFiles() throws Exception {
		resourcePath = Pars.cityName;
		if (Pars.javaProject)
			resourcePath = Pars.localPath + resourcePath;

		readLandmarksAndSightLines();
		readBarriers();
		// Read the street network shapefiles and create the primal and the dual graph
		readGraphs();
	}

	/**
	 * Reads and imports road network graphs required for the simulation.
	 *
	 * @throws Exception If an error occurs during the import process.
	 */
	private void readGraphs() throws Exception {

		try {
			String[] layerSuffixes = { "/edges", "/nodes", "/edgesDual", "/nodesDual" };
			VectorLayer[] vectorLayers = { PedSimCity.roads, PedSimCity.junctions, PedSimCity.intersectionsDual,
					PedSimCity.centroids };

			for (int i = 0; i < layerSuffixes.length; i++) {
				String filePath = resourcePath + layerSuffixes[i];
				URL fileUrl = Pars.javaProject ? new File(filePath + ".gpkg").toURI().toURL()
						: CLASSLOADER.getResource(filePath + ".gpkg");
				VectorLayer.readGPKG(fileUrl, vectorLayers[i]);

			}

			PedSimCity.network.fromStreetJunctionsSegments(PedSimCity.junctions, PedSimCity.roads);
			PedSimCity.dualNetwork.fromStreetJunctionsSegments(PedSimCity.centroids, PedSimCity.intersectionsDual);
			logger.info("Graphs successfully imported.");
		} catch (Exception e) {
			handleImportError("Importing Graphs failed", e);
		}
	}

	/**
	 * Reads and imports landmarks and sight lines data for the simulation.
	 *
	 * @throws Exception If an error occurs during the import process.
	 */
	private void readLandmarksAndSightLines() throws Exception {
		try {
			String[] layerSuffixes = { "/landmarks" };
			VectorLayer[] vectorLayers = { PedSimCity.buildings };

			for (int i = 0; i < layerSuffixes.length; i++) {
				String filePath = resourcePath + layerSuffixes[i];
				URL fileUrl = Pars.javaProject ? new File(filePath + ".gpkg").toURI().toURL()
						: CLASSLOADER.getResource(filePath + ".gpkg");
				VectorLayer.readGPKG(fileUrl, vectorLayers[i]);
			}

			PedSimCity.buildings.setID("buildingID");
			logger.info("Landmarks and sight lines successfully imported.");
		} catch (Exception e) {
			handleImportError("Importing Landmarks and Sight Lines Failed", e);
		}
	}

	/**
	 * Reads and imports barriers data for the simulation.
	 *
	 * @throws Exception If an error occurs during the import process.
	 */
	private void readBarriers() throws Exception {
		try {
			String filePath = resourcePath + "/barriers";
			URL fileUrl = Pars.javaProject ? new File(filePath + ".gpkg").toURI().toURL()
					: CLASSLOADER.getResource(filePath + ".gpkg");
			VectorLayer.readGPKG(fileUrl, PedSimCity.barriers);

			logger.info("Barriers successfully imported.");
		} catch (Exception e) {
			handleImportError("Importing Barriers Failed", e);
		}
	}

	/**
	 * Imports empirical agent groups data for the simulation.
	 *
	 * @throws Exception If an error occurs during the import process.
	 */
	private static void handleImportError(String layerName, Exception e) {
		logger.info(layerName);
	}
}
