import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.sql.*;

import org.apache.commons.io.IOUtils;

class TCPServer extends Thread {
    // server parameters
    final static String databaseFilename = "test2";
    final static int port = 3129;
    Connection connection;
    // internal variables
    Socket s;
    int num;

    /**
     * Try to connect to database, if there is no .db file - create one
     *
     * @return True if no error occurred during connection
     */
    private static boolean createDB(Connection connection) {
        //Connection connection = null;
        Statement statement = null;
        try {
            statement = connection.createStatement();
            // construct query
            String query = "CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT DEFAULT 1, message BLOB NOT NULL)";
            statement.executeUpdate(query);
            // commit changes
            statement.close();
            return true;
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Put byte array into new cell with auto-incremented index
     *
     * @param data Single chunk of data considered as one message
     * @return True if no error occurred during connection
     */
    synchronized private boolean insertData(byte[] data) {
        PreparedStatement statement = null;
        try {
            // construct query
            String query = "INSERT INTO messages (message) VALUES(?)";
            statement = connection.prepareStatement(query);
            statement.setBytes(1, data);
            statement.executeUpdate();
            // close connection to database
            connection.commit();
            statement.close();
            return true;
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete all entries from table "messages"
     * @return True if no error occurred during connection
     */
    synchronized private boolean clearTable() {
        Statement statement = null;
        try {
            statement = connection.createStatement();
            // construct query
            String query = "DELETE FROM messages WHERE id>=0";
            statement.executeUpdate(query);
            // commit changes
            connection.commit();
            statement.close();
            return true;
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Get all messages with id greater than givenIndex (excluding)
     *
     * @param fromIndex Index of message to start from (excluded)
     * @return Matrix of bytes[x][y] where x number of message, y offset inside
     * particular message
     */
    synchronized private byte[][] readData(int fromIndex) {
        PreparedStatement sizeStatement = null;
        PreparedStatement statement = null;
        try {
            // construct query for knowing size of resulting dataset
            String sizeQuery = "SELECT COUNT(*) AS number FROM messages WHERE id>?";
            sizeStatement = connection.prepareStatement(sizeQuery);
            sizeStatement.setInt(1, fromIndex);
            ResultSet sizeResult = sizeStatement.executeQuery();
            int size = sizeResult.getInt("number");
            sizeStatement.close();
            sizeResult.close();

            // break if there is no data
            if (size < 1) {
                System.out.println("No data, size: " + size);
                return null;
            }

            // construct query for getting data
            String query = "SELECT * FROM messages WHERE id>?";
            statement = connection.prepareStatement(query);
            statement.setInt(1, fromIndex);
            ResultSet resultData = statement.executeQuery();

            // initialize matrix for byte result
            byte[][] result = new byte[size][];

            // get data from query result
            int iterator = 0;
            while (resultData.next()) {
                int id = resultData.getInt("id");
                byte[] data = resultData.getBytes("message");
                result[iterator] = data;
                iterator++;
            }
            // close connection to database
            resultData.close();
            statement.close();
            return result;
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Start server
     *
     * @param args None required
     */
    public static void main(String[] args) {
        try {
            // counter of connections
            int i = 0;
            // create server
            ServerSocket server = new ServerSocket(port);
            Class.forName("org.sqlite.JDBC");
            Connection connection;
            // if database does not exist create it
            File dbFile = new File(databaseFilename + ".db");
            if (!dbFile.exists()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFilename + ".db");
                if (!createDB(connection)) {
                    System.exit(1);
                }
            } else {
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFilename + ".db");
            }
            connection.setAutoCommit(false);
            // start notification
            System.out.println("server is started");
            // infinite poll
            while (true) {
                new TCPServer(i, server.accept(), connection);
                i++;
            }
        } catch (Exception e) {
            System.out.println("init error: " + e);
        }
    }

    /**
     * The constructor for server
     *
     * @param num Connection index
     * @param s   Unified socket
     */
    public TCPServer(int num, Socket s, Connection connection) {
        this.num = num;
        this.s = s;
        this.connection = connection;
        setDaemon(true);
        setPriority(NORM_PRIORITY);
        start();
    }

    /**
     * Check received message for correctness
     *
     * @param data whole message
     * @return true if message is valid
     */
    public boolean validateBytes(byte[] data) {
        return true;
    }

    /**
     * Thread runner
     */
    public void run() {
        try {
            // as new connection appears, open i/o streams
            InputStream is = s.getInputStream();
            OutputStream os = s.getOutputStream();

            // receive message from client
            byte[] receivedBytes = IOUtils.toByteArray(is);
            // if message is only 4 bytes long that is probably number of messages
            if (receivedBytes.length == 1) {
                //try to clear table
                if(clearTable()){
                    //if no errors occurred reply with code:0
                    os.write(intToByteArray(0));
                }else{
                    //internal sql error => code:1
                    os.write(intToByteArray(1));
                }
            } else if (receivedBytes.length == 4) {
                // parse integer and get these messages
                byte[][] data = readData(new BigInteger(receivedBytes).intValue());
                // no new messages => error-byte 3
                if (data == null) {
                    os.write(intToByteArray(0));
                } else {
                    os.write(intToByteArray(data.length));
                    for (int i = 0; i < data.length; i++) {

                        os.write(intToByteArray(data[i].length));
                        os.write(data[i]);
                    }
                }
                System.out.println("Request from address: " + s.getInetAddress() + " , request with last index: "
                        + new BigInteger(receivedBytes).intValue() + " , sent: " + data.length + " messages.");
                // otherwise try to save new message in database
            } else {
                String stat = "Request from address: " + s.getInetAddress() + " , received message of size: "
                        + receivedBytes.length + " , ";
                if (!validateBytes(receivedBytes)) {
                    os.write(intToByteArray(2));
                    System.out.println(stat + "data incorrect.");
                } else if (!insertData(receivedBytes)) {
                    os.write(intToByteArray(1));
                    System.out.println(stat + "sql error.");
                } else {
                    System.out.println(stat + "fulfilled.");
                    os.write(intToByteArray(0));
                }
            }
            s.shutdownOutput();
            s.close();
        } catch (Exception e) {
            System.out.println("init error: " + e);
        }
    }

    private static byte[] intToByteArray(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }
}
