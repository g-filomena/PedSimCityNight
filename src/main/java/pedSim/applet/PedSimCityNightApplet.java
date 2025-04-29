package pedSim.applet;

import java.awt.Button;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import pedSim.engine.Engine;
import pedSim.engine.Environment;
import pedSim.engine.Import;
import pedSim.parameters.Pars;
import pedSim.parameters.TimePars;
import pedSim.utilities.LoggerUtil;

/**
 * A graphical user interface (GUI) applet for configuring and running the PedSimCity simulation. This applet allows
 * users to select simulation parameters, start the simulation, and view simulation progress. It provides options for
 * choosing the simulation mode, city name, and other simulation-specific settings. Users can also enable specific
 * origin-destination (OD) testing and access other advanced options.
 */
public class PedSimCityNightApplet extends Frame implements ItemListener {

	private static final long serialVersionUID = 1L;
	private Choice cityName;
	private Button startButton;
	private Button endButton;
	private Label jobLabel;
	private Label remainingTripsLabel;
	private Label jobsLabel;
	private TextField daysTextField;
	private TextField jobsTextField;

	private static final Logger logger = LoggerUtil.getLogger();
	private Thread simulationThread;
	private TextField populationTextField;
	private TextField percentageTextField;

	/**
	 * Constructs a new instance of the `PedSimCityApplet` class, creating a graphical user interface (GUI) applet for
	 * configuring and running the PedSimCity simulation. Initialises and arranges various GUI components, including
	 * mode selection, city selection, and simulation control buttons.
	 */
	public PedSimCityNightApplet() {
		super("PedSimCity Applet");
		setLayout(null);

		Label cityNameLabel = new Label("Study area:");
		cityNameLabel.setBounds(10, 70, 120, 20);
		add(cityNameLabel);
		cityName = new Choice();
		cityName.setBounds(140, 70, 150, 20);
		updateCityNameOptions(); // Set initial options based on the default selection of modeChoice
		add(cityName);

		Label daysLabel = new Label("Duration in days:");
		daysTextField = new TextField(Integer.toString(Pars.durationDays));
		daysLabel.setBounds(10, 100, 120, 20);
		daysTextField.setBounds(190, 100, 100, 20);
		add(daysLabel);
		add(daysTextField);

		Label populationLabel = new Label("Study area Population:");
		populationTextField = new TextField(Integer.toString(Pars.population));
		populationLabel.setBounds(10, 130, 150, 20);
		populationTextField.setBounds(190, 130, 100, 20);
		add(populationLabel);
		add(populationTextField);

		Label percentageLabel = new Label("% Population simulated:");
		percentageTextField = new TextField(Double.toString(Pars.percentagePopulationAgent * 100));
		percentageLabel.setBounds(10, 160, 150, 20);
		percentageTextField.setBounds(190, 160, 100, 20);
		add(percentageLabel);
		add(percentageTextField);

		Label nrJobsLabel = new Label("Nr jobs:");
		jobsTextField = new TextField(Integer.toString(1));
		nrJobsLabel.setBounds(10, 190, 100, 20);
		jobsTextField.setBounds(190, 190, 100, 20);
		add(nrJobsLabel);
		add(jobsTextField);

		Button otherOptionsButton = new Button("Other Options");
		otherOptionsButton.setBounds(10, 230, 150, 30);
		otherOptionsButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openParametersPanel();
			}
		});
		add(otherOptionsButton);

		Color color = new Color(0, 220, 0);
		startButton = new Button("Run Simulation");
		startButton.setBounds(10, 270, 120, 50);
		startButton.setBackground(color);
		add(startButton);

		endButton = new Button("End Simulation");
		endButton.setBackground(Color.PINK);

		startButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				endButton.setBounds(10, 270, 120, 50);
				add(endButton);
				startButton.setVisible(false);
				simulationThread = new Thread(() -> {
					try {
						startSimulation();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				});
				simulationThread.start();
			}
		});

		endButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (simulationThread != null && simulationThread.isAlive()) {
					System.exit(0);
				}
			}
		});

		jobLabel = new Label("Executing Job Nr:");
		jobLabel.setBounds(140, 280, 120, 20);
		jobLabel.setVisible(false);
		add(jobLabel);

		remainingTripsLabel = new Label("Trips left (jobs avg):");
		remainingTripsLabel.setBounds(140, 310, 170, 20);
		remainingTripsLabel.setVisible(false);
		add(remainingTripsLabel);

		jobsLabel = new Label("Parallelising ? Jobs");
		jobsLabel.setBounds(140, 350, 120, 20);
		jobsLabel.setVisible(false);
		add(jobsLabel);

		setSize(350, 350);
		setVisible(true);

	}

	/**
	 * Opens the `OtherOptionsPanel`, allowing the user to configure additional simulation options.
	 */
	private void openParametersPanel() {
		ParametersPanel parametersFrame = new ParametersPanel();
		parametersFrame.setVisible(true); // Display the frame
	}

	/**
	 * Initiates the simulation with the selected parameters and starts the simulation process. This method sets the
	 * city name, simulation mode, and other parameters before running the simulation.
	 * 
	 * @throws Exception
	 */
	private void startSimulation() throws Exception {

		Pars.cityName = cityName.getSelectedItem();
		Pars.jobs = Integer.parseInt(jobsTextField.getText());
		TimePars.numberOfDays = Integer.parseInt(daysTextField.getText());
		Pars.population = Integer.parseInt(populationTextField.getText());
		Pars.percentagePopulationAgent = Double.parseDouble(percentageTextField.getText()) / 100.0;
		Pars.setSimulationParameters();
		runSimulation();
	}

	/**
	 * Initiates the simulation with the selected parameters and starts the simulation process. This method sets the
	 * city name, simulation mode, and other parameters before running the simulation.
	 * 
	 * @throws Exception
	 */
	private void runSimulation() throws Exception {
		importFiles();

		Environment.prepare();
		logger.info("Environment Prepared. About to Start Simulation");

		// run in parallel
		IntStream.range(0, Pars.jobs).parallel().forEach(jobNr -> {
			try {
				Engine engine = new Engine(); // new instance per thread
				logger.info("Executing Job nr.: " + jobNr);
				engine.executeJob(jobNr);
			} catch (Exception e) {
				System.out.println("Error executing job " + jobNr + " " + e);
				throw new RuntimeException(e);
			}
		});
		handleEndSimulation();

	}

	private void importFiles() {
		try {
			Import importer = new Import();
			importer.importFiles();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void handleEndSimulation() {
		Label endLabel = new Label("Simulation has ended. Close the window to exit.");
		endLabel.setBounds(10, 410, 300, 30);
		add(endLabel);
	}

	/**
	 * The main entry point for the PedSimCityApplet application.
	 *
	 * @param args an array of command-line arguments (not used in this application).
	 */
	public static void main(String[] args) {
		PedSimCityNightApplet applet = new PedSimCityNightApplet();
		applet.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				applet.dispose();
			}
		});
	}

	// This method updates the available options for cityName based on the selected
	// modeChoice
	private void updateCityNameOptions() {
		cityName.removeAll();
		cityName.add("TorinoCentre");
		cityName.validate(); // Validate the layout to reflect changes in options
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
	}
}
