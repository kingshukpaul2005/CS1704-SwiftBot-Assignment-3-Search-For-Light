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
	public static int[] threshold;
	static long[] obstacleTimes = {-1,-1,-1,-1,-1}; //check
	public static double obstacleDistance; 
	static boolean obstacleFound = false;
	static int obstacleCount = 0; //done
	static int brightestIntensity = 0; //done
	static int direction;

	static long startTime;
	static ArrayList<String> movementLog = new ArrayList<>();
	static double totalDistance = 0;
	public static ArrayList<String> imageLog = new ArrayList<>();
	static int totalObstacleCount = 0;
	static Scanner sc = new Scanner(System.in);
	final double FORWARD_DISTANCE_CM = 15.0;



	public static void main(String[] args) throws InterruptedException {
		//Initialize the SwiftBotAPI with exception
		try {
			swiftBot = SwiftBotAPI.INSTANCE;
		} catch (Exception e) {
			System.out.println("\nI2C disabled!");
			System.exit(5);
		}
		fileHandler.clearObstaclesDirectory("/data/home/pi/Obstacles");

		System.out.printf("==================================================%n"
				+ "           SWIFTBOT: SEARCH FOR LIGHT             %n"
				+ "==================================================%n");

		//STANDBY LOOP
		System.out.println("Status: STANDBY");
		System.out.println("Action: Please press Button 'A' on the SwiftBot to begin...");


		swiftBot.enableButton(Button.X, () -> {
			System.out.println("[Button 'X' Pressed]");
			exit = true;
			standBy = false;
			swiftBot.disableUnderlights();
			System.exit(0);
		});

		swiftBot.enableButton(Button.A, () -> {
			System.out.println("[Button 'A' Pressed]");
			standBy = false;
		});

		while (standBy) { //make a time limit additional feature
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
		swiftBot.disableButton(Button.A);
		if (exit) {
			System.exit(0);
		}

		//Calibration
		EnvironmentalCalibration();

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
			System.out.println("==================================================");
			System.out.println("Log file saved to: " + logPath);
			System.out.println("==================================================");
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

		// Update Brightest Intensity
		int currentMax = sections[analyzer.getBrightestSection(sections)];
		if (currentMax > brightestIntensity) {
			brightestIntensity = currentMax;
		}
		return img;
	}

	public static boolean isWandering() {
		return sections[0] <= threshold[0] &&
				sections[1] <= threshold[1] &&
				sections[2] <= threshold[2];
	}

	public static boolean handleWandering(BufferedImage img, String[] directionNames) throws InterruptedException {
		System.out.println("No Light Source Detected. Wandering...");

		obstacleDistance = swiftBot.useUltrasound();
		System.out.println("DEBUG Distance: "+ obstacleDistance);
		if (obstacleDistance > 0 && obstacleDistance < 50) {
			return handleObstacle(img, directionNames, "Wander Obstacle Avoided");
		} else {
			int wanderDirection = (int)(Math.random() * 3);
			actions.wander(swiftBot, wanderDirection);
			movementLog.add("Wandering - " + directionNames[wanderDirection]);
			return false;
		}
	}

	public static boolean handleNormalMode(BufferedImage img, String[] directionNames) throws InterruptedException {
		obstacleDistance = swiftBot.useUltrasound();
		System.out.println("DEBUD distance: " + obstacleDistance);

		if (obstacleDistance > 0 && obstacleDistance < 50) {
			return handleObstacle(img, directionNames,"Obstacle Avoided");
		} else {
			actions.setUnderLights(swiftBot, "green");
			direction = analyzer.getBrightestSection(sections);
			ui.movement(sections, direction);
			actions.go(swiftBot, direction);
			movementLog.add(directionNames[direction]);
			if (direction==1) totalDistance += 15; 
			return false;
		}
	}

	public static boolean handleObstacle(BufferedImage img, String[] directionNames, String logLabel) throws InterruptedException{
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

		int brightestIndex = analyzer.getBrightestSection(sections);
		int avoidDirection = analyzer.getSecondBrightestIndex(sections, brightestIndex);
		if (avoidDirection == 1) {
			avoidDirection = (sections[0] >= sections[2]) ? 0 : 2;
		}

		System.out.println("Obstacle Detected! Distance: "+ obstacleDistance);
		ui.movement(sections, avoidDirection);
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
		System.out.println("Five Objects detrected within 5 minutes");
		System.out.println("Enter TERMINATE or CONTINUE: ");		
		String decision = sc.nextLine();
		while (!decision.equals(TERMINATE) && !decision.equals(CONTINUE)) {
			System.out.println("Enter valid input 'TERMINATE' or 'CONTINUE'");
			decision = sc.nextLine();
		}
		if (decision.equals(TERMINATE)) {
			return true;
		} else {
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

	public int getSecondBrightestIndex(int [] array, int excludedIndex) {
		int secondIndex = -1;
		for (int i = 0; i < array.length; i++) {
			if (i==excludedIndex) {
				continue;
			}
			if (secondIndex == -1 || array[i] > array[secondIndex]) {
				secondIndex = i;
			}
		}

		//Random Selection if remaining values are equal
		int[] remaining = new int[array.length-1];
		int[] remainingIndices = new int[array.length-1];
		int idx = 0;
		for (int i = 0; i < array.length; i++) {
			if (i !=excludedIndex) {
				remaining[idx] = array[i];
				remainingIndices[idx] = i;
				idx++;
			}
		}
		if (remaining[0] == remaining[1]) {
			return remainingIndices[(int)(Math.random()*2)];
		}
		return secondIndex;

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
		File file = new File(directory, baseName + "." + extension);

		// If base exists, find next available number
		int counter = 1; 
		while (true) {//removed upper limit
			String filename = String.format("%s_%d.%s", baseName, counter, extension);
			file = new File(directory, filename);

			if (!file.exists()) {
				return file;
			}
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
			/*
a)The starting threshold light intensity 
b) The brightest light intensity detected 
c) The number of times the SwiftBot detected light, intensity of light at each 
instance 
d) The duration of the execution 
e) Total distance travelled 
f) 
All movements of the SwiftBot in the order they were completed 
g) The number of obstacles encountered locations of images and log file
			 */
			//pw.println("The Starting Threshold Light Intensity: "+ threshold);

			pw.println("==================================================");
			pw.println("         SEARCH FOR LIGHT - SESSION LOG           ");
			pw.println("==================================================");
			pw.println();

			// Threshold
			pw.println("---Environment BaseLine (Threshold)---");
			pw.printf("  Left: %d | Centre: %d | Right: %d%n",
					threshold[0], threshold[1], threshold[2]);
			pw.println();

			// Brightest Intensity
			pw.println("---Brightest Intensity Detected---");
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
			for (int i = 0; i < movementLog.size(); i++) {
				Double[] s = sectionLog.get(i);
				pw.printf("  Cycle %d: Left=%.0f | Centre=%.0f | Right=%.0f%n",
						i + 1, s[0], s[1], s[2]);
				pw.printf("  %d. %s%n", i + 1, movementLog.get(i));
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
	public void go(SwiftBotAPI swiftBot, int direction) {
		switch (direction) {
		case 0:	// left
			swiftBot.move(-50, 50, 100); break;
		case 1: // forward
			swiftBot.move(60, 60, 1000); break;
		case 2: //right
			swiftBot.move(50, -50, 100); break;
		default:
			break;
		}
	}

	public void wander(SwiftBotAPI swiftBot, int direction) {
		switch (direction) {
		case 0: swiftBot.move(-50, 50, 200); break;  
		case 1: swiftBot.move(50, 50, 1000); break;  
		case 2: swiftBot.move(50, -50, 200); break;  
		}
	}

	public void avoid(SwiftBotAPI swiftBot, int direction) {
		switch (direction) {
		case 0: swiftBot.move(-50, 50, 600); break;  // longer turn left to clear obstacle
		case 2: swiftBot.move(50, -50, 600); break;  // longer turn right to clear obstacle
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
	public void movement(int[] sections, int direction) {
		System.out.println(sections[0] + "  " +sections[1] + "  " + sections[2]);
		System.out.println("Direction: " + direction);
	}
}

