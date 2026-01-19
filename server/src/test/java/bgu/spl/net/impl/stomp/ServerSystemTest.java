package bgu.spl.net.impl.stomp;

import bgu.spl.net.impl.echo.EchoProtocol;
import bgu.spl.net.impl.echo.LineMessageEncoderDecoder;
import bgu.spl.net.srv.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class ServerSystemTest {

    private Server<StompFrame> server;
    private Thread serverThread;
    private int port = 7779; // פורט שונה מהרגיל לטסטים

    @AfterEach
    public void tearDown() throws IOException {
        if (server != null) {
            server.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    // --- בדיקת שרת Thread Per Client (TPC) ---
    
    @Test
    public void testTPCServerFlow() throws Exception {
        // 1. אתחול והרצת שרת TPC
        startServer("tpc");

        // 2. התחברות כלקוח
        try (Socket client = new Socket("localhost", port);
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            // 3. שליחת CONNECT
            String username = "user_" + UUID.randomUUID();
            String connectFrame = "CONNECT\n" +
                    "accept-version:1.2\n" +
                    "host:stomp.cs.bgu.ac.il\n" +
                    "login:" + username + "\n" +
                    "passcode:pass\n" +
                    "\n\u0000";
            
            out.print(connectFrame);
            out.flush();

            // 4. קריאת CONNECTED
            String response = readFrame(in);
            assertTrue(response.contains("CONNECTED"), "Server should respond with CONNECTED");

            // 5. שליחת SUBSCRIBE עם קבלה
            String subFrame = "SUBSCRIBE\n" +
                    "destination:/topic/test\n" +
                    "id:1\n" +
                    "receipt:101\n" +
                    "\n\u0000";
            out.print(subFrame);
            out.flush();

            // 6. וידוא קבלת RECEIPT (מוכיח שההודעה עברה את ה-Handler והגיעה לפרוטוקול)
            response = readFrame(in);
            assertTrue(response.contains("RECEIPT"), "Should get receipt");
            assertTrue(response.contains("receipt-id:101"), "Receipt ID match");
        }
    }

    // --- בדיקת שרת Reactor ---

    @Test
    public void testReactorServerFlow() throws Exception {
        // 1. אתחול והרצת שרת Reactor (עם 3 תהליכונים)
        startServer("reactor");

        // 2. התחברות כלקוח
        try (Socket client = new Socket("localhost", port);
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            // 3. התחברות
            String username = "reactorUser_" + UUID.randomUUID();
            String connectFrame = "CONNECT\n" +
                    "accept-version:1.2\n" +
                    "host:stomp.cs.bgu.ac.il\n" +
                    "login:" + username + "\n" +
                    "passcode:pass\n" +
                    "\n\u0000";

            out.print(connectFrame);
            out.flush();

            String response = readFrame(in);
            assertTrue(response.contains("CONNECTED"), "Reactor should respond with CONNECTED");
            
            // 4. שליחת DISCONNECT וניתוק מסודר
            String disconnect = "DISCONNECT\nreceipt:999\n\n\u0000";
            out.print(disconnect);
            out.flush();
            
            response = readFrame(in);
            assertTrue(response.contains("RECEIPT"), "Should get receipt for disconnect");
        }
    }

    // --- עזרים ---

    private void startServer(String type) throws InterruptedException {
        // בחירת סוג השרת להרצה
        if (type.equals("tpc")) {
            server = Server.threadPerClient(
                    port,
                    () -> new StompProtocolImp(),
                    () -> new StompMessageEncoderDecoder()
            );
        } else {
            server = Server.reactor(
                    3,
                    port,
                    () -> new StompProtocolImp(),
                    () -> new StompMessageEncoderDecoder()
            );
        }

        // הרצה ב-Thread נפרד כדי לא לחסום את הטסט
        serverThread = new Thread(() -> server.serve());
        serverThread.start();
        
        // המתנה קצרה לוודא שהשרת עלה
        Thread.sleep(500);
    }

    // קריאת Frame שלם מה-Socket (עד לתו Null)
    private String readFrame(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\u0000') break; // סוף הפריים
            sb.append((char) ch);
        }
        return sb.toString();
    }
}