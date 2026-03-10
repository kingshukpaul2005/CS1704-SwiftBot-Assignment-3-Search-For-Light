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


	public static void main(String[] args) throws InterruptedException {
		System.out.println(System.currentTimeMillis());
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
			swiftBot.disableAllButtons();
			exit = true;
			standBy = false;
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

		System.exit(0);
	}

	public static void EnvironmentalCalibration() {
		//Environment Calibration
		BufferedImage img = swiftBot.takeStill(ImageSize.SQUARE_720x720);		
		threshold = analyzer.calculateSectionIntensities(img); 
	}

	public static void CoreLoop() throws InterruptedException {
		boolean terminate = false;
		startTime = System.currentTimeMillis();
		String[] directionNames = {"Left", "Straight", "Right"};
		final double FORWARD_DISTANCE_CM = 15.0; //placeholder value

		while (!terminate) {
			//Take Picture
			BufferedImage img = swiftBot.takeStill(ImageSize.SQUARE_720x720);		
			sections = analyzer.calculateSectionIntensities(img); 
			sectionLog.add(new Double[] {
					(double) sections[0],
					(double) sections[1],
					(double) sections[2]
			});

			//Update Brightest Intensity
			int currentMax = sections[analyzer.getBrightestSection(sections)];
			if (currentMax>brightestIntensity) {
				brightestIntensity = currentMax;
			}

			//Wandering Mode
			if (	sections[0] <= threshold[0] &&
					sections[1] <= threshold[1] &&
					sections[2] <= threshold[2]) {
				System.out.println("No Light Source Detected. Wandering...");
				int wanderDirection = (int) (Math.random()*3);
				actions.wander(swiftBot, wanderDirection);
				movementLog.add("wandering Direction - "+directionNames[direction]);
				continue; //skipping obstacle detection
			}

			//Obstacle Detection
			obstacleDistance = swiftBot.useUltrasound();
			if (obstacleDistance <= 0) {
				obstacleFound = false;} //Bad Reading //test
			else if (obstacleDistance<50) {
				obstacleFound=true;}
			else {
				obstacleFound=false;}

			if (obstacleFound) {
				obstacleCount += 1;

				// Shift all times left by one position
				obstacleTimes[0] = obstacleTimes[1];
				obstacleTimes[1] = obstacleTimes[2];
				obstacleTimes[2] = obstacleTimes[3];
				obstacleTimes[3] = obstacleTimes[4];
				// Store current time in the last slot
				obstacleTimes[4] = System.currentTimeMillis();

				// Save picture into directory
				String imagePath = fileHandler.saveImage(img);
				if (imagePath!=null) {
					imageLog.add(imagePath);
				}

				//move in second brightest direction
				for (int i = 0; i <3; i++) {
					actions.setUnderLights(swiftBot, "red");
					Thread.sleep(100);
					actions.setUnderLights(swiftBot, "blank");
				}
				int brightestIndex = analyzer.getBrightestSection(sections);
				direction = analyzer.getSecondBrightestIndex(sections, brightestIndex);

				ui.movement(sections, direction);
				System.out.println("Distance from object: "+ obstacleDistance);
				actions.go(swiftBot, direction);
				movementLog.add("Obstacle Avoided - "+directionNames[direction]);
			}
			else {
				actions.setUnderLights(swiftBot, "green");
				direction = analyzer.getBrightestSection(sections);
				ui.movement(sections, direction);
				actions.go(swiftBot, direction);
				movementLog.add(directionNames[direction]);
			}

			if (direction == 1) {
				totalDistance += FORWARD_DISTANCE_CM;
			}

			if (obstacleCount >=5) { //add 5 minute condition
				long windowMs = 5*60*1000; //5 Minutes in Milliseconds
				if (obstacleTimes[4]-obstacleTimes[0] < windowMs) {
					terminate = termination();
				}
			}

			System.out.println(); // display 
		}
		FileHandler.writeLog(threshold, brightestIntensity, startTime, totalDistance);
	}

	public static boolean termination() {
		final String TERMINATE = "TERMINATE";		
		final String CONTINUE = "CONTINUE";
		try (Scanner sc = new Scanner(System.in)) {
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
/*
class ObstacleDetector {
	double obstacleDistance= SearchForLight.obstacleDistance;

	public boolean checkObstacles(SwiftBotAPI swiftBot) {
		obstacleDistance = swiftBot.useUltrasound();
		if (obstacleDistance>50) {return false;}
		else {return true;}
	}
}
 */
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
		int counter = 1; //replace counter with global obstacleCount
		while (counter<=5) {
			String filename = String.format("%s_%d.%s", baseName, counter, extension);
			file = new File(directory, filename);

			if (!file.exists()) {
				//System.out.println("Found available filename: " + filename);
				return file;
			}
			counter++;
		}
		throw new RuntimeException("5 Images already present!");
	}

	public static File writeLog(
			int[] threshold,
			int brightestIntensity,
			long startTime,
			double totalDistance
			) {
		String directoryPath ="/data/home/pi";
		String baseName = "Logger";
		String extension = "txt";

		File outputFile = findAvailableFilename(directoryPath, baseName, extension);
		long durationMs = System.currentTimeMillis()- startTime;
		long durationSecs = durationMs/1000;
		int durationMins = (int) Math. floorDiv(durationSecs, 60) ;

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
			pw.printf("  %d seconds (%d ms)%n", durationMins, durationSecs);
			pw.println();

			//Total Distance
			pw.println("--- Total Distance Travelled ---");
			pw.printf("  %.1f cm%n", totalDistance);
			pw.println();

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

}


class SwiftBotActions {
	public void go(SwiftBotAPI swiftBot, int direction) {
		switch (direction) {
		case 0:	// left
			swiftBot.move(-50, 50, 200); break;
		case 1: // forward
			swiftBot.move(50, 50, 1000); break;
		case 2: //right
			swiftBot.move(50, -50, 200); break;
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

