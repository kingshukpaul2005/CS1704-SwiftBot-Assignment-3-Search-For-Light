import swiftbot.SwiftBotAPI;

public class SearchForLight {
	static SwiftBotAPI swiftBot;
	public static void main(String[] args) {
		try {
			swiftBot = SwiftBotAPI.INSTANCE;
		} catch (Exception e) {
			System.out.println("\nI2C disabled!");
			System.exit(5);
		}
	}

}
