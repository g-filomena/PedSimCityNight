package pedSim.applet;

import java.awt.Button;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import pedSim.parameters.Pars;
import pedSim.parameters.RouteChoicePars;

/**
 * A graphical user interface panel for configuring various simulation parameters.
 */
public class ParametersPanel extends Frame {
	private static final long serialVersionUID = 1L;
	private static final int X = 10;
	private int y = 50;
	private static final int Y_SPACE_BETWEEN = 30;
	TextField localPathField = new TextField(null);
	ArrayList<TextField> doubleTextFields = new ArrayList<>();
	ArrayList<TextField> booleanTextFields = new ArrayList<>();

	TextField cityCentreField = new TextField();

	String[] doubleStrings = { "Average distance in meters walked a day, per person" };
	Double defaultValues[] = { Pars.metersPerDayPerPerson };

	/**
	 * Constructs the Parameters Panel.
	 */
	public ParametersPanel() {
		super("Parameters Panel");
		setLayout(null);

		for (String string : doubleStrings) {
			Double defaultValue = defaultValues[Arrays.asList(doubleStrings).indexOf(string)];
			addDoubleField(string, defaultValue, X, y);
			y += Y_SPACE_BETWEEN;
		}

		Label cityCentreLabel = new Label("Enter City Centre Region IDs (comma-separated):");
		cityCentreLabel.setBounds(X, y, 350, 20);
		add(cityCentreLabel);

		String defaultCityCentreRegions = Arrays.stream(RouteChoicePars.cityCentreRegionsID).map(String::valueOf)
				.collect(Collectors.joining(","));
		cityCentreField = new TextField(defaultCityCentreRegions);
		cityCentreField.setBounds(X + 500, y, 200, 20);
		add(cityCentreField);

		y += Y_SPACE_BETWEEN;
		addLocalPathlabel();
		y += Y_SPACE_BETWEEN;

		Button applyButton = new Button("Apply");
		applyButton.setBounds(X, y, 80, 30);
		applyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				adjustParameters();
				inputCityCentreRegions();
				closePanel();
			}
		});
		add(applyButton);
		y += (Y_SPACE_BETWEEN * 2);

		setSize(800, y);
		setVisible(true);

		addWindowListener((WindowListener) new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				closePanel();
			}
		});
	}

	private void addLocalPathlabel() {

		Label localPathLabel = new Label(
				"Fill this field only with a local path, only if running as Java Project, not from JAR");
		localPathLabel.setBounds(X, y, 450, 20);
		localPathLabel.setFont(new Font("Arial", Font.ITALIC, 12));
		add(localPathLabel);
		y += Y_SPACE_BETWEEN;

		localPathLabel = new Label("e.g.: C:/Users/YourUser/Scripts/pedsimcity/src/main/resources/");
		localPathLabel.setBounds(X, y - 10, 450, 20);
		localPathLabel.setFont(new Font("Arial", Font.ITALIC, 12));
		add(localPathLabel);
		y += Y_SPACE_BETWEEN;

		localPathField.setBounds(X, y - 10, 350, 20);
		add(localPathField);
	}

	private void inputCityCentreRegions() {
		String[] cityCentreArray = cityCentreField.getText().split(",");
		Integer[] cityCentreRegions;

		cityCentreRegions = new Integer[cityCentreArray.length];
		for (int i = 0; i < cityCentreArray.length; i++)
			cityCentreRegions[i] = Integer.parseInt(cityCentreArray[i].trim());

		RouteChoicePars.cityCentreRegionsID = cityCentreRegions;
	}

	/**
	 * Adds double-interpreter field to the panel for adjusting simulation parameters.
	 *
	 * @param fieldName    The name of the parameter.
	 * @param defaultValue The default value for the parameter.
	 * @param x            The x-coordinate for the field.
	 * @param y            The y-coordinate for the field.
	 */
	private void addDoubleField(String fieldName, double defaultValue, int x, int y) {
		Label label = new Label(fieldName + ":");
		TextField textField = new TextField(Double.toString(defaultValue));
		label.setBounds(x, y, 600, 20);
		textField.setBounds(x + 600, y, 100, 20);
		add(label);
		add(textField);
		doubleTextFields.add(textField);
	}

	public static void main(String[] args) {
		ParametersPanel frame = new ParametersPanel();
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
	}

	/**
	 * Adjusts the simulation parameters based on the values entered in text fields. Parses the values and updates the
	 * corresponding parameters in the Parameters class.
	 */
	private void adjustParameters() {

		if (localPathField.getText() != null) {
			Pars.localPath = localPathField.getText();
			Pars.javaProject = true;
		}
	}

	/**
	 * Closes the Panel.
	 */
	private void closePanel() {
		setVisible(false);
		dispose();
	}
}
