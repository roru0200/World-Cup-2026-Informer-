package bgu.spl.net.impl.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class StompMessageEncoderDecoderTest {

    private StompMessageEncoderDecoder encdec;

    @BeforeEach
    public void setUp() {
        encdec = new StompMessageEncoderDecoder();
    }

    @Test
    public void testEncode() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("key", "value");
        // שימוש בקבוע מספרי
        StompFrame frame = new StompFrame(StompFrame.CLI_SEND, headers, "BodyContent");

        byte[] encoded = encdec.encode(frame);
        String encodedStr = new String(encoded, StandardCharsets.UTF_8);

        assertTrue(encodedStr.startsWith("SEND\n"));
        assertTrue(encodedStr.contains("key:value\n"));
        assertTrue(encodedStr.endsWith("\nBodyContent\u0000"));
    }

    @Test
    public void testDecodeCompleteFrame() {
        String rawFrame = "CONNECT\naccept-version:1.2\n\n\u0000";
        byte[] bytes = rawFrame.getBytes(StandardCharsets.UTF_8);

        StompFrame result = null;
        for (byte b : bytes) {
            result = encdec.decodeNextByte(b);
        }

        assertNotNull(result);
        assertEquals(StompFrame.CLI_CONNECT, result.getCommand());
        assertEquals("1.2", result.getHeaders().get("accept-version"));
    }

    @Test
    public void testDecodePartialFrame() {
        String part1 = "SEND\ndes";
        String part2 = "tination:/topic/a\n\nBo";
        String part3 = "dy\u0000";

        assertNull(decodeString(part1), "Should not be ready yet");
        assertNull(decodeString(part2), "Should not be ready yet");
        
        StompFrame result = decodeString(part3); 
        assertNotNull(result, "Should be ready now");
        assertEquals(StompFrame.CLI_SEND, result.getCommand());
        assertEquals("/topic/a", result.getHeaders().get("destination"));
        assertEquals("Body", result.getBody());
    }
    
    @Test
    public void testDecodeTwoFramesInOneBatch() {
        String msg1 = "DISCONNECT\nreceipt:77\n\n\u0000";
        String msg2 = "SEND\ndestination:/a\n\nhello\u0000";
        
        byte[] allBytes = (msg1 + msg2).getBytes(StandardCharsets.UTF_8);
        
        StompFrame frame1 = null;
        StompFrame frame2 = null;
        
        for (byte b : allBytes) {
            StompFrame temp = encdec.decodeNextByte(b);
            if (temp != null) {
                if (frame1 == null) frame1 = temp;
                else frame2 = temp;
            }
        }
        
        assertNotNull(frame1);
        assertEquals(StompFrame.CLI_DISCONNECT, frame1.getCommand());
        
        assertNotNull(frame2);
        assertEquals(StompFrame.CLI_SEND, frame2.getCommand());
    }

    private StompFrame decodeString(String str) {
        StompFrame res = null;
        for (byte b : str.getBytes(StandardCharsets.UTF_8)) {
            res = encdec.decodeNextByte(b);
        }
        return res;
    }
}