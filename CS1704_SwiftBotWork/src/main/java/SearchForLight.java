import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

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
	static boolean exit = false; //TestForGithubConnection
	static int[] sections;
	static ArrayList<Double[]> sectionLog = new ArrayList<Double[]>(); //done
	static int[] threshold;
	static long[] obstacleTimes = {-1,-1,-1,-1,-1}; //check
	static boolean terminate = false; //done
	public static double obstacleDistance; 
	static boolean obstacleFound = false;
	static int obstacleCount = 0; //done
	static int brightestIntensity = 0; //done
	static int direction;


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
		while (!terminate) {
			//Take Picture
			BufferedImage img = swiftBot.takeStill(ImageSize.SQUARE_720x720);		
			sections = analyzer.calculateSectionIntensities(img); 

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
				fileHandler.saveImage(img);
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
			}
			else {
				actions.setUnderLights(swiftBot, "green");
				direction = analyzer.getBrightestSection(sections);
				ui.movement(sections, direction);
				actions.go(swiftBot, direction);
			}
			if (obstacleCount >=5) { //add 5 minute condition
				long windowMs = 5*60*1000; //5 Minutes in Milliseconds
				if (obstacleTimes[4]-obstacleTimes[0] > windowMs) {
					terminate = true;
					terminate = termination();
				}
			}

			System.out.println(); // display 
		}
	}
	
	public static boolean termination() {
		return true;
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

	public void saveImage(BufferedImage img) {
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
				}
				else {
					System.err.println("Failed to write image file");
				}
			} catch (IOException e) {
				System.err.println("Error saving image: " + e.getMessage());
				e.printStackTrace();
			}
		}
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

		//		// Check base filename first
		//		if (!file.exists()) {
		//			return file;
		//		}

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
}


class SwiftBotActions {
	public void go(SwiftBotAPI swiftBot, int direction) {
		switch (direction) {
		case 0:	// left
			swiftBot.move(20, 80, 250);
			break;
		case 1: // forward
			swiftBot.move(80, 80, 1000);
			break;
		case 2: //right
			swiftBot.move(80, 20, 250);
			break;
		default:
			break;
		}
	}

	public void setUnderLights(SwiftBotAPI swiftBot, String colour) {
		int[] red = {255, 0, 0};
		int[] green = {0, 255, 0};
		int[] blue = {0, 0, 255};
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