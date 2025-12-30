import java.awt.Color;
import java.awt.image.BufferedImage;

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
		while (standBy) { //make a time limit
			try { 
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}

		//Environment Calibration
		LightAnalyzer analyzer = new LightAnalyzer();
		BufferedImage img = swiftBot.takeStill(ImageSize.SQUARE_720x720);		
		int [] sections = analyzer.calculateSectionIntensities(img); 
		System.exit(0);

	}
}

class LightAnalyzer {
	public int[] calculateSectionIntensities(BufferedImage img) {
		int[] sections = {0,0,0};
		//Can do img.getWidth and getHeight however using 720x720 as total
		
		return sections;

	}
	private int getLuminance(int rgb) {
		Color c = new Color(rgb);
		c.getBlue();
		c.getRed();
		c.getGreen();
		return 0;
	}

}
