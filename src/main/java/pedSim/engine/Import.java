package pedSim.engine;

import java.io.File;
import java.net.URL;
import java.util.logging.Logger;

import pedSim.parameters.Pars;
import pedSim.utilities.LoggerUtil;
import sim.field.geo.VectorLayer;

/**
 * This class is responsible for importing various data files required for the simulation based on the selected
 * simulation parameters. It includes methods for importing distances, barriers, buildings and sight lines, road network
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
	 * Imports various data files required for the simulation based on the selected simulation parameters.
	 *
	 * @throws Exception If an error occurs during the import process.
	 */
	public void importFiles() throws Exception {
		resourcePath = Pars.cityName;
		if (Pars.javaProject)
			resourcePath = Pars.localPath + resourcePath;

		readBuildings();
		readBarriers();
		readGraphs();
	}

	/**
	 * Reads and imports road network graphs required for the simulation.
	 *
	 * @throws Exception If an error occurs during the import process.
	 */
	private void readGraphs() throws Exception {

		try {
			String[] layerSuffixes = { "_edges", "_nodes", "_edgesDual_graph", "_nodesDual_graph" };
			VectorLayer[] vectorLayers = { PedSimCity.roads, PedSimCity.junctions, PedSimCity.intersectionsDual,
					PedSimCity.centroids };

			for (int i = 0; i < layerSuffixes.length; i++) {
				String filePath = resourcePath + "/" + Pars.cityName + layerSuffixes[i];
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
	 * Reads and imports buildings data for the simulation.
	 *
	 * @throws Exception if an error occurs during the import process.
	 */
	private void readBuildings() throws Exception {
		try {
			String[] layerSuffixes = { "_buildings" };
			VectorLayer[] vectorLayers = { PedSimCity.buildings };

			for (int i = 0; i < layerSuffixes.length; i++) {
				String filePath = resourcePath + "/" + Pars.cityName + layerSuffixes[i];
				URL fileUrl = Pars.javaProject ? new File(filePath + ".gpkg").toURI().toURL()
						: CLASSLOADER.getResource(filePath + ".gpkg");
				VectorLayer.readGPKG(fileUrl, vectorLayers[i]);
			}

			PedSimCity.buildings.setID("buildingID");
			logger.info("Buildings successfully imported.");
		} catch (Exception e) {
			handleImportError("Importing Buildings Failed", e);
		}
	}

	/**
	 * Reads and imports barriers data for the simulation.
	 *
	 * @throws Exception If an error occurs during the import process.
	 */
	private void readBarriers() throws Exception {
		try {
			String filePath = resourcePath + "/" + Pars.cityName + "_barriers";
			URL fileUrl = Pars.javaProject ? new File(filePath + ".gpkg").toURI().toURL()
					: CLASSLOADER.getResource(filePath + ".gpkg");
			VectorLayer.readGPKG(fileUrl, PedSimCity.barriers);
			logger.info("Barriers successfully imported.");
		} catch (Exception e) {
			handleImportError("Importing Barriers Failed", e);
		}
	}

	/**
	 * Handles errors that occur during the import of a layer. It logs the layer name and the exception that occurred.
	 *
	 * @param layerName The name of the layer that caused the error.
	 * @param e         The exception that was thrown during the import process.
	 */
	private static void handleImportError(String layerName, Exception e) {
		logger.info(layerName + "  " + e);
	}
}
