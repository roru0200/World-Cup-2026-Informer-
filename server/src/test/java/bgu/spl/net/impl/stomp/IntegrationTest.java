package bgu.spl.net.impl.stomp;

import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.srv.ConnectionsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {

    private ConnectionsImpl<StompFrame> connections;
    private Database database;

    @BeforeEach
    public void setUp() {
        // אנחנו משתמשים במימוש האמיתי הפעם!
        connections = new ConnectionsImpl<>();
        database = Database.getInstance();
    }

    // --- בדיקות אינטגרציה מלאות (כולל DB) ---

    @Test
    public void testRegisterNewUser() {
        String username = "user_" + UUID.randomUUID(); // שם ייחודי למניעת התנגשויות
        String password = "pass";
        int connectionId = generateId();

        // ניסיון ראשון - צריך להוסיף משתמש חדש
        LoginStatus status = connections.login(connectionId, username, password);
        assertEquals(LoginStatus.ADDED_NEW_USER, status, "First login should register new user");

        // וידוא שהמשתמש מחובר במערכת
        connections.disconnect(connectionId); // מנתקים כדי לנקות
    }

    @Test
    public void testLoginSuccessAfterLogout() {
        String username = "user_" + UUID.randomUUID();
        String password = "pass";
        int connId1 = generateId();
        int connId2 = generateId();

        // 1. הרשמה
        connections.login(connId1, username, password);
        
        // 2. התנתקות
        connections.disconnect(connId1);

        // 3. התחברות מחדש (עם ID חדש)
        LoginStatus status = connections.login(connId2, username, password);
        assertEquals(LoginStatus.LOGGED_IN_SUCCESSFULLY, status, "Should login successfully after logout");
        
        connections.disconnect(connId2);
    }

    @Test
    public void testDoubleLoginFail() {
        String username = "user_" + UUID.randomUUID();
        int connId1 = generateId();
        int connId2 = generateId();

        // משתמש 1 מתחבר
        connections.login(connId1, username, "1234");

        // משתמש 2 מנסה להתחבר לאותו שם משתמש (בזמן שהראשון מחובר)
        LoginStatus status = connections.login(connId2, username, "1234");
        assertEquals(LoginStatus.ALREADY_LOGGED_IN, status, "Should not allow double login");

        connections.disconnect(connId1);
    }

    @Test
    public void testWrongPassword() {
        String username = "user_" + UUID.randomUUID();
        int connId1 = generateId();
        int connId2 = generateId();

        // הרשמה עם סיסמה א'
        connections.login(connId1, username, "correctPass");
        connections.disconnect(connId1);

        // ניסיון התחברות עם סיסמה ב'
        LoginStatus status = connections.login(connId2, username, "wrongPass");
        assertEquals(LoginStatus.WRONG_PASSWORD, status, "Should detect wrong password");
    }

    @Test
    public void testClientAlreadyConnectedFail() {
        // בודק מצב שבו אותו Client (אותו ConnectionHandler ID) מנסה לעשות לוגין פעמיים
        // זה קורה אם שולחים שני פריימים של CONNECT אחד אחרי השני
        String user1 = "user_" + UUID.randomUUID();
        String user2 = "user_" + UUID.randomUUID();
        int connId = generateId();

        connections.login(connId, user1, "pass");
        
        // אותו חיבור מנסה להתחבר שוב (אפילו עם שם אחר)
        LoginStatus status = connections.login(connId, user2, "pass");
        assertEquals(LoginStatus.CLIENT_ALREADY_CONNECTED, status, "Connection ID cannot login twice");

        connections.disconnect(connId);
    }

    // --- עזרים ---
    private static int idCounter = 0;
    private int generateId() {
        return ++idCounter;
    }
}