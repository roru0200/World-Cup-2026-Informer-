package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.impl.stomp.*;

public class StompProtocolImp implements StompMessagingProtocol<StompFrame> {

    private boolean shouldTerminate = false;
    private int myId;
    private ConnectionsImpl<StompFrame> connections;
    private boolean isLoggedIn = false;
    private HashMap<String, String> subscriptionIds;
    private String recieptId = null;
    private int msgCounter;

    @Override
    public void start(int connectionId, Connections<StompFrame> connections){
        myId = connectionId;
        msgCounter = 0;
        this.connections = (ConnectionsImpl<StompFrame>)connections;
        subscriptionIds = new HashMap<>();
    }
    
    public void process(StompFrame message){
        recieptId = null;
        if(message.needsReceipt())
            recieptId = message.getHeaders().get("receipt");

        if(!isLoggedIn && message.getCommand() != StompFrame.CLI_CONNECT) //checks if logged in first
        {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("message", "user not logged in");
            String body = "You must login first";

            processError(headers, body);
        }

        else{
            boolean success = true;
            switch (message.getCommand()) {
                case StompFrame.CLI_CONNECT:
                    success = processConnect(message);
                    break;

                case StompFrame.CLI_SEND:
                    success = processSend(message);
                    break;

                case StompFrame.CLI_SUBSCRIBE:
                    success = processSubscribe(message);
                    break;

                case StompFrame.CLI_UNSUBSCRIBE:
                    success = processUnsubscribe(message);
                    break;

                case StompFrame.CLI_DISCONNECT:
                    processDisconnect();
                    break;

                default:
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("message", "Unknown or unsupported command");
                    processError(headers, "\n");
                    break;
            }
            if(success && recieptId != null ){ //check if needs receipt
                sendReceipt(recieptId);
            }
        }

    }

    private boolean processConnect(StompFrame message){
        HashMap<String, String> headers = message.getHeaders();

        if(!headers.containsKey("version") || !headers.get("version").equals("1.2")){
            HashMap<String, String> errorHeaders = new HashMap<>();
            errorHeaders.put("message", "malformed frame recieved");
            String body = "The message:\n ------\n" + message.toString() + "---------\n"
                        + "Missing or invalid verison header. could not login\n";
            processError(errorHeaders, body);
            return false;
        }

        else if(!headers.containsKey("host") || !headers.get("host").equals("stomp.cs.bgu.ac.il")){
            HashMap<String, String> errorHeaders = new HashMap<>();
            errorHeaders.put("message", "malformed frame recieved");
            String body = "The message:\n ------\n" + message.toString() + "---------\n"
                        + "Missing or invalid host header. could not login\n";
            processError(errorHeaders, body);
            return false;
        }
        //login check function
        else if(!login()){
            HashMap<String, String> errorHeaders = new HashMap<>();
            errorHeaders.put("message", "Invalid user name or password");
            processError(errorHeaders, "The message:\n ------\n" + message.toString() + "---------\n");
            return false;
        }

        else{
            connections.send(myId, createConnected(message));
            return true;
        }
    }

    private boolean processSend(StompFrame message){
        HashMap<String, String> headers = message.getHeaders();
        
        if(!headers.containsKey("destination")){
            HashMap<String, String> errorHeaders = new HashMap<>();
            errorHeaders.put("message", "malformed frame recieved");
            String body = "The message:\n ------\n" + message.toString() + "---------\n"
                        + "Did not contain a destination header which is REQUIRED for message propagation.\n";
            processError(errorHeaders, body);
            return false;
        }
        else{
            String destination = headers.get("destination");
            ConcurrentHashMap<Integer, String> subscribers = connections.getSubscribers(destination);
            
            if (subscribers != null) {
                for (Map.Entry<Integer, String> entry : subscribers.entrySet()) {
                    int subscriberConnId = entry.getKey();
                    String subscriberSubId = entry.getValue();
                    StompFrame outMsg = createMessage(message); 
                    outMsg.getHeaders().put("subscription", subscriberSubId);
                    connections.send(subscriberConnId, outMsg);
                }
            }
            return true;
        }
    }

    private boolean processSubscribe(StompFrame message){
        HashMap<String, String> headers = message.getHeaders();

        if(!headers.containsKey("destination") || !headers.containsKey("id")){
            HashMap<String, String> errorHeaders = new HashMap<>();
            errorHeaders.put("message", "malformed frame recieved");
            String body = "The message:\n ------\n" + message.toString() + "---------\n"
                        + "Did not contain a 'destination' and/or 'id' header which is REQUIRED for message propagation.\n";
            processError(errorHeaders, body);
            return false;
        }
        else if(message.getBody() != null && !message.getBody().isEmpty()){
            HashMap<String, String> errorHeaders = new HashMap<>();
            errorHeaders.put("message", "malformed frame recieved");
            String body = "A SUBSCRIPTION frame should not contain a body.\n";
            processError(errorHeaders, body);
            return false;
        }

        else{
            String channel = headers.get("destination");
            String channelId = headers.get("id");
            connections.subscribe(channel, myId, channelId);
            subscriptionIds.put(channelId, channel);
            return true;
        }
    }

    private boolean processUnsubscribe(StompFrame message){
        HashMap<String, String> headers = message.getHeaders();

        if(!headers.containsKey("id")){
            HashMap<String, String> errorHeaders = new HashMap<>();
            errorHeaders.put("message", "malformed frame recieved");
            String body = "The message:\n ------\n" + message.toString() + "---------\n"
                        + "Did not contain a 'id' header which is REQUIRED for message propagation.\n";
            processError(errorHeaders, body);
            return false;
        }
        else if(message.getBody() != null && !message.getBody().isEmpty()){
            HashMap<String, String> errorHeaders = new HashMap<>();
            errorHeaders.put("message", "malformed frame recieved");
            String body = "A UNSUBSCRIPTION frame should not contain a body.\n";
            processError(errorHeaders, body);
            return false;
        }

        else{
            String channelId = headers.get("id");
            String channel = subscriptionIds.get(channelId);
            if (channel != null){
                connections.unsubscribe(channel, myId);
                subscriptionIds.remove(channelId);
                return true;
            }
            else{//not sure if we need to be this strict
                HashMap<String, String> errorHeaders = new HashMap<>();
                errorHeaders.put("message", "malformed frame recieved");
                String body = "The message:\n ------\n" + message.toString() + "---------\n"
                            + "The subscription id provided does not exist.\n";
                processError(errorHeaders, body);
                return false;
            }
        }
    }
    

    private void processDisconnect(){
        connections.disconnect(myId);
        isLoggedIn = false;
        shouldTerminate = true;
    }

    private void processError(HashMap<String, String> headers, String body){
        if(recieptId != null)
            headers.put("receipt-id", recieptId);

        StompFrame error = new StompFrame(StompFrame.SER_ERROR, headers, body);
        connections.send(myId, error);
        processDisconnect();
    }

    private StompFrame createConnected(StompFrame message) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("version", "1.2");
        return new StompFrame(StompFrame.SER_CONNECTED, headers, "");
    }

    private StompFrame createMessage(StompFrame message) {
        HashMap<String, String> headers = message.getHeaders();
        HashMap<String, String> msgHeaders = new HashMap<>();

        msgHeaders.put("message-id",myId+ "." + Integer.toString(msgCounter++));
        msgHeaders.put("destination", headers.get("destination"));
        return new StompFrame(StompFrame.SER_MESSAGE, msgHeaders, message.getBody());
    }

    public String getSubscriptionID(String channel){
        for (String key : subscriptionIds.keySet()) {
            if (subscriptionIds.get(key).equals(channel)) {
                return key;
            }
        }
        return null;
    }


    private void sendReceipt(String recieptId) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("receipt-id", recieptId);
        connections.send(myId, new StompFrame(StompFrame.SER_RECEIPT, headers, "\n"));
    }

    private boolean login() {//placeholder
        isLoggedIn = true;
        return true;
    }
	
	@Override
    public boolean shouldTerminate(){
        return shouldTerminate;
    };
    
}
