import swiftbot.SwiftBotAPI;

public class Testing {
	public static SwiftBotAPI swiftBot;	
    static final String RESET   = "\u001B[0m";
    static final String RED     = "\u001B[31m";
    static final String GREEN   = "\u001B[32m";
    static final String YELLOW  = "\u001B[33m";
    static final String BLUE    = "\u001B[34m";
    static final String CYAN    = "\u001B[36m";
    static final String WHITE   = "\u001B[37m";
    static final String BOLD    = "\u001B[1m";
    static final String DIM     = "\u001B[2m";
    
	public static void main(String[] args) throws InterruptedException {
		//ASCII Art Title
        System.out.println(CYAN + BOLD+ "  ___ ___   _   ___  ___ _  _   ___ ___  ___   _    ___ ___ _  _ _____ ");
        System.out.println(" / __| __| /_\\ | _ \\/ __| || | | __/ _ \\| _ \\ | |  |_ _/ __| || |_   _|");
        System.out.println(" \\__ \\ _| / _ \\|   / (__| __ | | _| (_) |   / | |__ | | (_ | __ | | |  ");
        System.out.println(" |___/___/_/ \\_\\_|_\\\\___|_||_| |_| \\___/|_|_\\ |____|___\\___|_||_| |_|  ");
        System.out.println(RESET);
        
        System.out.println(WHITE + "======================================================================" + RESET);

        // Status
        System.out.println(BOLD + "Status: " + RESET + YELLOW + "STANDBY" + RESET);

        // Action prompts
        System.out.println(WHITE + "Action: " + RESET +
                           "Please press" + GREEN + BOLD + " Button 'A'" + RESET +
                           " on the SwiftBot to begin " + CYAN + "Search For Light" + RESET);

        // Additional actions
        System.out.println();
        System.out.println(DIM +BOLD+ WHITE + "Additional Actions" + RESET);
        System.out.println(GREEN + BOLD + "Button 'B'" + RESET +
                           ": " + YELLOW + "Search for Dark" + RESET);

        System.out.println(WHITE + "======================================================================" + RESET);
        
        String Button = "X";
        System.out.println(BLUE + "[Button '" + Button + "' Pressed]" + RESET);
		//		try {
		//			swiftBot = SwiftBotAPI.INSTANCE;
		//		} catch (Exception e) {
		//			System.out.println("\nI2C disabled!");
		//			System.exit(5);
		//		}
		//		//30 degrees
		//		//swiftBot.move(-100, 100, 100);
		//		double value1 = swiftBot.useUltrasound();
		//		swiftBot.move(60, 60, 1000);
		//		double value2 = swiftBot.useUltrasound();
		//		swiftBot.move(60, 60, 1000);
		//		double value3 = swiftBot.useUltrasound();
		//		swiftBot.move(60, 60, 1000);
		//		double value4 = swiftBot.useUltrasound();
		//		
		//		//24cm
		//		
		//		System.out.println("Value 1:"+ (value1-value2));
		//		Thread.sleep(100);
		//		System.out.println("Value 2:"+ (value2-value3));
		//		Thread.sleep(100);
		//		System.out.println("Value 3:"+ (value3-value4));
		//		Thread.sleep(100);
		//		double avg = ((value1-value2)+(value2-value3)+(value3-value4))/3;
		//		System.out.println("The Average:"+ avg);
		//		
		//		
	}

}
