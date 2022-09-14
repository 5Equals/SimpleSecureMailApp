/**
 * Just a simple e-mail app that has a secure connection to manage mails.
 */

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class SMTPAndIMAP {
    public static void main(String[] args) {
        String username = args[0];
        String password = args[1];

        smtpTask(username, password);
        imapTask(username, password);
    }

    static void smtpTask(String username, String password) {
        System.out.println("\n----- SMTP -----\n");
        String response = "";

        SMTPHandler smtpHandler = new SMTPHandler(username, password);

        response = smtpHandler.sendMessage(username, "Hello this is a test message for sending a mail!!");
        System.out.println(response);

        smtpHandler.closeConnection();
    }

    static void imapTask(String username, String password) {
        System.out.println("\n----- IMAP -----\n");
        String response = "";
        IMAPHandler imapHandler = new IMAPHandler(username, password);

        response = imapHandler.selectBox("INBOX");
        System.out.println(response);

        response = imapHandler.listContent(100);
        System.out.println(response);

        response = imapHandler.fetchRecentMail();
        System.out.println(response);

        imapHandler.closeConnection();
    }
}

// Responsible to handle the smtp protocol from start to finish for KTH webmail server.
class SMTPHandler {
    private Socket socket;
    private SSLSocket secureSocket;
    private InputStream receiver;
    private OutputStream sender;
    private String username;
    private String password;
    private Base64.Encoder base64Encoder;
    private Base64.Decoder base64Decoder;

    SMTPHandler(String username, String password) {
        this.base64Encoder = Base64.getEncoder();
        this.base64Decoder = Base64.getDecoder();
        this.username = username;
        this.password = password;

        getNormalConnection();
        startHELLO();
        getSecureConnection();
        loginUser();
    }

    // Get the response that comes from the server when executing a query, specifying what to wait for.
    private String getResponses(String waitContent)  {
        try {
            int maxBufferSizePerIteration = 32 * 1024; // Chunk of 32KB
            StringBuilder requestData = new StringBuilder();

            while (true) {
                byte[] data = new byte[maxBufferSizePerIteration];
                int recvLen = this.receiver.read(data);
                String response;
                if (recvLen != -1) {
                    response = new String(data, 0, recvLen, StandardCharsets.UTF_8);
                    // The query results are done
                    if (response.contains(waitContent))
                    {
                        requestData.append(response);
                        break;
                    } else { // There is data more to come
                        response = new String(data, 0, recvLen, StandardCharsets.UTF_8);
                        requestData.append(response);
                    }
                } else {
                    break;
                }
            }

            return requestData.toString();
        } catch (IOException ioException) {
            ioException.printStackTrace();
            return "There is no data to receive";
        }
    }

    // Send the query to the server.
    private void sendCommands(String cmd) {
        try {
            this.sender.write(cmd.getBytes(StandardCharsets.UTF_8));
            this.sender.write("\r\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    // Get a normal socket(connection) with the server.
    private void getNormalConnection() {
        try {
            Socket socket = new Socket("smtp.kth.se", 587);
            this.socket = socket;
            System.out.println("\n" + this.socket);

            getNormalInputAndOutputStreams();
            System.out.println(getResponses("Spammers be gone"));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    // Get the streams of the normal socket, so we can send and receive data form the server.
    private void getNormalInputAndOutputStreams() {
        try {
            if (this.socket != null) {
                this.receiver = this.socket.getInputStream();
                this.sender = this.socket.getOutputStream();
                return;
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        this.receiver = null;
        this.sender = null;
    }

    // Get a secure socket(connection) with the server.
    private void getSecureConnection() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            SSLSocketFactory factory = sslContext.getSocketFactory();
            SSLSocket secureSocket = (SSLSocket) factory.createSocket(this.socket, null, this.socket.getPort(), false);
            secureSocket.startHandshake();

            this.secureSocket = secureSocket;
            System.out.println(this.secureSocket);
            System.out.println(this.secureSocket.getSession() + "\n");

            getSecureInputAndOutputStreams();
        } catch (IOException | KeyManagementException | NoSuchAlgorithmException ioException) {
            ioException.printStackTrace();
        }
    }

    // Get the streams of the secure socket, so we can send and receive data form the server.
    private void getSecureInputAndOutputStreams() {
        try {
            if (this.secureSocket != null) {
                this.receiver = this.secureSocket.getInputStream();
                this.sender = this.secureSocket.getOutputStream();
                return;
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        this.receiver = null;
        this.sender = null;
    }

    // Communicate with the server to initiate a secure socket.
    private void startHELLO() {
        sendCommands("EHLO g42.com");
        getResponses("250-STARTTLS");
        sendCommands("STARTTLS");
        getResponses("Ready to start TLS");
    }

    // Login the user into the server.
    private void loginUser() {
        String cmd = "EHLO g42.com";
        sendCommands(cmd);
        getResponses("250 DSN");
        cmd = "AUTH LOGIN";
        sendCommands(cmd);
        getResponses("334 VXNlcm5hbWU6");
        sendCommands(this.base64Encoder.encodeToString(this.username.getBytes(StandardCharsets.UTF_8)));
        getResponses("334 UGFzc3dvcmQ6");
        sendCommands(this.base64Encoder.encodeToString(this.password.getBytes(StandardCharsets.UTF_8)));
        System.out.println(getResponses("Authentication successful"));
    }

    String sendMessage(String receiver, String message) {
        String response = "";
        String cmd = String.format("MAIL FROM:<%s@kth.se>", this.username);
        sendCommands(cmd);
        getResponses("Ok");
        cmd = String.format("RCPT TO:<%s@kth.se>", receiver);
        sendCommands(cmd);
        getResponses("Ok");
        cmd = "DATA";
        sendCommands(cmd);
        getResponses("End data with");
        cmd = message + "\r\n" + "."; // crlf + . + crlf, the last crlf exists in the send commands method.
        sendCommands(cmd);
        return getResponses("Ok: queued");
    }

    // Close the sockets with the server when finishing up.
    void closeConnection() {
        String cmd = "QUIT";
        sendCommands(cmd);
        String response = getResponses("Bye");
        System.out.println(response);

        try {
            if (this.sender != null) {
                this.sender.close();
            }
            if (this.receiver != null) {
                this.receiver.close();
            }
            if (this.socket != null) {
                this.socket.close();
            }
            if (this.secureSocket != null) {
                this.secureSocket.close();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}

// Responsible to handle the imap protocol from start to finish for KTH webmail server.
class IMAPHandler {
    private SSLSocket secureSocket;
    private InputStream receiver;
    private OutputStream sender;
    private String username;
    private String password;
    private int totalMails;


    IMAPHandler(String username, String password) {
        this.username = username;
        this.password = password;
        this.totalMails = 0;
        getConnection();
    }

    // Get the response that comes from the server when executing a query.
    private String getResponses()  {
        try {
            int maxBufferSizePerIteration = 32 * 1024; // Chunk of 32KB
            StringBuilder requestData = new StringBuilder();

            while (true) {
                byte[] data = new byte[maxBufferSizePerIteration];
                int recvLen = this.receiver.read(data);
                String response;
                if (recvLen != -1) {
                    response = new String(data, 0, recvLen, StandardCharsets.UTF_8);
                    // The query results are done
                    if (response.contains("OK") || response.contains("NO") || response.contains("BAD"))
                    {
                        requestData.append(response);
                        break;
                    } else { // There is data more to come
                        response = new String(data, 0, recvLen, StandardCharsets.UTF_8);
                        requestData.append(response);
                    }
                } else {
                    break;
                }
            }

            return requestData.toString();
        } catch (IOException ioException) {
            ioException.printStackTrace();
            return "There is no data to receive";
        }
    }

    // Send the query to the server.
    private void sendCommands(String cmd) {
        try {
            this.sender.write("g42 ".getBytes(StandardCharsets.UTF_8));
            this.sender.write(cmd.getBytes(StandardCharsets.UTF_8));
            this.sender.write("\r\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    // Get a secure socket(connection) with the server and login into the desired account.
    private void getConnection() {
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket secureSocket = (SSLSocket) factory.createSocket("webmail.kth.se", 993);
            secureSocket.startHandshake();

            this.secureSocket = secureSocket;
            System.out.println("\n" + this.secureSocket);
            System.out.println(this.secureSocket.getSession() + "\n");

            getInputAndOutputStreams();
            getResponses();

            String login = String.format("LOGIN %s %s", this.username, this.password);
            System.out.println(sendQuery(login));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    // Get the streams of the secure socket, so we can send and receive data form the server.
    private void getInputAndOutputStreams() {
        try {
            if(this.secureSocket != null) {
                this.receiver = this.secureSocket.getInputStream();
                this.sender = this.secureSocket.getOutputStream();
                return;
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        this.receiver = null;
        this.sender = null;
    }

    // Send the command to the server.
    private String sendQuery(String cmd) {
        sendCommands(cmd);
        return getResponses();
    }

    // Get the number of email that are present in the selected mailbox.
    private int getEmailCount(String response) {
        String count = response.split("\n")[0];
        count = count.split(" ")[1];
        return Integer.parseInt(count);
    }

    // Select the mailbox that is desired.
    String selectBox(String box) {
        String selectBox = String.format("SELECT %s", box);
        String response =  sendQuery(selectBox);
        this.totalMails = getEmailCount(response);
        return response;
    }

    // List the content(subject and sender) of the specified number of mails.
    String listContent(int count) {
        String listContent = String.format("FETCH %d:%d (BODY.PEEK[HEADER.FIELDS (FROM SUBJECT)])", this.totalMails - count, totalMails);
        return sendQuery(listContent);
    }

    // Get the whole content of the recent mail received.
    String fetchRecentMail() {
        String fetchLastMail = String.format("FETCH %d (BODY[HEADER.FIELDS (FROM SUBJECT)] BODY[TEXT])", this.totalMails);
        return sendQuery(fetchLastMail);
    }

    // Close the socket with the server when finishing up.
    void closeConnection() {
        String cmd = "LOGOUT";
        sendCommands(cmd);
        System.out.println(getResponses());

        try {
            if(this.sender != null) {

                this.sender.close();
            }
            if(this.receiver != null) {
                this.receiver.close();
            }
            if(this.secureSocket != null) {
                this.secureSocket.close();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}