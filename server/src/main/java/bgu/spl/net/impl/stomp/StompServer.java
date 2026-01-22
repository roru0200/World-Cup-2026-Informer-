package bgu.spl.net.impl.stomp;
import java.util.function.Supplier;

import bgu.spl.net.impl.stomp.*;
import bgu.spl.net.srv.*;
import bgu.spl.net.api.*;

public class StompServer {

    public static void main(String[] args) {
        // TODO: implement this
        int port;
        try{
            port = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException e){
            System.out.println("Invalid port number input");
            return;
        }
        String serverLogic = args[1];

        int numOfThreads = 4; //unknown requirments placeholder

        Server<StompFrame> ser = null;

        switch (serverLogic) {
            case "tpc":
                ser = Server.threadPerClient(
                    port,
                    () -> new StompProtocolImp(),
                    () -> new StompMessageEncoderDecoder()
                );
                break;
                
            case "reactor":
                ser = Server.reactor(
                    numOfThreads,
                    port,
                    () -> new StompProtocolImp(),
                    () -> new StompMessageEncoderDecoder()
                );
                break;
            default:
                System.out.println("Invalid server logic input");
                break;
        }

        ser.serve();
        
    }
}
