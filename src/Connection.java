//import java.io.IOException;
//import java.math.BigInteger;
//import java.net.*;
//import java.nio.ByteBuffer;
//import java.util.Arrays;
//import java.util.function.Consumer;
//
//public class Connection {
//    private DatagramSocket socket;
//    private final InetAddress ip;
//    private final int port; // the port to send to
//    private int portToBind; // the port to listen to
//    private boolean listening = false;
//    private boolean acknowledged = false;
//    private final SecurityLayer securityLayer = new SecurityLayer();
//
//    public Connection(String ip, int port) {
//        try{
//            this.ip = InetAddress.getByName(ip);
//            this.port = port;
//            this.portToBind = port;
//            this.socket = new DatagramSocket();
//            System.out.println("Sending to " + this.ip+" On port "+this.port);
//
//        }catch (UnknownHostException e){
//            System.out.println("Unknown Host \n Input a valid IP address");
//            throw new RuntimeException(e);
//        }catch (SocketException e){
//            System.out.println("Socket Error");
//            throw new RuntimeException(e);
//        }
//    }
//    public Connection(String ip, int port, int portToBind) {
//        this(ip, port);
//        this.portToBind = portToBind;
//    }
//    public boolean isListening(){
//        return listening;
//    }
//
//    public void sendEncrypted(byte[] data){
//        if(!securityLayer.hasSharedSecretKey()){
//            throw new RuntimeException("Connection Not Established");
//        }
//        byte[] encryptedData = securityLayer.encrypt(data);
//        this.sendData(encryptedData);
//    }
//
//    public void sendData(byte[] data){
//        // go through processes to send - full layers
//        try{
//            byte[] authenticatedPacket = securityLayer.createAuthenticatedPacket(data);
//            DatagramPacket dataPacket = new DatagramPacket(authenticatedPacket, authenticatedPacket.length, this.ip, this.port);
//            socket.send(dataPacket);
//        }catch ( IOException e){
//            System.out.println("Error sending data");
//            e.printStackTrace();
//        }
//    }
//    public Connection listen(Consumer<byte[]> callback){ // for listening to incoming audio
//        try{
//            this.socket = new DatagramSocket(this.portToBind);
//            Thread socketListenerThread = new Thread(()->{
//                System.out.println("Listening on port "+this.portToBind);
//                while (this.listening){
//                    try{
//                        byte[] buffer = new byte[1024];
//                        DatagramPacket incomingPacket = new DatagramPacket(buffer, buffer.length);
//                        socket.receive(incomingPacket);
//
//                        byte[] trimmedData = Arrays.copyOfRange(incomingPacket.getData(), 0, incomingPacket.getLength());
//                        byte[] authenticatedData = securityLayer.authenticate(trimmedData);
//
//                        if (authenticatedData == null){
//                            System.out.println("Invalid data received");
//                            continue;
//                        }
//                        byte[] plainTextData = securityLayer.decrypt(authenticatedData);// put through security layer
//                        callback.accept(plainTextData); // pass to callback
//
//                    }catch (IOException e){
//                        System.out.println("Error receiving data");
//                        e.printStackTrace();
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    }
//                }
//
//            });
//            handshake();
//            if (this.securityLayer.hasSharedSecretKey()){ // if security Layer can perform encryption/ decryption then allow incoming audio traffic
//                this.listening = true;
//                socketListenerThread.start();
//            }
//            return this;
//        }catch (SocketException e){
//            System.out.println("Socket Error");
//            throw new RuntimeException(e);
//        }
//
//    }
//
//
//    private String extractEvent(final String Event,final byte[] data ){
//        byte[] eventBytes = Arrays.copyOfRange(data,0,Event.length());
//        return new String(eventBytes);
//    }
//    private void handshake(){
//        new Thread(()->{
//            byte[] clientPublicKey = securityLayer.createClientPublicKey().toByteArray();
//            byte[] eventBuffer = "HELLO".getBytes();
//            ByteBuffer helloBuffer = ByteBuffer.allocate(clientPublicKey.length+eventBuffer.length).put(eventBuffer).put(clientPublicKey);
//            while(!this.acknowledged){
//                sendData(helloBuffer.array());
//                try {
//                    Thread.sleep(200);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }).start();
//        Thread handshakeThread  = new Thread(()->{
//            String ACK = "ACK",HELLO="HELLO";
//            while(!this.acknowledged){
//                try {
//                    byte[] incomingDataBuffer = new byte[1024];
//                    DatagramPacket dataPacket = new DatagramPacket(incomingDataBuffer, incomingDataBuffer.length);
//                    socket.receive(dataPacket);
//                    byte[] processedPacket = processPacket(dataPacket.getData(), dataPacket.getLength());//package without the hmac
//                    if (processedPacket == null) {
//                        System.out.println("not auth");
//                        return;
//                    }
//
//                    if(extractEvent(HELLO, processedPacket).equals(HELLO)){
//                        byte[] incomingData = Arrays.copyOfRange(processedPacket, HELLO.length(), processedPacket.length);
//                        byte[] publicKeyBytes = this.securityLayer.createClientPublicKey().toByteArray();
//                        this.securityLayer.createSharedSecret(new BigInteger(incomingData));
//                        ByteBuffer acknowledgementBuffer = ByteBuffer.allocate(ACK.getBytes().length + publicKeyBytes.length);
//                        acknowledgementBuffer.put(ACK.getBytes());
//                        acknowledgementBuffer.put(publicKeyBytes);
//                        sendData(acknowledgementBuffer.array());
//                        this.acknowledged = true;
//                    } else if (extractEvent(ACK, processedPacket).equals(ACK)) {
//                        byte[] incomingData = Arrays.copyOfRange(processedPacket,ACK.length(), processedPacket.length);
//                        this.securityLayer.createSharedSecret(new BigInteger(incomingData));
//                        this.acknowledged = true;
//                    }
//                }catch (Exception e){
//                    e.printStackTrace();
//                }
//            }
//        });
//
//        try {
//            handshakeThread.start();
//            handshakeThread.join();
//            System.out.println("Done handshake");
//        }catch (InterruptedException e){
//            System.out.println("Error waiting for handshake thread");
//            e.printStackTrace();
//        }
//
//
//
//
//
//
//    }
//}
