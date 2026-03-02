
public class Testing {

	public static void main(String[] args) throws InterruptedException {
		long start = System.currentTimeMillis();
		Thread.sleep(2);
		long end = System.currentTimeMillis();
		System.out.println(start);
		System.out.println(end);
		System.out.println(end-start);
	}

}
