package bgu.spl.net.impl.stomp;

import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.srv.ConnectionsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class StompProtocolTest {

    private StompProtocolImp protocol;
    private MockConnections connections;
    private int connectionId = 1;

    @BeforeEach
    public void setUp() {
        protocol = new StompProtocolImp();
        connections = new MockConnections(); 
        protocol.start(connectionId, connections);
    }

    // --- בדיקות התחברות (CONNECT) ---

    @Test
    public void testConnectSuccess() {
        StompFrame connectFrame = createFrame("CONNECT");
        connectFrame.getHeaders().put("accept-version", "1.2");
        connectFrame.getHeaders().put("host", "stomp.cs.bgu.ac.il");
        connectFrame.getHeaders().put("login", "meni");
        connectFrame.getHeaders().put("passcode", "films");

        protocol.process(connectFrame);

        StompFrame response = connections.getLastSentMessage();
        assertNotNull(response, "Should send a response");
        assertEquals(StompFrame.SER_CONNECTED, response.getCommand());
        assertEquals("1.2", response.getHeaders().get("version"));
    }

    @Test
    public void testConnectFailWrongVersion() {
        StompFrame connectFrame = createFrame("CONNECT");
        connectFrame.getHeaders().put("accept-version", "1.1"); // גירסה לא נכונה
        connectFrame.getHeaders().put("host", "stomp.cs.bgu.ac.il");
        connectFrame.getHeaders().put("login", "meni");
        connectFrame.getHeaders().put("passcode", "films");

        protocol.process(connectFrame);

        StompFrame response = connections.getLastSentMessage();
        assertEquals(StompFrame.SER_ERROR, response.getCommand());
        assertTrue(response.getBody().contains("Invalid verison") || response.getBody().contains("version"), "Body should describe the error");
        assertTrue(protocol.shouldTerminate(), "Should terminate after error");
    }

    @Test
    public void testConnectFailMissingHeader() {
        StompFrame connectFrame = createFrame("CONNECT");
        // חסר accept-version
        connectFrame.getHeaders().put("host", "stomp.cs.bgu.ac.il");
        connectFrame.getHeaders().put("login", "meni");
        connectFrame.getHeaders().put("passcode", "films");

        protocol.process(connectFrame);

        StompFrame response = connections.getLastSentMessage();
        assertEquals(StompFrame.SER_ERROR, response.getCommand());
        assertTrue(response.getBody().contains("Missing") || response.getBody().contains("invalid"), "Should complain about missing header");
    }

    @Test
    public void testNotLoggedInAction() {
        // מנסים לשלוח הודעה לפני התחברות
        StompFrame sendFrame = createFrame("SEND");
        sendFrame.getHeaders().put("destination", "/topic/a");

        protocol.process(sendFrame);

        StompFrame response = connections.getLastSentMessage();
        assertEquals(StompFrame.SER_ERROR, response.getCommand());
        assertTrue(response.getBody().contains("login"), "Should require login");
    }

    // --- בדיקות הרשמה ושליחה (SUBSCRIBE / SEND) ---

    @Test
    public void testSubscribeAndSend() {
        login(); // מבצע לוגין תקין כהכנה

        // 1. הרשמה
        StompFrame subFrame = createFrame("SUBSCRIBE");
        subFrame.getHeaders().put("destination", "/topic/germany");
        subFrame.getHeaders().put("id", "78");
        protocol.process(subFrame);

        assertTrue(connections.subscribedChannels.contains("/topic/germany"), "Should call connections.subscribe");

        // 2. שליחה
        HashMap<String, String> headers = new HashMap<>();
        headers.put("destination", "/topic/germany");
        StompFrame sendFrame = new StompFrame(StompFrame.CLI_SEND, headers, "Hello World");
        
        protocol.process(sendFrame);

        // --- התיקון כאן: בודקים את sentMessages במקום את broadcastMessages ---
        assertFalse(connections.sentMessages.isEmpty(), "Should send message to subscribers");
        
        // וידוא שההודעה האחרונה שנשלחה היא אכן הודעה למנוי
        StompFrame lastMsg = connections.getLastSentMessage();
        assertEquals(StompFrame.SER_MESSAGE, lastMsg.getCommand());
        assertEquals("Hello World", lastMsg.getBody());
        assertEquals("sub-id-mock", lastMsg.getHeaders().get("subscription"), "Should attach the correct subscription ID");
    }

    // --- בדיקות קבלות (Receipts) ---

    @Test
    public void testReceipts() {
        login();

        StompFrame subFrame = createFrame("SUBSCRIBE");
        subFrame.getHeaders().put("destination", "/topic/brazil");
        subFrame.getHeaders().put("id", "99");
        subFrame.getHeaders().put("receipt", "1234"); // מבקש קבלה

        protocol.process(subFrame);

        StompFrame response = connections.getLastSentMessage();
        assertEquals(StompFrame.SER_RECEIPT, response.getCommand());
        assertEquals("1234", response.getHeaders().get("receipt-id"));
    }

    // --- בדיקות ניתוק (DISCONNECT) ---

    @Test
    public void testDisconnect() {
        login();

        StompFrame discFrame = createFrame("DISCONNECT");
        discFrame.getHeaders().put("receipt", "77");

        protocol.process(discFrame);

        StompFrame response = connections.getLastSentMessage();
        assertEquals(StompFrame.SER_RECEIPT, response.getCommand());
        assertEquals("77", response.getHeaders().get("receipt-id"));
        
        assertTrue(protocol.shouldTerminate(), "Protocol should terminate");
        assertTrue(connections.disconnected, "Should call connections.disconnect");
    }

    // --- עזרים ---

    private void login() {
        StompFrame connectFrame = createFrame("CONNECT");
        connectFrame.getHeaders().put("accept-version", "1.2");
        connectFrame.getHeaders().put("host", "stomp.cs.bgu.ac.il");
        connectFrame.getHeaders().put("login", "meni");
        connectFrame.getHeaders().put("passcode", "films");
        protocol.process(connectFrame);
        connections.sentMessages.clear(); // מנקה את התשובה כדי לא להפריע לטסט הבא
    }

    private StompFrame createFrame(String command) {
        int cmdInt = StompFrame.UNSUPPORTED;
        if (command.equals("CONNECT")) cmdInt = StompFrame.CLI_CONNECT;
        else if (command.equals("SUBSCRIBE")) cmdInt = StompFrame.CLI_SUBSCRIBE;
        else if (command.equals("UNSUBSCRIBE")) cmdInt = StompFrame.CLI_UNSUBSCRIBE;
        else if (command.equals("SEND")) cmdInt = StompFrame.CLI_SEND;
        else if (command.equals("DISCONNECT")) cmdInt = StompFrame.CLI_DISCONNECT;
        
        return new StompFrame(cmdInt, new HashMap<>(), null);
    }

    /**
     * מחלקת Mock ל-Connections
     */
    private static class MockConnections extends ConnectionsImpl<StompFrame> {
        List<StompFrame> sentMessages = new ArrayList<>();
        // broadcastMessages לא באמת בשימוש כי אתה שולח פרטנית, אבל השארתי למקרה הצורך
        List<StompFrame> broadcastMessages = new ArrayList<>();
        List<String> subscribedChannels = new ArrayList<>();
        boolean disconnected = false;

        @Override
        public boolean send(int connectionId, StompFrame msg) {
            sentMessages.add(msg);
            return true;
        }

        @Override
        public void send(String channel, StompFrame msg) {
            broadcastMessages.add(msg);
        }

        @Override
        public void disconnect(int connectionId) {
            disconnected = true;
        }

        @Override
        public LoginStatus login(int connectionId, String username, String password) {
            return LoginStatus.LOGGED_IN_SUCCESSFULLY; 
        }

        @Override
        public void subscribe(String channel, int connectionId, String subscriptionId) {
            subscribedChannels.add(channel);
        }

        @Override
        public void unsubscribe(String channel, int connectionId) {
            subscribedChannels.remove(channel);
        }

        @Override
        public ConcurrentHashMap<Integer, String> getSubscribers(String channel) {
             // מדמה מנוי קיים בערוץ כדי שהלולאה ב-processSend תרוץ ותשלח הודעה
             ConcurrentHashMap<Integer, String> subs = new ConcurrentHashMap<>();
             if (subscribedChannels.contains(channel)) {
                 subs.put(100, "sub-id-mock"); 
             }
             return subs;
        }
        
        public StompFrame getLastSentMessage() {
            if (sentMessages.isEmpty()) return null;
            return sentMessages.get(sentMessages.size() - 1);
        }
    }
}