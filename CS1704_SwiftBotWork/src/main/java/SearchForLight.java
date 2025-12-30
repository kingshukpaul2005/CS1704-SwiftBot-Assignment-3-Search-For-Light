import swiftbot.Button;
import swiftbot.SwiftBotAPI;

public class SearchForLight {
	static SwiftBotAPI swiftBot;
	public static void main(String[] args) {
		//Initialize the SwiftBotAPI with exception
		try {
			swiftBot = SwiftBotAPI.INSTANCE;
		} catch (Exception e) {
			System.out.println("\nI2C disabled!");
			System.exit(5);
		}

		//StandBy Loop: Until the button isnt pressed
		boolean standBy = true;
		swiftBot.enableButton(Button.A, () -> {standBy = false;});
		while (standBy) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
	}
}
