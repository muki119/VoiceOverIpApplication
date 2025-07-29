import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class newConnection {
    // isInitiator
    private AtomicBoolean isInitiator = new AtomicBoolean(false); // will be false by default
    //socket
    private DatagramSocket socket = null; // will be created on init
    // isConnected
    private AtomicBoolean isConnected = new AtomicBoolean(false); // will be false by default

    private AtomicBoolean isClosed = new AtomicBoolean(false); // will be false by default -- used to check if the connection is closed
    // isListening
    private AtomicBoolean isListening  = new AtomicBoolean(false); // will be false by default // is listening for incomming connections 
    //-- should be set to true on init for responder mode and false on init for initiator mode since it will not listen for incomming connections but will initiate them
    // listener thread
    private Thread listenerThread = null; // will be created on init
    // modeThread
    private Thread modeThread = null; // will be created on init

    private InetSocketAddress peerAddress = null; // will be set on init -- contains the ip address and the port number

    private short socketTimeout = 1000; // 1 second timeout for socket operations

    private SecurityLayer securityLayer = new SecurityLayer(); // security layer for encryption and authentication

    

    // sockets will be bound in the constructors
    newConnection(int portToBindTo){ // responderMode
        // takes a port to bind to for listening purposes
        try {
            this.socket = new DatagramSocket(portToBindTo);
            this.socket.setReuseAddress(true); // allow address reuse
            this.isListening.set(true); // set listening to true
            // responderMode(); // start the responder mode
        } catch (Exception e) {
            System.out.println("(Responder Mode) Error creating newConnection: " + e.getMessage());
            e.printStackTrace();
            return; // no need to continue if we can't create the socket
        }
        // will begin listening on running
        // call responder mode
        modeThread = new Thread(this::responderMode,"ResponderModeThread");
        modeThread.setDaemon(true); // set as daemon thread to not block shutdown
        modeThread.start(); // start the responder mode thread
        System.out.println("Responder mode started on port " + portToBindTo);
    }
    newConnection(String peerIpAddress, int peerPort){ // initiatorMode
        try {
            this.peerAddress = new InetSocketAddress(peerIpAddress, peerPort);
            this.socket = new DatagramSocket(); // liste on any port
            this.isInitiator.set(true); // set initiator to true
            modeThread = new Thread(this::initiatorMode,"InitiatorModeThread");
            modeThread.setDaemon(true); // set as daemon thread to not block shutdown
            modeThread.start(); // start the initiator mode thread
        } catch (Exception e) {
            System.out.println("(Initiator Mode) Error creating newConnection: " + e.getMessage());
            return; // no need to continue if we can't create the socket
        }
        // will bind to any socket and will initiate the calls
        // will create the full address from params
        // modeThread = InitiatorMode();
    }

    private void responderMode(){ // will be ran on startup -- listens for incomming -- creates and destorys 1 thread (this is a thread itself) - will be saved to modeThread attribute
        while (!Thread.currentThread().isInterrupted()){
            if (!this.isListening.get()){
                System.out.println("Responder mode is not listening, exiting...");
                return; // exit if not listening
            }
            try {
                // will listen for syn -- blocking
                byte[] synData = null; // syn data should be the peers 
                while (synData == null) { // always check for interruption
                    try{
                        if (Thread.currentThread().isInterrupted()){
                            System.out.println("Responder mode interrupted at Syn, exiting...");
                            return; // exit if interrupted
                        }
                        synData = this.receiveData(DataFlags.SYN.value); // will block until data is received -- // will get the address of the sender 
                        if (synData != null) {
                            System.out.println("Received SYN, starting connection...");
                            // create peer dh key exchange
                            securityLayer.createSharedSecret(new BigInteger(synData));
                        }

                    }catch (SocketTimeoutException e){ // catch timeout exception
                        //System.out.println("Timeout while waiting for SYN, retrying...");
                        synData = null; // reset synData to null to retry
                        continue;
                    }
                }
                
                long fiveSecondTimeout = System.currentTimeMillis() + 5000; // 5 seconds timeout for receiving synack
                
                // will then send synack -- on another thread
                Thread synackThread = new Thread(() -> {
                    while(System.currentTimeMillis() < fiveSecondTimeout) { // have 5 senconds to send synack
                        try {
                            if (Thread.currentThread().isInterrupted()){
                                System.out.println("Send Synack thread interrupted, exiting...");
                                return; // exit if interrupted
                            }
                            // send synack
                            byte[] publicKey  = securityLayer.createClientPublicKey().toByteArray(); // create the public key to send
                            this.sendFlag(DataFlags.SYNACK.value, publicKey); // will send synack with no data
                        } catch (Exception e) {
                            System.out.println("Error while sending SYNACK: " + e.getMessage());
                        }
                    }
                },"SendSynackThread");
                try {
                    byte[] ackData = null; // will be used to store the ack data
                    while (System.currentTimeMillis() < fiveSecondTimeout) { // will attempt to listen for ack for 5 seconds
                        // start the synack thread
                        if (!synackThread.isAlive()) {
                            synackThread.start(); // start the synack thread
                        }
                        ackData = this.receiveData(DataFlags.ACK.value); // will block until data is received
                        if (ackData != null) {
                            System.out.println("Received ACK, connection established.");
                            synackThread.interrupt(); // interrupt the synack thread if ack is received
                            this.isConnected.set(true); // set connected to true
                            this.isListening.set(false); // set listening to false
                            return; // exit the loop if ack is received
                        }
                    }
                    if (ackData == null) { // if it comes out of the loop without receiving ack 
                        System.out.println("No ACK received, connection failed.");
                        synackThread.interrupt(); // interrupt the synack thread if no ack is received
                        this.isConnected.set(false); // set connected to false
                        this.isListening.set(false); // set listening to false
                        return; // exit the loop if no ack is received
                    }
                } catch (Exception e) {
                    System.out.println("Error while listening for ACK: " + e.getMessage());
                }finally {
                    if (synackThread.isAlive()) {
                        synackThread.interrupt(); // interrupt the synack thread if it is still alive
                    }
                }     
                    // will try listen for ack in the 
                // if no ack kill connection.and restart function (while not connected{})
            }catch (Exception e) {
                System.out.println("Error in responder mode: " + e.getMessage());
                return; // exit on error
            }
        }

    }

    private void initiatorMode(){ // creates and destroys 2 threads -- this is a thread itself - will be saved to modeThread attribute
        while (!Thread.currentThread().isInterrupted()){
            long tenSecondTimeout = System.currentTimeMillis() + 10000; // 10 seconds timeout for sending syn
            Thread sendSynThread  = new Thread(()->{
                while(System.currentTimeMillis() < tenSecondTimeout){
                    if (Thread.currentThread().isInterrupted()){
                        System.out.println("Send SYN thread interrupted, exiting...");
                        return; // exit if interrupted
                    }
                    try {
                        // try send syn with dh public key
                        byte[] publicKey = securityLayer.createClientPublicKey().toByteArray();
                        this.sendFlag(DataFlags.SYN.value, publicKey); // will send syn with dh public key

                    }catch (Exception e) {
                        System.out.println("Error while sending SYN: " + e.getMessage());
                    }
                }
            },"SendSynThread");

            byte[] synackData = null; // will be used to store the synack data
            while (System.currentTimeMillis() < tenSecondTimeout) { // will attempt to listen for synack for 10 seconds
                if (!sendSynThread.isAlive()) {
                    sendSynThread.start(); // start the send syn thread
                }
                if (Thread.currentThread().isInterrupted()){
                    System.out.println("Initiator mode interrupted at Synack listen, exiting...");
                    return; // exit if interrupted
                }
                try {
                    synackData = this.receiveData(DataFlags.SYNACK.value); // will block for 1 second until data is received
                    if (synackData != null) { 
                        System.out.println("Received SYNACK, sending ACK.");

                        securityLayer.createSharedSecret(new BigInteger(synackData)); // create shared secret with the peer public key

                        this.sendFlag(DataFlags.ACK.value, new byte[0]); // will send ack once with no data

                        this.isConnected.set(true); // set connected to true
                        this.isListening.set(false); // set listening to false
                        if(sendSynThread.isAlive()) {
                            sendSynThread.interrupt(); // interrupt the send syn thread if synack is received
                        }
                        return; // exit the loop if synack is received
                    }
                } catch(SocketTimeoutException e) {
                    System.out.println("Timeout while waiting for SYNACK, retrying...");
                    synackData = null; // reset synackData to null to retry
                    continue; // continue to retry
                } catch (Exception e) {
                    System.out.println("Error while receiving SYNACK: " + e.getMessage());
                }finally {
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("Initiator mode interrupted at finally block, exiting...");
                        return; // exit if interrupted
                    }
                }


            }
            if(synackData == null) { // if it comes out of the loop without receiving synack
                System.out.println("No SYNACK received, connection failed.");
                if(sendSynThread.isAlive()) {
                    sendSynThread.interrupt(); // interrupt the send syn thread if no synack is received
                }
                this.isConnected.set(false); // set connected to false
                this.isListening.set(false); // set listening to false
                return; // exit the loop if no synack is received
            }
        }
        // will send sync every 500ms for 10 seconds -- this will be in another controlled thread
        // while sending , it will listen for a synack  -- this will be blocking
        // the sending thread will be killed
        // will then send ack every 500ms for around 5 seconds
        // if it dosent work - end function
    }

    public boolean isConnected() {
        return this.isConnected.get(); // will return true if connected
    }

    public void listen(Consumer<byte[]> callback){ // -- creates thread - will be saved to listenerThread attribute
        this.listenerThread = new Thread(()->{
            while(!Thread.currentThread().isInterrupted()){
                try {
                    if (Thread.currentThread().isInterrupted()){
                        System.out.println("Listener thread interrupted, exiting...");
                        return; // exit if interrupted
                    }
                    if (!this.isConnected.get()){
                        continue;
                    }
                    byte[] data = this.receiveEncrypted(); // will block until data is received
                    if (data != null) {
                        callback.accept(data); // call the callback with the received data
                    }
                } catch (Exception e) {
                    System.out.println("Error in listener thread: " + e.getMessage());
                }
            }
        },"Listener Thread");

        try {
            this.listenerThread.setDaemon(true); // set as daemon thread to not block shutdown
            this.listenerThread.start(); // start the listener thread
        } catch (Exception e) {
            System.out.println("Error while starting listener thread: " + e.getMessage());
        } 
        // will run on a thread that will check for its interruption
        // while not interrupted
        // will check if connection is turned on
        // thread created must be in class attribute to be interruptable from the close function
        // will only route DATA flags
    }

    // [hmac|flag|[encrypted data]]

    private void sendFlag(byte flag , byte[] data){
        if (flag == 0){
            System.out.println("Flag cannot be 0, please use a valid flag.");
            return; // return if flag is 0
        }
        if (peerAddress == null){
            System.out.println("Peer address is not set, cannot send data.");
            return; // return if peer address is not set
        }
        try{
            ByteBuffer preAuthenticatedData = ByteBuffer.allocate(1 + data.length);// will send a flag with any additional data
            preAuthenticatedData.put(flag);        // this function only adds the flag on
            preAuthenticatedData.put(data);
            byte[] authenticatedData = securityLayer.createAuthenticatedPacket(preAuthenticatedData.array());      // will call another function to add hmac hash on total package.
            DatagramPacket packet = new DatagramPacket(authenticatedData, authenticatedData.length, peerAddress);
            this.socket.send(packet);         
        } catch (IOException e) {
            System.out.println("Error while sending data: " + e.getMessage());
        }
        // for encrypted data - data should be encrypted before passed to this function
    }

    public void sendAudio(byte[] audioBlock){
        try {
            this.sendEncrypted(audioBlock);
        } catch (Exception e) {
            System.out.println("(sendAudio)Error while sending audio: " + e.getMessage());
        }
    } 

    private void sendEncrypted(byte[] plaintextData){
        try{
            if (plaintextData == null || plaintextData.length == 0) {
                System.out.println("sendEncrypted: No data to send, skipping send.");
                return; // return if no data to send
            }
            byte[] encryptedData = securityLayer.encrypt(plaintextData); // will encrypt data with security layer encryption function.
            sendFlag(DataFlags.DATA.value, encryptedData); // pass to sendFlag function with only DATA flag  
        }catch (Exception e) {
            System.out.println("Error while sending encrypted data: " + e.getMessage());
        }
    }

    private byte[] authenticatePacket(byte[] data,int dataLength){
        try {
            byte[] trimmedBuffer = Arrays.copyOf(data,dataLength);
            return securityLayer.authenticate(trimmedBuffer);
        } catch (Exception e) {
            System.out.println("Error while authenticating packet: " + e.getMessage());
            return null; // return null if authentication fails
        }
    }
    // function will listen to data for a specific flag for 5 seconds  -- i blocking for 5 seconds
    private byte[] receiveData(byte flag) throws SocketTimeoutException{ // -- blocking -- will throw exception if no socket or not listening -- will throw timout exception if no data received in 5 seconds
        if (flag == 0){
            System.out.println("Flag cannot be 0, please use a valid flag.");
            return null; // return null if flag is 0
        }
        try {
            ByteBuffer incomingDataBuffer = ByteBuffer.allocate(1024);
            DatagramPacket dataPacket = new DatagramPacket(incomingDataBuffer.array(), incomingDataBuffer.capacity());
            this.socket.setSoTimeout(socketTimeout); // set the socket timeout to 5 seconds
            this.socket.receive(dataPacket);
            byte[] authenticatedData = authenticatePacket(dataPacket.getData(), dataPacket.getLength());
            if (authenticatedData == null) {
                System.out.println("Authentication failed for received data: " + Arrays.toString(dataPacket.getData()));
                return null; // return null if authentication fails
            }

            if (((authenticatedData[0] & DataFlags.SYN.value) == DataFlags.SYN.value) && !isConnected.get()) { // if you receive a SYN flag and not connected -- incomming connection
                System.out.println("Received SYN flag, but not connected , initiating connection...");
                peerAddress = new InetSocketAddress(dataPacket.getAddress(), dataPacket.getPort());
            } 
            // check for fin -- close connection

            if ((authenticatedData[0] & flag) == flag) { // check if the first byte of the authenticated data matches the flag
                byte[] data = Arrays.copyOfRange(authenticatedData, 1, authenticatedData.length); // strip the flag
                return data; // return the data
            }
            else {
                System.out.println("Received data with incorrect flag: " + Arrays.toString(authenticatedData));
                return null; // return null if the flag does not match
            }
        }catch (SocketTimeoutException e) {
            //System.out.println("Timeout while waiting for data with flag " + flag);
            throw e;
        }catch (SocketException e ){
            System.out.println("Socket error while receiving data: " + e.getMessage());
            return null; // return null if socket error
        }catch (IOException e) {
            System.out.println("IO error while receiving data: " + e.getMessage());
            return null; // return null if IO error
        }catch (Exception e) {
            System.out.println("Error while receiving data: " + e.getMessage());
            return null; // return null for any other error
        }
        // will check mode of connection 
        // if starting handshake and syn - the ip and port will be set to the sender of the syn
        // will listen for flag
        // once it gets the flag ,authenticate and strip of hmac bits and also flag
        // will always have a 5 second timeout
    }

    private byte[] receiveEncrypted(){
        try {
            byte[] encryptedData = receiveData(DataFlags.DATA.value);
            return securityLayer.decrypt(encryptedData); // will decrypt the data using the security layer
        } catch (SocketTimeoutException e) {
            System.out.println("receiveEncrypted: Timeout while waiting for data.");
        } catch (Exception e) {
            System.out.println("receiveEncrypted: Error while receiving data.");
            e.printStackTrace();
        } // will call receiveData with DATA flag
        // will call recieveData() for DATA flag
        // will decrypt data and return
        return null; // return null if no data received or error occurred
    }

    public void close(){
        try{
            if (this.listenerThread != null && this.listenerThread.isAlive()) {
                this.listenerThread.interrupt(); // interrupt the listener thread if it is alive
                listenerThread.join(2000);
            }
            if (this.modeThread != null && this.modeThread.isAlive()) {
                this.modeThread.interrupt(); // interrupt the mode thread if it is alive
                modeThread.join(2000);
            }
            this.socket.close(); // close the socket
            this.isConnected.set(false); // set connected to false
            this.isListening.set(false); // set listening to false
            System.out.println("Connection closed.");
        } catch (InterruptedException e) {
                System.out.println("Error while closing threads: " + e.getMessage());
        }
        // closes listening thread
        // closes socket
    }


}