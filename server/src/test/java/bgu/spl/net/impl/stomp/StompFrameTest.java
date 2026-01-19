package bgu.spl.net.impl.stomp;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

public class StompFrameTest {

    @Test
    public void testFrameToString() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("key1", "value1");
        String body = "Hello Body";
        
        // שימוש ב-CLI_SEND שהוא int
        StompFrame frame = new StompFrame(StompFrame.CLI_SEND, headers, body);
        String raw = frame.toString();

        assertTrue(raw.startsWith("SEND\n"), "Should start with command");
        assertTrue(raw.contains("key1:value1\n"), "Should contain header");
        assertTrue(raw.endsWith("\nHello Body\u0000"), "Should end with body and null char");
    }
}