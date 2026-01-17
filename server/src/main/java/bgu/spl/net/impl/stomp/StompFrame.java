package bgu.spl.net.impl.stomp;
import java.util.HashMap;
import java.util.Map;

public class StompFrame {

    public static final int SER_CONNECTED = 1;
    public static final int SER_MESSAGE = 2;
    public static final int SER_RECEIPT = 3;
    public static final int SER_ERROR = 4;
    public static final int CLI_CONNECT = 5;
    public static final int CLI_SEND = 6;
    public static final int CLI_SUBSCRIBE = 7;
    public static final int CLI_UNSUBSCRIBE = 8;
    public static final int CLI_DISCONNECT = 9;
    public static final int UNSUPPORTED = -1;

    private int command;
    private HashMap<String, String> headers;
    private String body;

    public StompFrame(String rawFrame){
        headers = new HashMap<>();
        int cmdEnd = rawFrame.indexOf('\n');
        if (cmdEnd == -1) {
            cmdEnd = rawFrame.length(); // Handle case with no newline
        }
        // Trim removes accidental whitespace (e.g., " SEND ")
        String cmdString = rawFrame.substring(0, cmdEnd).trim();
        if (cmdEnd < rawFrame.length()) {
            rawFrame = rawFrame.substring(cmdEnd + 1);
        } else {
            rawFrame = "";
        }
        
        switch (cmdString) {
            case "CONNECTED":
                command = SER_CONNECTED;
                break;

            case "MESSAGE":
                command = SER_MESSAGE;
                break;
            
            case "RECEIPT":
                command = SER_RECEIPT;
                break;

            case "ERROR":
                command = SER_ERROR;
                break;

            case "CONNECT":
                command = CLI_CONNECT;
                break;

            case "SEND":
                command = CLI_SEND;
                break;

            case "SUBSCRIBE":
                command = CLI_SUBSCRIBE;
                break;

            case "UNSUBSCRIBE":
                command = CLI_UNSUBSCRIBE;
                break;

            case "DISCONNECT":
                command = CLI_DISCONNECT;
                break;
        
            default:
                command = UNSUPPORTED;
                break;
        }

        while(!rawFrame.isEmpty() && rawFrame.charAt(0) != '\n'){//isEmpty to prevent crash on empty lines
            int endHeaderName = rawFrame.indexOf(':');
            int endHeader = rawFrame.indexOf('\n');
            // If headers are malformed, stop parsing to avoid crash
            if (endHeaderName == -1 || endHeader == -1) {
                break; 
            }
            String headerName = rawFrame.substring(0, endHeaderName);
            String headerValue = rawFrame.substring(endHeaderName + 1, endHeader);
            headers.put(headerName, headerValue);
            if (endHeader + 1 < rawFrame.length()) {
                rawFrame = rawFrame.substring(endHeader + 1);
            } 
            else {
                rawFrame = "";
                break;
            }
        }
        if (!rawFrame.isEmpty() && rawFrame.charAt(0) == '\n') {//skip the empty line
            rawFrame = rawFrame.substring(1);
        }
        int nullIndex = rawFrame.indexOf('\u0000');//find the null terminator and extract body accordingly
        if (nullIndex != -1) {
            body = rawFrame.substring(0, nullIndex);
        } else {
            body = rawFrame;
        }
    }


    public StompFrame(int command, HashMap<String, String> headers, String body){
        if(command < 1 || command > 9)
            command = -1;
        else
            this.command = command;
        this.headers = headers;
        this.body = body;
    }

    public int getCommand(){
        return command;
    }

    public boolean needsReceipt(){
        if(headers != null)
            return headers.containsKey("receipt");
        return false;
    }

    public HashMap<String, String> getHeaders(){
        return headers;
    }

    public String getBody(){
        return body;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        switch (command) {
            case SER_CONNECTED:
                sb.append("CONNECTED\n");
                break;

            case SER_MESSAGE:
                sb.append("MESSAGE\n");
                break;

            case SER_RECEIPT:
                sb.append("RECEIPT\n");
                break;

            case SER_ERROR:
                sb.append("ERROR\n");
                break;

            case CLI_CONNECT:
                sb.append("CONNECT\n");
                break;

            case CLI_SEND:
                sb.append("SEND\n");
                break;

            case CLI_SUBSCRIBE:
                sb.append("SUBSCRIBE\n");
                break;

            case CLI_UNSUBSCRIBE:
                sb.append("UNSUBSCRIBE\n");
                break;

            case CLI_DISCONNECT:
                sb.append("DISCONNECT\n");
                break;

            default:
                break;
        }

        for (Map.Entry<String, String> header : headers.entrySet()) {
            String headerName = header.getKey();
            String headerValue = header.getValue();
            sb.append(headerName + ":" + headerValue + "\n");
        }
        sb.append("\n");
        if (body != null)
            sb.append(body);
        sb.append('\u0000');
        return sb.toString();
    }
}
