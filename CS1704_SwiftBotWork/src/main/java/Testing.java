import swiftbot.SwiftBotAPI;

public class Testing {
	public static SwiftBotAPI swiftBot;		

	public static void main(String[] args) throws InterruptedException {
		try {
			swiftBot = SwiftBotAPI.INSTANCE;
		} catch (Exception e) {
			System.out.println("\nI2C disabled!");
			System.exit(5);
		}
		//30 degrees
		//swiftBot.move(-100, 100, 100);
		double value1 = swiftBot.useUltrasound();
		swiftBot.move(60, 60, 1000);
		double value2 = swiftBot.useUltrasound();
		swiftBot.move(60, 60, 1000);
		double value3 = swiftBot.useUltrasound();
		swiftBot.move(60, 60, 1000);
		double value4 = swiftBot.useUltrasound();
		//24cm
		
		System.out.println("Value 1:"+ (value1-value2));
		Thread.sleep(100);
		System.out.println("Value 2:"+ (value2-value3));
		Thread.sleep(100);
		System.out.println("Value 3:"+ (value3-value4));
		Thread.sleep(100);
		double avg = ((value1-value2)+(value2-value3)+(value3-value4))/3;
		System.out.println("The Average:"+ avg);
		
	}

}
