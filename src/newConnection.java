import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class newConnection {
    private DatagramSocket socket;
    private InetAddress ip;
    private int port; // the port to send to
    private int portToBind; // the port to listen to
    private final byte SYN = 0b0000001;
    private final byte ACK = 0b0000010;
    private final byte FIN = 0b0000100;
    private final byte DATA =0b0001000;
    private final byte SYNACK = (byte) (this.SYN | this.ACK);
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicBoolean isInitiator = new AtomicBoolean(false);
    private final short signalRetryWaitMs = 2000; // how long till you send another signal
    private boolean connected = false;
    private final SecurityLayer securityLayer = new SecurityLayer();
    private AtomicBoolean ack = new AtomicBoolean(false);
    private SocketAddress initiatorAddress = null;

    public newConnection(String ip, int port,int portToBind) {
        try{
            this.ip = InetAddress.getByName(ip);
            this.port = port;
            this.portToBind = portToBind;
            this.socket = new DatagramSocket();
            this.isInitiator.set(true);
            socket.setSoTimeout(signalRetryWaitMs/4);
            System.out.println("Sending to " + this.ip+" On port "+this.port);

        }catch (UnknownHostException e){
            System.out.println("Unknown Host \n Input a valid IP address");
            throw new RuntimeException(e);
        }catch (SocketException e){
            System.out.println("Socket Error");
            throw new RuntimeException(e);
        }
    }


    public newConnection(int portToBind){ // this wil be the initial one where theure just listening for a connection.
        // bind to a port
        this.portToBind = portToBind;
        // set initiator to false, -- default nvm
    }

    ///packet anatomy [1st byte ]

    public boolean isListening(){
        return listening.get();
    }
    public void sendData(byte[] data) {
        // go through processes to send - full layers
        try {
            byte[] authenticatedPacket = securityLayer.createAuthenticatedPacket(data);
            DatagramPacket dataPacket = null;
            if(isInitiator.get()){ // if its an initiator , use the ip and socket you inputted
                dataPacket = new DatagramPacket(authenticatedPacket, authenticatedPacket.length, this.ip, this.port);
            }else { // otherwise get the address from  the syn request .
                dataPacket = new DatagramPacket(authenticatedPacket, authenticatedPacket.length,initiatorAddress);
            };
            socket.send(dataPacket);
        } catch (IOException e) {
            System.out.println("Error sending data");
            e.printStackTrace();
        }
    }
    public void sendEncrypted(byte[] plaintextData){
        try {
            byte[] cypherText = securityLayer.encrypt(plaintextData);
            ByteBuffer flagBuffer = ByteBuffer.allocate(1+cypherText.length).put(DATA).put(cypherText);
            sendData(flagBuffer.array());
        } catch (Exception e) {
            System.out.println("Error sending data");
        }
    }

    public newConnection listen(Consumer<byte[]> callback){
        try{
            this.socket = new DatagramSocket(this.portToBind); // bind to a port to get requests from there.
            Handshake();
            if (listening.get() && connected) {
                SocketListenerThread(callback).start();
            }
        }catch (Exception e){
            System.out.println("Error listening on port "+this.portToBind);
        }
        return this;
    }

    // [hmac[flag|encrypteddata]]
    private Thread SocketListenerThread(Consumer<byte[]> callback){
        return new Thread(()->{
            while (connected && listening.get()){ // while connected and listening,
                byte[] incomingDataEncrypted = ListenForEvent(DATA); // authenticates and strips data but dosent decrypt
                if (incomingDataEncrypted == null){
                    System.out.println("Incoming data encrypted is null");
                    continue;
                }
                callback.accept(securityLayer.decrypt(incomingDataEncrypted));

            }
        });
    }


    private void Handshake(){
        //listener thread
        // check if in client mode (sends syn and synack) - only listens for ack
        // if in server mode (sends ack) - only listens for syn and synack
        if (isInitiator.get()){ // "initiator" mode - send syn , wait for syn ack , send ack
            byte[] byteInitiatorPublicKey = securityLayer.createClientPublicKey().toByteArray();
             // send syn
            Thread sendSyn = sendFlag(byteInitiatorPublicKey,SYN);
            sendSyn.start();
            byte[] responderSynAck = null;
            while (responderSynAck == null){
                responderSynAck = ListenForEvent(SYNACK); // wait for an actual response from the client
            }
            System.out.println(responderSynAck);
            sendSyn.interrupt(); // kills sending process
            // now you have the other clients public key,
            securityLayer.createSharedSecret(new BigInteger(responderSynAck)); //
            //from here send ack to make syn ack stop
            byte[] ackPacket = {ACK};
            sendData(ackPacket); // send ack
            // set to listening mode , no more handshake
            listening.set(true);
            connected = true;
        }

        if (!isInitiator.get()){ //"responder mode" - listen for syn- return synack ,wait some seconds for ack
            // listen for syn
            byte[] initiatorSyn = null;
            while (initiatorSyn == null){
                initiatorSyn = ListenForEvent(SYN); //
            }
            securityLayer.createSharedSecret(new BigInteger(initiatorSyn));
            byte[] byteServerPublicKey = securityLayer.createClientPublicKey().toByteArray();
            Thread sendSynAck = sendFlag(byteServerPublicKey,SYNACK); // send syn ack with your data
            sendSynAck.start();
            // listen for ack for a couple seconds
            byte[] ackListen = null;
            long ackTimeout  = System.currentTimeMillis() + (signalRetryWaitMs*2);
            while (ackTimeout > System.currentTimeMillis()){
                ackListen = ListenForEvent(ACK);
            }

            if (ackListen != null ){
                System.out.println("ack never returned in amount of time , starting anyway. ");
            }

            sendSynAck.interrupt(); // stop sending synack
            ack.set(true);// set acknowledged to true
            listening.set(true);// start listening
            connected = true;
        }

    }



    private Thread sendFlag(byte[] bytePlainData, byte flag){ // sends data multiple times
        return new Thread(()->{
            ByteBuffer flagBuffer = ByteBuffer.allocate(1+bytePlainData.length).put(flag).put(bytePlainData); // add flag to data
            while (!Thread.currentThread().isInterrupted()){
                try {
                    sendData(flagBuffer.array()); // enclose flag and data with hmac
                    Thread.sleep(signalRetryWaitMs);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
    private byte[] processPacket(byte[] data,int dataLength){
        byte[] trimmedBuffer = Arrays.copyOf(data,dataLength);
        return securityLayer.authenticate(trimmedBuffer);
    }

    public void close(){
        connected = false;
        listening.set(false);
        ack.set(false);
        sendData(new byte[]{FIN});
        socket.close();
        System.out.println("Closed connection");
    }
    private byte[] ListenForEvent(byte flag){
        try {
            ByteBuffer incomingDataBuffer = ByteBuffer.allocate(1024);
            DatagramPacket dataPacket = new DatagramPacket(incomingDataBuffer.array(), incomingDataBuffer.capacity());
            socket.receive(dataPacket);
            byte[] processedPacket = processPacket(dataPacket.getData(), dataPacket.getLength());//package without the hmac
            if (processedPacket == null) {
                System.out.println("getting null bytes here ");
                return null;
            }
            if ((processedPacket[0] & FIN) == FIN){
                close();
                return null;
            }
            if ((processedPacket[0] & flag) == SYN){ // if getting a connect request
                initiatorAddress  = dataPacket.getSocketAddress();
            }
            if ((processedPacket[0] & flag) == flag ){ // first byte should always be the flag
                byte[] plainData = Arrays.copyOfRange(processedPacket,1,processedPacket.length); // the rest of the data in the incomming packet
                return plainData;
            }
            return null;
        }catch (Exception e ){
            System.out.println(e);
            return null;
        }
    }
}


// potential addition - adding text sending too
