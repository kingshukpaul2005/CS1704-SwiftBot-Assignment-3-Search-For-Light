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
	public static SwiftBotAPI swiftBot;		
	static LightAnalyzer analyzer = new LightAnalyzer();
	static FileHandler fileHandler = new FileHandler();
	static SwiftBotActions actions = new SwiftBotActions();
	static UI ui = new UI();

	static boolean standBy = true;
	static boolean exit = false; 
	static int[] sections;
	static ArrayList<Double[]> sectionLog = new ArrayList<Double[]>(); //done
	static int[] threshold;
	static long[] obstacleTimes = {-1,-1,-1,-1,-1}; //check
	static int obstacleCount = 0; //done
	static int brightestIntensity = 0; //done
	static int direction;

	static long startTime;
	static ArrayList<String> movementLog = new ArrayList<>();
	static double totalDistance = 0;
	static ArrayList<String> imageLog = new ArrayList<>();
	static int totalObstacleCount = 0;
	static Scanner sc = new Scanner(System.in);
	static final double FORWARD_DISTANCE_CM = 24.0;
	static String mode = "LIGHT"; 

	public static void main(String[] args) throws InterruptedException {
		//Initialize the SwiftBotAPI with exception
		try {
			swiftBot = SwiftBotAPI.INSTANCE;
		} catch (Exception e) {
			System.out.println("\nI2C disabled!");
			System.exit(5);
		}
		fileHandler.clearObstaclesDirectory("/data/home/pi/Obstacles");

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
			mode = "DARK";
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
		ui.calibrationUI(threshold, mode);
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
				imageLog);

		if (logPath != null) {
			ui.terminationScreenUI(startTime, totalDistance, totalObstacleCount, logPath);
		}

		System.exit(0);
	}

	public static void EnvironmentalCalibration() {
		BufferedImage img = swiftBot.takeStill(ImageSize.SQUARE_720x720);		
		threshold = analyzer.calculateSectionIntensities(img); 
	}

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
			ui.cycleCount++;

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
		if (mode.equals("DARK")) {
			int currentMin = sections[analyzer.getDarkestSection(sections)];
			if (brightestIntensity == 0 || currentMin < brightestIntensity) {
				brightestIntensity = currentMin;
			}
		} else {
			int currentMax = sections[analyzer.getBrightestSection(sections)];
			if (currentMax > brightestIntensity) {
				brightestIntensity = currentMax;
			}
		}

		return img;

	}

	public static boolean isWandering() {
		if (mode.equals("DARK")) {
			// Wander if nothing is darker than baseline
			return sections[0] >= threshold[0] &&
					sections[1] >= threshold[1] &&
					sections[2] >= threshold[2];
		} else {
			// Wander if nothing is brighter than baseline
			return sections[0] <= threshold[0] &&
					sections[1] <= threshold[1] &&
					sections[2] <= threshold[2];
		}
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
			direction = mode.equals("DARK")
					? analyzer.getDarkestSection(sections)
							: analyzer.getBrightestSection(sections);
			int speed = analyzer.calculateSpeed(img, direction,actions.getBaseSpeed());
			ui.movementUI(sections, direction, false, 0, speed, mode);  
			actions.go(swiftBot, direction, speed);
			movementLog.add(directionNames[direction]);
			if (direction==1) totalDistance += FORWARD_DISTANCE_CM; 
			return false;
		}
	}

	public static boolean handleObstacle(BufferedImage img, String[] directionNames, double obstacleDistance, String logLabel) throws InterruptedException{
		obstacleCount += 1;
		totalObstacleCount += 1;

		obstacleTimes[0] = obstacleTimes[1];
		obstacleTimes[1] = obstacleTimes[2];
		obstacleTimes[2] = obstacleTimes[3];
		obstacleTimes[3] = obstacleTimes[4];
		obstacleTimes[4] = System.currentTimeMillis();

		String imagePath = fileHandler.saveImage(img);
		if (imagePath != null) imageLog.add(imagePath);

		for (int i = 0; i < 3; i++) {
			actions.setUnderLights(swiftBot, "red");
			Thread.sleep(100);
			actions.setUnderLights(swiftBot, "blank");
		}

		int avoidDirection;
		if (mode.equals("DARK")) {
			int darkestIndex = analyzer.getDarkestSection(sections);
			avoidDirection = analyzer.getSecondDirectionIndex(sections, darkestIndex, true);
		} else {
			int brightestIndex = analyzer.getBrightestSection(sections);
			avoidDirection = analyzer.getSecondDirectionIndex(sections, brightestIndex, false);
		}

		if (avoidDirection == 1) {
			if (mode.equals("DARK")) {
				avoidDirection = (sections[0] <= sections[2]) ? 0 : 2; // pick darker side
			} else {
				avoidDirection = (sections[0] >= sections[2]) ? 0 : 2; // pick brighter side
			}
		}

		ui.movementUI(sections, avoidDirection, true, obstacleDistance, actions.getBaseSpeed(), mode);
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
			obstacleCount = 0;
			obstacleTimes[0] = -1;
			obstacleTimes[1] = -1;
			obstacleTimes[2] = -1;
			obstacleTimes[3] = -1;
			obstacleTimes[4] = -1;
			return false;
		}
	}
}


class LightAnalyzer {

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

	public String saveImage(BufferedImage img) {
		if (img == null) {
			System.out.println("Error: Image is Null!");
		}
		else {
			String directoryPath = "/data/home/pi/Obstacles";
			String baseName = "Image";
			String extension = "png";
			try {
				File outputFile = findAvailableFilename(directoryPath, baseName, extension);
				boolean success = ImageIO.write(img, extension, outputFile);

				if (success) {
					System.out.println("Image successfully saved as: " + outputFile.getName());
					return outputFile.getAbsolutePath();
				}
				else {
					System.err.println("Failed to write image file");
				}
			} catch (IOException e) {
				System.err.println("Error saving image: " + e.getMessage());
				e.printStackTrace();
			}
		}
		return null;
	}

	public void clearObstaclesDirectory(String directoryPath) {
		File directory = new File(directoryPath);
		// Create directory if it doesn't exist
		if (directory.exists()) {
			File[] files = directory.listFiles();

			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
						file.delete(); // Delete each individual file
					}
				}
				System.out.println("Existing obstacle logs cleared.");
			}
		} else {
			directory.mkdirs();
		}		

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
			ArrayList<String> imageLog
			) {
		String directoryPath ="/data/home/pi";
		String baseName = "Logger";
		String extension = "txt";

		File outputFile = findAvailableFilename(directoryPath, baseName, extension);
		long durationMs = System.currentTimeMillis()- startTime;
		long durationSecs = durationMs/1000;
		int durationMins = (int) Math. floorDiv(durationSecs, 60);
		durationSecs = durationSecs%60;

		try (PrintWriter pw = new PrintWriter(new FileWriter(outputFile))) {

			pw.println("==================================================");
			pw.println("         " + SearchForLight.mode + " - SESSION LOG           ");
			pw.println("==================================================");
			pw.println();

			// Threshold
			pw.println("---Environment BaseLine (Threshold)---");
			pw.printf("  Left: %d | Centre: %d | Right: %d%n",
					threshold[0], threshold[1], threshold[2]);
			pw.println();

			// Peak Intensity
			pw.println(SearchForLight.mode.equals("DARK") 
					? "---Darkest Intensity Detected---" 
							: "---Brightest Intensity Detected---");
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
		int[] red = {255, 0, 0};
		int[] green = {0, 255, 0};
		int[] blank = {0, 0, 0};

		switch (colour) {
		case "red":
			swiftBot.setUnderlight(Underlight.FRONT_RIGHT, red);
			swiftBot.setUnderlight(Underlight.MIDDLE_RIGHT, red);
			swiftBot.setUnderlight(Underlight.BACK_RIGHT, red);
			swiftBot.setUnderlight(Underlight.FRONT_LEFT, red);
			swiftBot.setUnderlight(Underlight.MIDDLE_LEFT, red);
			swiftBot.setUnderlight(Underlight.BACK_LEFT, red);
			break;

		case "green":
			swiftBot.setUnderlight(Underlight.FRONT_RIGHT, green);
			swiftBot.setUnderlight(Underlight.MIDDLE_RIGHT, green);
			swiftBot.setUnderlight(Underlight.BACK_RIGHT, green);
			swiftBot.setUnderlight(Underlight.FRONT_LEFT, green);
			swiftBot.setUnderlight(Underlight.MIDDLE_LEFT, green);
			swiftBot.setUnderlight(Underlight.BACK_LEFT, green);
			break;

		case "blank":
			swiftBot.setUnderlight(Underlight.FRONT_RIGHT, blank);
			swiftBot.setUnderlight(Underlight.MIDDLE_RIGHT, blank);
			swiftBot.setUnderlight(Underlight.BACK_RIGHT, blank);
			swiftBot.setUnderlight(Underlight.FRONT_LEFT, blank);
			swiftBot.setUnderlight(Underlight.MIDDLE_LEFT, blank);
			swiftBot.setUnderlight(Underlight.BACK_LEFT, blank);
			break; 

		default:
			break;
		}
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

	int cycleCount = 0;

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

	public void calibrationUI(int[] threshold, String mode) {
		String modeColour = mode.equals("DARK") ? BLUE : YELLOW;
		String modeLabel  = mode.equals("DARK") ? "SEARCH FOR DARK" : "SEARCH FOR LIGHT";

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

	public void movementUI(int[] sections, int direction, boolean obstacleFound, double obstacleDistance, int speed, String mode) {
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
				YELLOW + BOLD, brightestName, RESET + WHITE,
				mode.equals("DARK") ? "lowest" : "highest");

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
		System.out.println(WHITE + "  ─────────────────────────────────────────" + RESET);
		System.out.printf (WHITE + "  Execution Time  : " + RESET + CYAN  + "%dm %ds%n"   + RESET, mins, secs);
		System.out.printf (WHITE + "  Distance        : " + RESET + CYAN  + "%.1f cm%n"   + RESET, totalDistance);
		System.out.printf (WHITE + "  Total Obstacles : " + RESET + RED   + "%d%n"        + RESET, totalObstacleCount);
		System.out.printf (WHITE + "  Navigation Cycles: " + RESET + CYAN + "%d%n"        + RESET, cycleCount);
		System.out.println(WHITE + "  ─────────────────────────────────────────" + RESET);
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


