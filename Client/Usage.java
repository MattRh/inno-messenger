import java.util.Arrays;

public class Usage {

    public static void main(String[] args) {
        // Create client
        TCPClient client = new TCPClient("localhost", 3129);//138.197.176.233

        // Send bytes to server
        //System.out.println(client.sendMessage("Hello".getBytes()));

        //Request server to delete all messages
        //System.out.println(client.requestClear());

        // Get messages
        byte[][] result = client.getMessages(0);

        //System.out.println("result "+result);
        // print result
        if (result != null) {
            for (int i = 0; i < result.length; i++) {
                System.out.println(i + ") " + new String(result[i]));
            }
        }
    }
}
