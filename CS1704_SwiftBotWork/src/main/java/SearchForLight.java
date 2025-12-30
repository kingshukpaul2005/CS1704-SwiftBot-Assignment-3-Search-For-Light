import swiftbot.Button;
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
		while (standBy) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
		System.out.println("Exit");
		
	}
}
