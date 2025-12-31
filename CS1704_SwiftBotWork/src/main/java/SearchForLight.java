import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;

import swiftbot.Button;
import swiftbot.ImageSize;
import swiftbot.SwiftBotAPI;

public class SearchForLight {
	public static SwiftBotAPI swiftBot;		
	static boolean standBy = true;
	static boolean exit = false;
	static LightAnalyzer analyzer = new LightAnalyzer();
	static ObstacleDetector detector = new ObstacleDetector();
	static FileHandler fileHandler = new FileHandler();
	static int[] sections;
	static int[] threshold;
	static boolean terminate = false;
	public static double obstacleDistance;
	static boolean obstacleFound;
	static int obstacleCount = 0;
	static int brightestIntensity = 0;

	public static void main(String[] args) {
		//Initialize the SwiftBotAPI with exception
		try {
			swiftBot = SwiftBotAPI.INSTANCE;
		} catch (Exception e) {
			System.out.println("\nI2C disabled!");
			System.exit(5);
		}

		System.out.print(
				"==================================================\r\n"
						+ "           SWIFTBOT: SEARCH FOR LIGHT\r\n"
						+ "=================================================="
				);

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

		swiftBot.disableAllButtons();
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

	public static void CoreLoop() {
		while (!terminate) {
			//Take Picture
			BufferedImage img = swiftBot.takeStill(ImageSize.SQUARE_720x720);		
			sections = analyzer.calculateSectionIntensities(img); 

			//Obstacle Detection
			obstacleFound = detector.checkObstacles();

			if (obstacleFound) {
				obstacleCount += 1;
				//save picture into directory
				fileHandler.saveImage(img);

			}
			else {

			}

			if (obstacleCount >5) { //add 5 minute condition
				terminate = true;
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

}

class ObstacleDetector {
	SwiftBotAPI swiftBot = SearchForLight.swiftBot;
	double obstacleDistance= SearchForLight.obstacleDistance;

	public boolean checkObstacles() {
		obstacleDistance = swiftBot.useUltrasound();
		if (obstacleDistance>50) {return false;}
		else {return true;}
	}
}

class FileHandler {

	public void saveImage(BufferedImage img) {
		if (img == null) {
			System.out.println("Error: Image is Null!");
		}
		else {
			String directoryPath = "/data/home/pi";
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

	public static File findAvailableFilename(String directoryPath, String baseName, String extension) {
		File directory = new File(directoryPath);

		// Create directory if it doesn't exist
		if (!directory.exists()) {
			directory.mkdirs();
		}

		// Check base filename first
		File file = new File(directory, baseName + "." + extension);
		if (!file.exists()) {
			return file;
		}

		// If base exists, find next available number
		int counter = 1; //replace counter with global obstacleCount
		while (counter<=5) {
			String filename = String.format("%s_%d.%s", baseName, counter, extension);
			file = new File(directory, filename);

			if (!file.exists()) {
				System.out.println("Found available filename: " + filename);
				return file;
			}
			counter++;
		}
		throw new RuntimeException("5 Images already present!");
	}
}

class Movement {
	SwiftBotAPI swiftBot = SearchForLight.swiftBot;
	int[] sections = SearchForLight.sections;
	public void go(int direction) {
		switch (direction) {
		case 0:	
			break;
		case 1:
			break;
		case 2:
			break;
		default:
			break;
		}
	}
}