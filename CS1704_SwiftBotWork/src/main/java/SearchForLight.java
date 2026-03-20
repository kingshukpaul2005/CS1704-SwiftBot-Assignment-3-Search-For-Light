import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Scanner;

import javax.imageio.ImageIO;

import swiftbot.Button;
import swiftbot.ImageSize;
import swiftbot.SwiftBotAPI;
import swiftbot.Underlight;

public class SearchForLight {
	public static SwiftBotAPI swiftBot;  // must stay public for SwiftBot API

	private static LightAnalyzer analyzer = new LightAnalyzer();
	private static FileHandler fileHandler = new FileHandler();
	private static SwiftBotActions actions = new SwiftBotActions();
	private static UI ui = new UI();

	private static boolean standBy = true;
	private static boolean exit = false;
	private static int[] sections;
	private static ArrayList<Double[]> sectionLog = new ArrayList<>();
	private static int[] threshold;
	private static long[] obstacleTimes = {-1,-1,-1,-1,-1};
	private static int obstacleCount = 0;
	private static int brightestIntensity = 0;
	private static int direction;

	private static long startTime;
	private static ArrayList<String> movementLog = new ArrayList<>();
	private static double totalDistance = 0;
	private static ArrayList<String> imageLog = new ArrayList<>();
	private static int totalObstacleCount = 0;
	private static Scanner sc = new Scanner(System.in);
	private static final double FORWARD_DISTANCE_CM = 24.0;
	private static SearchMode searchMode = new LightMode();
	private static String sessionPath;

	public static void main(String[] args) throws InterruptedException {
		//Initialize the SwiftBotAPI with exception
		try {
			swiftBot = SwiftBotAPI.INSTANCE;
		} catch (Exception e) {
			System.out.println("\nI2C disabled!");
			System.exit(5);
		}
		sessionPath = FileHandler.createSessionFolder();

		ui.standByModeUI();


		swiftBot.enableButton(Button.X, () -> {
			ui.buttonPressedUI("X");
			exit = true;
			standBy = false;
		});

		swiftBot.enableButton(Button.A, () -> {
			ui.buttonPressedUI("A");
			standBy = false;
		});

		swiftBot.enableButton(Button.B, () -> {
			ui.buttonPressedUI("B");
			searchMode = new DarkMode();
			standBy = false;
		});

		while (standBy) { 
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
		swiftBot.disableAllButtons();
		if (exit) {
			ui.exitNoticeUI();
			swiftBot.disableUnderlights(); 
			System.exit(0);
		}

		//Calibration
		EnvironmentalCalibration();
		ui.calibrationUI(threshold, searchMode.getModeLabel(), searchMode.getModeColour());
		actions.surfaceType(sc, ui);

		swiftBot.enableButton(Button.X, () -> {
			ui.buttonPressedUI("X");
			exit = true;
		});
		//Main Game Loop
		CoreLoop();

		//Writing Log
		String logPath = FileHandler.writeLog(
				threshold, 
				brightestIntensity, 
				startTime, 
				totalDistance, 
				totalObstacleCount, 
				movementLog,
				sectionLog,
				imageLog,
				sessionPath,
				searchMode.getModeName(),
				searchMode.getPeakIntensityLabel());

		if (logPath != null) {
			ui.terminationScreenUI(startTime, totalDistance, totalObstacleCount, logPath);
		}

		System.exit(0);
	}
	
	// Takes a baseline image at startup to establish ambient light levels.
	// The resulting intensities are stored as a per-column threshold array,
	// used throughout the session to distinguish target light from background.
	public static void EnvironmentalCalibration() {
		BufferedImage img = swiftBot.takeStill(ImageSize.SQUARE_720x720);		
		threshold = analyzer.calculateSectionIntensities(img); 
	}

	// Main execution loop. Each cycle: captures image, checks for wandering,
	// handles normal navigation or obstacle avoidance accordingly.
	// Button X sets exit flag which is checked at the start of each cycle
	// to allow clean shutdown with log writing.
	public static void CoreLoop() throws InterruptedException {
		boolean terminate = false;
		startTime = System.currentTimeMillis();
		String[] directionNames = {"Left", "Straight", "Right"};

		while (!terminate) {
			if (exit) {
				ui.exitNoticeUI();
				swiftBot.disableUnderlights();
				return; // exits CoreLoop, then main writes log and exits
			}
			ui.incrementCycle();

			BufferedImage img = captureAndAnalyse();

			if (isWandering()) {
				terminate = handleWandering(img, directionNames);
			}
			else {
				terminate = handleNormalMode(img, directionNames);
			}
			System.out.println();
		}
	}

	// Captures image, splits into left/centre/right columns and calculates
	// average luminance per section using the ITU-R BT.601 formula.
	// Updates peak intensity tracked across the whole session.
	public static BufferedImage captureAndAnalyse() {
		// Take Picture
		BufferedImage img = swiftBot.takeStill(ImageSize.SQUARE_720x720);
		sections = analyzer.calculateSectionIntensities(img);
		sectionLog.add(new Double[]{
				(double) sections[0],
				(double) sections[1],
				(double) sections[2]
		});

		// Update Peak Intensity
		int currentBest = sections[searchMode.getBestSection(sections, analyzer)];
		if (brightestIntensity == 0 || searchMode.isBetterIntensity(currentBest, brightestIntensity)) {
			brightestIntensity = currentBest;
		}

		return img;

	}

	// Wandering occurs when all three sections are at or below (LIGHT mode)
	// or at or above (DARK mode) the calibrated threshold, meaning no
	// meaningful light source has been detected in any direction.
	public static boolean isWandering() {
		return searchMode.isWandering(sections, threshold);
	}

	public static boolean handleWandering(BufferedImage img, String[] directionNames) throws InterruptedException {
		double obstacleDistance = swiftBot.useUltrasound();
		if (obstacleDistance > 0 && obstacleDistance < 50) {
			return handleObstacle(img, directionNames, obstacleDistance, "Wander Obstacle Avoided");
		} else {
			int wanderDirection = (int)(Math.random() * 3);
			ui.wanderDisplayUI(directionNames[wanderDirection], obstacleDistance);
			actions.wander(swiftBot, wanderDirection);
			movementLog.add("Wandering - " + directionNames[wanderDirection]);
			return false;
		}
	}

	public static boolean handleNormalMode(BufferedImage img, String[] directionNames) throws InterruptedException {
		double obstacleDistance = swiftBot.useUltrasound();

		if (obstacleDistance > 0 && obstacleDistance < 50) {
			return handleObstacle(img, directionNames, obstacleDistance, "Obstacle Avoided");
		} else {
			actions.setUnderLights(swiftBot, "green");
			direction = searchMode.getBestSection(sections, analyzer);
			int speed = analyzer.calculateSpeed(img, direction, actions.getBaseSpeed());
			ui.movementUI(sections, direction, false, 0, speed, searchMode.getIntensityWord());
			actions.go(swiftBot, direction, speed);
			movementLog.add(directionNames[direction]);
			if (direction==1) totalDistance += FORWARD_DISTANCE_CM; 
			return false;
		}
	}

	// Tracks the last 5 obstacle timestamps to detect high-frequency encounters.
	// If 5 obstacles occur within a 5-minute window, termination is prompted.
	public static boolean handleObstacle(BufferedImage img, String[] directionNames, double obstacleDistance, String logLabel) throws InterruptedException{
		obstacleCount += 1;
		totalObstacleCount += 1;

		obstacleTimes[0] = obstacleTimes[1];
		obstacleTimes[1] = obstacleTimes[2];
		obstacleTimes[2] = obstacleTimes[3];
		obstacleTimes[3] = obstacleTimes[4];
		obstacleTimes[4] = System.currentTimeMillis();

		String imagePath = fileHandler.saveImage(img, sessionPath);
		if (imagePath != null) imageLog.add(imagePath);

		for (int i = 0; i < 3; i++) {
			actions.setUnderLights(swiftBot, "red");
			Thread.sleep(200);
			actions.setUnderLights(swiftBot, "blank");
		}

		int bestIndex = searchMode.getBestSection(sections, analyzer);
		int avoidDirection = analyzer.getSecondDirectionIndex(sections, bestIndex, searchMode.findLowest());

		if (avoidDirection == 1) {
			avoidDirection = searchMode.pickSide(sections);
		}

		ui.movementUI(sections, avoidDirection, true, obstacleDistance, actions.getBaseSpeed(), searchMode.getIntensityWord());
		actions.avoid(swiftBot, avoidDirection);
		movementLog.add(logLabel + "-"+ directionNames[avoidDirection]);

		if (obstacleCount >= 5) {
			long windowMs = 5 * 60 * 1000;
			if (obstacleTimes[4] - obstacleTimes[0] < windowMs) {
				return termination();
			}
		}
		return false;
	}


	public static boolean termination() {
		final String TERMINATE = "TERMINATE";		
		final String CONTINUE = "CONTINUE";
		ui.terminationPromptUI(obstacleCount);	
		String decision = sc.nextLine();
		while (!decision.equals(TERMINATE) && !decision.equals(CONTINUE)) {
			ui.invalidInputUI(); 
			decision = sc.nextLine(); 
		}
		if (decision.equals(TERMINATE)) {
			return true;
		} else {
			ui.continueConfirmedUI();
			// Reset the window
			java.util.Arrays.fill(obstacleTimes, -1);
			return false;
		}
	}
}

//Abstract class representing the search strategy.
//Subclasses LightMode and DarkMode implement mode-specific behaviour,
//eliminating conditional checks throughout the main logic (polymorphism).
abstract class SearchMode {
	// Returns index of the best section (brightest for Light, darkest for Dark)
	public abstract int getBestSection(int[] sections, LightAnalyzer analyzer);

	// Returns true when no target has been found above/below threshold
	public abstract boolean isWandering(int[] sections, int[] threshold);

	// Returns true if current intensity reading is better than stored peak
	public abstract boolean isBetterIntensity(int current, int best);

	// Whether to find the lowest intensity when selecting avoidance direction
	public abstract boolean findLowest();

	// Fallback side selection when second direction resolves to straight (1)
	public abstract int pickSide(int[] sections);            
	public abstract String getModeName();
	public abstract String getPeakIntensityLabel();
	public abstract String getModeLabel();       // "SEARCH FOR LIGHT" or "SEARCH FOR DARK"
	public abstract String getModeColour();      // BLUE or YELLOW terminal colour code
	public abstract String getIntensityWord();   // "highest" or "lowest"

}

class LightMode extends SearchMode {

	public int getBestSection(int[] sections, LightAnalyzer analyzer) {
		return analyzer.getBrightestSection(sections);
	}

	public boolean isWandering(int[] sections, int[] threshold) {
		return sections[0] <= threshold[0] &&
				sections[1] <= threshold[1] &&
				sections[2] <= threshold[2];
	}

	public String getModeName() {
		return "LIGHT";
	}

	public boolean isBetterIntensity(int current, int best) {
		return current > best;          // light mode wants the highest intensity
	}

	public boolean findLowest() {
		return false;                   // light mode avoids toward brighter side
	}

	public int pickSide(int[] sections) {
		return (sections[0] >= sections[2]) ? 0 : 2;  // pick brighter side
	}

	public String getPeakIntensityLabel() {
		return "---Brightest Intensity Detected---";
	}

	public String getModeLabel()    { return "SEARCH FOR LIGHT"; }
	public String getModeColour()   { return UI.YELLOW; }
	public String getIntensityWord(){ return "highest"; }
}

class DarkMode extends SearchMode {

	public int getBestSection(int[] sections, LightAnalyzer analyzer) {
		return analyzer.getDarkestSection(sections);
	}

	public boolean isWandering(int[] sections, int[] threshold) {
		return sections[0] >= threshold[0] &&
				sections[1] >= threshold[1] &&
				sections[2] >= threshold[2];
	}

	public String getModeName() {
		return "DARK";
	}

	public boolean isBetterIntensity(int current, int best) {
		return current < best;          // dark mode wants the lowest intensity
	}

	public boolean findLowest() {
		return true;                    // dark mode avoids toward darker side
	}

	public int pickSide(int[] sections) {
		return (sections[0] <= sections[2]) ? 0 : 2;  // pick darker side
	}

	public String getPeakIntensityLabel() {
		return "---Darkest Intensity Detected---";
	}

	public String getModeLabel()    { return "SEARCH FOR DARK"; }
	public String getModeColour()   { return UI.BLUE; }
	public String getIntensityWord(){ return "lowest"; }
}

class LightAnalyzer {
	
	// Iterates every pixel of the 720x720 image.
	// Luminance formula: 0.299*R + 0.587*G + 0.114*B (ITU-R BT.601 standard)
	// Divides horizontally into three equal columns of 240px each.
	public int[] calculateSectionIntensities(BufferedImage img) {
		// Left, Center, Right
		int[] sectionSums = {0,0,0};

		for (int y = 0; y < 720; y++) {
			for (int x = 0; x < 720; x++) {
				int brightness = getLuminance(img.getRGB(x, y));
				if (x<720/3) {sectionSums[0]+=brightness;}
				else if (x<720*2/3) {sectionSums[1]+=brightness;}
				else {sectionSums[2]+=brightness;}
			}
		}
		int pixelsPerSection = 720*720/3;
		return new int[] {
				(int) sectionSums[0]/pixelsPerSection,
				(int) sectionSums[1]/pixelsPerSection,
				(int) sectionSums[2]/pixelsPerSection,
		};
	}

	private int getLuminance(int rgb) {
		Color c = new Color(rgb);
		return (int) (0.299*c.getRed() + 0.587*c.getGreen() + 0.114*c.getBlue());
	}

	public int getBrightestSection(int[] array) {
		if (array == null || array.length == 0) {
			return -1;
		}
		int maxIndex = 0;
		for (int i = 1; i < array.length; i++) {
			if (array[i] > array[maxIndex]) {
				maxIndex = i;
			}
		}
		return maxIndex;
	}

	public int getSecondDirectionIndex(int[] array, int excludedIndex, boolean findLowest) {
		int secondIndex = -1;
		for (int i = 0; i < array.length; i++) {
			if (i == excludedIndex) continue;
			if (secondIndex == -1) {
				secondIndex = i;
			} else if (findLowest  && array[i] < array[secondIndex]) {
				secondIndex = i;
			} else if (!findLowest && array[i] > array[secondIndex]) {
				secondIndex = i;
			}
		}

		// Equal — random selection
		int[] remaining = new int[array.length - 1];
		int[] remainingIndices = new int[array.length - 1];
		int idx = 0;
		for (int i = 0; i < array.length; i++) {
			if (i != excludedIndex) {
				remaining[idx] = array[i];
				remainingIndices[idx] = i;
				idx++;
			}
		}
		if (remaining[0] == remaining[1]) {
			return remainingIndices[(int)(Math.random() * 2)];
		}
		return secondIndex;
	}

	public int getDarkestSection(int[] array) {
		if (array == null || array.length == 0) {
			return -1;
		}
		int minIndex = 0;
		for (int i = 1; i < array.length; i++) {
			if (array[i] < array[minIndex]) {
				minIndex = i;
			}
		}
		return minIndex;
	}
	
	// Splits the target column into upper and lower halves.
	// If the upper half is brighter, the light source is likely further away,
	// so speed is increased by 20 to close the distance faster.
	public int calculateSpeed(BufferedImage img, int direction, int baseSpeed) {
		int xStart, xEnd;
		switch (direction) {
		case 0: xStart = 0;   xEnd = 240; break;
		case 1: xStart = 240; xEnd = 480; break;
		case 2: xStart = 480; xEnd = 720; break;
		default: return baseSpeed;
		}
		long upperSum = 0, lowerSum = 0;
		for (int y = 0; y < 720; y++) {
			for (int x = xStart; x < xEnd; x++) {
				int brightness = getLuminance(img.getRGB(x, y));
				if (y < 360) upperSum += brightness;
				else lowerSum += brightness;
			}
		}
		return (upperSum > lowerSum) ? baseSpeed + 20 : baseSpeed;
	}
}


class FileHandler {

	public static String createSessionFolder() {
		String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
				.format(new java.util.Date());
		String sessionPath = "/data/home/pi/Session_" + timestamp;
		File sessionDir = new File(sessionPath);
		if (sessionDir.mkdirs()) {
			System.out.println("Session folder created: " + sessionPath);
		} else {
			System.err.println("Failed to create session folder.");
		}
		return sessionPath;
	}

	public String saveImage(BufferedImage img, String sessionPath) {
		if (img == null) {
			System.out.println("Error: Image is Null!");
			return null;
		}
		String baseName = "Image";
		String extension = "png";
		try {
			File outputFile = findAvailableFilename(sessionPath, baseName, extension);
			boolean success = ImageIO.write(img, extension, outputFile);
			if (success) {
				System.out.println("Image saved: " + outputFile.getName());
				return outputFile.getAbsolutePath();
			} else {
				System.err.println("Failed to write image.");
			}
		} catch (IOException e) {
			System.err.println("Error saving image: " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	public static File findAvailableFilename(String directoryPath, String baseName, String extension) {
		File directory = new File(directoryPath);
		int counter = 1;
		while (true) {
			File file = new File(directory, String.format("%s_%d.%s", baseName, counter, extension));
			if (!file.exists()) return file;
			counter++;
		}
	}

	public static String writeLog(
			int[] threshold,
			int brightestIntensity,
			long startTime,
			double totalDistance,
			int totalObstacleCount,
			ArrayList<String> movementLog,
			ArrayList<Double[]> sectionLog,
			ArrayList<String> imageLog,
			String sessionPath,
			String modeName,
			String peakIntensityLabel
			) {
		String baseName = "Logger";
		String extension = "txt";

		File outputFile = findAvailableFilename(sessionPath, baseName, extension);
		long durationMs = System.currentTimeMillis()- startTime;
		long durationSecs = durationMs/1000;
		int durationMins = (int) Math. floorDiv(durationSecs, 60);
		durationSecs = durationSecs%60;

		try (PrintWriter pw = new PrintWriter(new FileWriter(outputFile))) {

			pw.println("==================================================");
			pw.println("         SEARCH FOR " + modeName + " - SESSION LOG           ");
			pw.println("==================================================");
			pw.println();

			// Threshold
			pw.println("---Environment BaseLine (Threshold)---");
			pw.printf("  Left: %d | Centre: %d | Right: %d%n",
					threshold[0], threshold[1], threshold[2]);
			pw.println();

			// Peak Intensity
			pw.println(peakIntensityLabel);  
			pw.println("Peak Intensity: "+ brightestIntensity);
			pw.println();

			//Execution Duration
			pw.println("--- Execution Duration ---");
			pw.printf("  %d minutes %d seconds %n", durationMins, durationSecs);
			pw.println();

			//Total Distance
			pw.println("--- Total Distance Travelled ---");
			pw.printf("  %.1f cm%n", totalDistance);
			pw.println();

			// Obstacle Count
			pw.println("--- Obstacles Detected ---");
			pw.println("  Count: " + totalObstacleCount);
			pw.println();

			// Movement log and Light Intensity
			pw.println("--- Movement History and Light Intensity ---");
			int logSize = Math.min(movementLog.size(), sectionLog.size());
			for (int i = 0; i < logSize; i++) {
				Double[] s = sectionLog.get(i);
				pw.printf("  Cycle %d: Left=%.0f | Centre=%.0f | Right=%.0f%n",
						i + 1, s[0], s[1], s[2]);
				pw.printf("  %d. %s%n", i + 1, movementLog.get(i));
				pw.println();
			}
			pw.println();


			// Image file paths
			pw.println("--- Obstacle Images Saved ---");
			if (imageLog.isEmpty()) {
				pw.println("  No images saved.");
			} else {
				for (String path : imageLog) {
					pw.println("  " + path);
				}
			}
			pw.println();

			pw.println("--- Log File Location ---");
			pw.println("  " + outputFile.getAbsolutePath());
			pw.println("==================================================");



		} catch (IOException e) {
			e.printStackTrace();
		}
		return outputFile.getAbsolutePath();
	}

}


class SwiftBotActions {
	private static int baseSpeed;

	// Additional feature: surface calibration at startup.
	// Carpet requires higher base speed (60) due to increased friction.
	// Smooth surfaces use lower base speed (40) to maintain control.
	// Base speed is then passed into calculateSpeed() each navigation cycle.
	public void surfaceType(Scanner sc, UI ui) {
		while (true) {
			System.out.println("Is the surface carpet? (True/False): ");
			String input = sc.nextLine().trim().toLowerCase();

			if (input.equals("true")) {
				baseSpeed = 60;
				ui.surfaceConfirmedUI(true, baseSpeed);
				break;
			} else if (input.equals("false")) {
				baseSpeed = 40;
				ui.surfaceConfirmedUI(false, baseSpeed);
				break;
			} else {
				System.out.println(UI.RED + "[ERROR]: Enter 'True' or 'False'." + UI.RESET);
			}
		}
	}

	public int getBaseSpeed() {
		return baseSpeed;
	}

	public void go(SwiftBotAPI swiftBot, int direction, int speed) {
		switch (direction) {
		case 0:	// left
			swiftBot.move(-100, 100, 100); break;
		case 1: // forward
			swiftBot.move(speed, speed, 1000); break;
		case 2: //right
			swiftBot.move(100, -100, 100); break;
		default:
			break;
		}
	}

	public void wander(SwiftBotAPI swiftBot, int direction) {
		switch (direction) {
		case 0: swiftBot.move(-100, 100, 200); break;  
		case 1: swiftBot.move(baseSpeed, baseSpeed, 1000); break;  
		case 2: swiftBot.move(100, -100, 200); break;  
		}
	}

	public void avoid(SwiftBotAPI swiftBot, int direction) {
		switch (direction) {
		case 0: swiftBot.move(-baseSpeed, baseSpeed, 600); break;  // longer turn left to clear obstacle
		case 2: swiftBot.move(baseSpeed, -baseSpeed, 600); break;  // longer turn right to clear obstacle
		default: break; // case 1 (forward) should never reach here due to guard
		}
	}

	public void setUnderLights(SwiftBotAPI swiftBot, String colour) {
		int[] red   = {255, 0, 0};
		int[] green = {0, 255, 0};
		int[] blank = {0, 0, 0};

		int[] rgb;
		switch (colour) {
		case "red":   rgb = red;   break;
		case "green": rgb = green; break;
		default:      rgb = blank; break;
		}
		setAllUnderlights(swiftBot, rgb);
	}

	private void setAllUnderlights(SwiftBotAPI swiftBot, int[] rgb) {
		swiftBot.setUnderlight(Underlight.FRONT_RIGHT,  rgb);
		swiftBot.setUnderlight(Underlight.MIDDLE_RIGHT, rgb);
		swiftBot.setUnderlight(Underlight.BACK_RIGHT,   rgb);
		swiftBot.setUnderlight(Underlight.FRONT_LEFT,   rgb);
		swiftBot.setUnderlight(Underlight.MIDDLE_LEFT,  rgb);
		swiftBot.setUnderlight(Underlight.BACK_LEFT,    rgb);
	}
}

class UI {
	// Colour constants
	public static final String RESET   = "\u001B[0m";
	public static final String RED     = "\u001B[31m";
	public static final String GREEN   = "\u001B[32m";
	public static final String YELLOW  = "\u001B[33m";
	public static final String BLUE    = "\u001B[34m";
	public static final String CYAN    = "\u001B[36m";
	public static final String WHITE   = "\u001B[37m";
	public static final String BOLD    = "\u001B[1m";
	public static final String DIM     = "\u001B[2m";

	private int cycleCount = 0;

	public void incrementCycle() { 
		cycleCount++;
	}

	public int getCycleCount() {
		return cycleCount;
	}

	public void standByModeUI() {
		//ASCII Art Title
		System.out.println(CYAN + BOLD+ "  ___ ___   _   ___  ___ _  _   ___ ___  ___   _    ___ ___ _  _ _____ ");
		System.out.println(" / __| __| /_\\ | _ \\/ __| || | | __/ _ \\| _ \\ | |  |_ _/ __| || |_   _|");
		System.out.println(" \\__ \\ _| / _ \\|   / (__| __ | | _| (_) |   / | |__ | | (_ | __ | | |  ");
		System.out.println(" |___/___/_/ \\_\\_|_\\\\___|_||_| |_| \\___/|_|_\\ |____|___\\___|_||_| |_|  ");
		System.out.println(RESET);

		System.out.println(WHITE + "======================================================================" + RESET);

		// Status
		System.out.println(BOLD + "Status: " + RESET + YELLOW + "STANDBY" + RESET);

		// Action prompts
		System.out.println(WHITE + "Action: " + RESET +
				"Please press" + GREEN + BOLD + " Button 'A'" + RESET +
				" on the SwiftBot to begin " + CYAN + "Search For Light" + RESET);

		// Additional actions
		System.out.println();
		System.out.println(DIM +BOLD+ WHITE + "Additional Actions" + RESET);
		System.out.println(GREEN + BOLD + "Button 'B'" + RESET +
				": " + YELLOW + "Search for Dark" + RESET);

		System.out.println();
	}

	public void calibrationUI(int[] threshold, String modeLabel, String modeColour) {
		System.out.println(modeColour + BOLD + "Mode: " + modeLabel + RESET);
		System.out.println("Baseline threshold set: " + YELLOW +
				"[" + threshold[0] + "] " +
				"[" + threshold[1] + "] " +
				"[" + threshold[2] + "] " + RESET);
		System.out.println("Environment analyzed. Ready to begin search.");
		System.out.println(WHITE + "======================================================================" + RESET);
	}

	public void buttonPressedUI(String button) {
		System.out.println(BLUE + "[Button '" + button + "' Pressed]" + RESET);
		System.out.println();
		if (button.equals("A")) {
			System.out.println(CYAN + "Starting Search For Light..." + RESET);
		} else if (button.equals("B")) {
			System.out.println(BLUE + "Starting Search For Dark..." + RESET);
		} else if (button.equals("X")) {
			System.out.println(RED + "Exiting..." + RESET);
		}
	}

	public void movementUI(int[] sections, int direction, boolean obstacleFound, 
			double obstacleDistance, int speed, String intensityWord) {
		String[] sectionNames = {"LEFT", "CENTRE", "RIGHT"};
		String[] actionNames  = {"LEFT for 0.2 seconds", "STRAIGHT for 1 second", "RIGHT for 0.2 seconds"};
		String   brightestName = sectionNames[direction];

		//Speed
		String speedLabel;
		if      (speed >= 80) speedLabel = "High Speed";
		else if (speed >= 60) speedLabel = "Medium Speed";
		else                  speedLabel = "Low Speed";

		//Header
		System.out.printf(CYAN + BOLD + "%n===== NAVIGATION CYCLE: %02d ======%n" + RESET, cycleCount);

		// Sensing
		System.out.println(WHITE + "Sensing Environment..." + RESET);
		System.out.println(WHITE + "Light Intensities:" + RESET);
		System.out.printf ("  - LEFT:   %s%d%s%n", (direction == 0 ? YELLOW + BOLD : ""), sections[0], RESET);
		System.out.printf ("  - CENTRE: %s%d%s%n", (direction == 1 ? YELLOW + BOLD : ""), sections[1], RESET);
		System.out.printf ("  - RIGHT:  %s%d%s%n", (direction == 2 ? YELLOW + BOLD : ""), sections[2], RESET);
		System.out.println();

		// Obstacle check
		if (obstacleFound) {
			System.out.printf(RED + BOLD + "Obstacle Check: Object detected at %.1fcm!%n" + RESET, obstacleDistance);
		} else {
			System.out.println(GREEN + "Obstacle Check: No objects detected within 50cm." + RESET);
		}

		// Decision
		System.out.printf(WHITE + "Decision: %s%s%s has the %s intensity.%n" + RESET,
				YELLOW + BOLD, brightestName, RESET + WHITE, intensityWord);

		// Action
		System.out.printf(WHITE + "Action: Moving %s%s%s at %s (%d).%n" + RESET,
				CYAN + BOLD, actionNames[direction], RESET + WHITE,
				speedLabel, speed);

		// Underlights
		String underlightColour = obstacleFound ? RED + "RED" : GREEN + "GREEN";
		System.out.println(WHITE + "Underlights: " + BOLD + underlightColour + RESET);

		// Footer
		System.out.println(CYAN + "=================================" + RESET);
		System.out.println();
	}

	public void wanderDisplayUI(String direction, double obstacleDistance) {
		System.out.printf(YELLOW + BOLD + "%n===== NAVIGATION CYCLE: %02d (WANDERING) ======%n" + RESET, cycleCount);
		System.out.println(YELLOW + "No light source detected above threshold." + RESET);
		System.out.println(YELLOW + "Mode: WANDERING" + RESET);
		System.out.println();
		System.out.println(GREEN + "Obstacle Check: No objects detected within 50cm." + RESET);
		System.out.printf(YELLOW + "Action: Wandering %s for 1 second.%n" + RESET, direction);
		System.out.println(WHITE + "Underlights: " + DIM + "OFF" + RESET);
		System.out.println(CYAN + "=============================================" + RESET);
		System.out.println();
	}

	public void terminationPromptUI(int obstacleCount) {
		System.out.println();
		System.out.println(RED + BOLD + "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" + RESET);
		System.out.printf (RED + BOLD + "  %d OBSTACLES DETECTED WITHIN 5 MINUTES   %n" + RESET, obstacleCount);
		System.out.println(RED + BOLD + "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" + RESET);
		System.out.println();
		System.out.println(WHITE + "Enter " + RED   + BOLD + "TERMINATE" + RESET +
				WHITE   + " to end the session."   + RESET);
		System.out.println(WHITE + "Enter " + GREEN + BOLD + "CONTINUE"  + RESET +
				WHITE   + " to reset the 5-minute window and continue." + RESET);
		System.out.print (BOLD  + "Decision: " + RESET);
	}

	public void invalidInputUI() {
		System.out.println(RED + "[ERROR]: Invalid command. Please enter 'TERMINATE' or 'CONTINUE'." + RESET);
		System.out.print(BOLD + "Decision: " + RESET);
	}

	public void continueConfirmedUI() {
		System.out.println(GREEN + BOLD + "Session continuing. Timer reset." + RESET);
		System.out.println();
	}

	public void terminationScreenUI(long startTime, double totalDistance, int totalObstacleCount, String logPath) {
		long durationSecs = (System.currentTimeMillis() - startTime) / 1000;
		long mins = durationSecs / 60;
		long secs = durationSecs % 60;

		System.out.println();
		System.out.println(RED + BOLD + "======================================================================" + RESET);
		System.out.println(RED + BOLD + "                      PROGRAM TERMINATED                            "  + RESET);
		System.out.println(RED + BOLD + "======================================================================" + RESET);
		System.out.println();
		System.out.println(WHITE + BOLD + "  Session Summary" + RESET);
		System.out.println(WHITE + "  =========================================" + RESET);
		System.out.printf (WHITE + "  Execution Time  : " + RESET + CYAN  + "%dm %ds%n"   + RESET, mins, secs);
		System.out.printf (WHITE + "  Distance        : " + RESET + CYAN  + "%.1f cm%n"   + RESET, totalDistance);
		System.out.printf (WHITE + "  Total Obstacles : " + RESET + RED   + "%d%n"        + RESET, totalObstacleCount);
		System.out.printf (WHITE + "  Navigation Cycles: " + RESET + CYAN + "%d%n"        + RESET, cycleCount);
		System.out.println(WHITE + "  =========================================" + RESET);
		System.out.println(WHITE + "  Log saved to    : " + RESET + GREEN + logPath       + RESET);
		System.out.println();
		System.out.println(RED + BOLD + "======================================================================" + RESET);
	}

	public void surfaceConfirmedUI(boolean isCarpet, int baseSpeed) {
		String surface = isCarpet ? "Carpet" : "Smooth";
		String colour  = isCarpet ? YELLOW   : CYAN;
		System.out.println(colour + BOLD + "Surface: " + surface + RESET +
				WHITE  + " — Base speed set to " + BOLD + baseSpeed + RESET);
		System.out.println();
	}

	public void exitNoticeUI() {
		System.out.println();
		System.out.println(RED + BOLD + "======================================================================" + RESET);
		System.out.println(RED + BOLD + "          Button X pressed — saving log and shutting down.          "  + RESET);
		System.out.println(RED + BOLD + "======================================================================" + RESET);
		System.out.println();
	}
}


