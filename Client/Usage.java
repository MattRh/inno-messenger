
public class Usage {

	public static void main(String[] args) {
		// Create client
		TCPClient client = new TCPClient("localhost", 3128);

		// Send bytes to server
		client.sendMessage(new byte[800000000]);

		// Get messages
		byte[][] result = client.getMessages(0);

		// print result
		if (result == null) {
			System.exit(0);
		}
		for (int i = 0; i < result.length; i++) {
			System.out.println(result[i].length);
		}
	}
}
