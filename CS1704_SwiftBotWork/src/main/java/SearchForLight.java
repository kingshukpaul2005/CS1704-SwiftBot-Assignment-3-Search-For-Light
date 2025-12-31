import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Iterator;

import swiftbot.Button;
import swiftbot.ImageSize;
import swiftbot.SwiftBotAPI;

public class SearchForLight {
	static SwiftBotAPI swiftBot;		
	static boolean standBy = true;

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

		System.out.println("Status: STANDBY");
		System.out.println("Action: Please press Button 'A' on the SwiftBot to begin...");

		//StandBy Loop: Until the button isnt pressed
		swiftBot.enableButton(Button.A, () -> {
			System.out.println("[Button 'A' Pressed]");
			standBy = false;
		});
		swiftBot.enableButton(Button.X, () -> {
			System.out.println("[Button 'X' Pressed]");
			swiftBot.disableAllButtons();
			System.exit(0);
			standBy = false;
		});
		
		while (standBy) { //make a time limit
			try { 
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
		swiftBot.disableAllButtons();
		
		//Environment Calibration
		LightAnalyzer analyzer = new LightAnalyzer();
		BufferedImage img = swiftBot.takeStill(ImageSize.SQUARE_720x720);		
		int [] sections = analyzer.calculateSectionIntensities(img); 
		System.out.println(sections[0]);
		System.out.println(sections[1]);
		System.out.println(sections[2]);
		
		System.exit(0);

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
