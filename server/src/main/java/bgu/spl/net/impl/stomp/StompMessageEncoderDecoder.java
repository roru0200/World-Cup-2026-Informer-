package bgu.spl.net.impl.stomp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class StompMessageEncoderDecoder implements MessageEncoderDecoder<StompFrame>{

    private byte[] buffer =  new byte[1 << 10];
    private int length = 0;
    
    
    @Override
    public StompFrame decodeNextByte(byte nextByte){
        if(nextByte == '\u0000')
            return popFrame();

        pushByte(nextByte);
        return null;
    }

    @Override
    public byte[] encode(StompFrame message){
        return message.toString().getBytes();
    }
    
    private void pushByte(byte nextByte) {
        if (length >= buffer.length) {
            buffer = Arrays.copyOf(buffer, length * 2);
        }

        buffer[length++] = nextByte;
    }

    private StompFrame popFrame() {
        //notice that we explicitly requesting that the string will be decoded from UTF-8
        //this is not actually required as it is the default encoding in java.
        String result = new String(buffer, 0, length, StandardCharsets.UTF_8);
        length = 0;
        return new StompFrame(result);
    }
}
