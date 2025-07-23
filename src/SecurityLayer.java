import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Random;

public class SecurityLayer {
    //shared static secret key for authentication
    public static final String SHARED_SECRET = "SecureVoIPKey";

    private byte[] xorEncryptionKey; // the key used for xOrEncryption
    private final String hexPrime = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
            "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
            "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
            "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
            "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
            "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
            "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
            "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64" +
            "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
            "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B" +
            "F12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
            "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31" +
            "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF";

    private final BigInteger primeNumber = new BigInteger(hexPrime,16); // prime number
    private final short generator = 2; // primitive root -- magic number kinda bad
    private BigInteger clientPrivateKey; // your private key youre going to create your public key with
    private BigInteger clientPublicKey; // your public key aswell
    public BigInteger sharedSecretKey; // the final key that will be used for encryption
    private final String preSharedKey ="d36a6190d328e9d8d6960cd9fc377648282723d304444fa75f711a75aa169689";
    private final Mac Hmac ;


    SecurityLayer(){
        try {
            Hmac = Mac.getInstance("HmacSHA256");
            Hmac.init(new SecretKeySpec(preSharedKey.getBytes(), "HmacSHA256"));
            generateClientPrivateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] xorFunction(byte[] data){
        byte[] decryptedDataArray = new byte[data.length];

        int encryptionKeyLength = xorEncryptionKey.length;
        for (int x=0; x<data.length; x++){
            byte incomingData = data[x];
            byte key = xorEncryptionKey[x%encryptionKeyLength];
            byte decryptedData = (byte) (incomingData^key);
            decryptedDataArray[x] = decryptedData;
        }
        return decryptedDataArray;
    }
    public byte[] encrypt(byte[]decryptedData){
        return (xorFunction(decryptedData));
    }
    // the incoming data is an array containing the audio to be played.
    public byte[] decrypt(byte[]encryptedData){
        return (xorFunction(encryptedData));
    }

    public void generateClientPrivateKey(){
        SecureRandom rand = new SecureRandom(); //1
        this.clientPrivateKey = new BigInteger(256,rand); // generate a 256bit integer.
    }
    public BigInteger createClientPublicKey(){ // 2 Creates public key to be shared to other peer
        this.clientPublicKey = BigInteger.valueOf(generator).modPow(this.clientPrivateKey,primeNumber);
        return this.clientPublicKey;
    }
    public BigInteger getClientPublicKey(){
        return this.clientPublicKey;
    }
    public void createSharedSecret(BigInteger otherPublicKey){ // 3
        this.sharedSecretKey = otherPublicKey.modPow(this.clientPrivateKey,primeNumber);
        this.xorEncryptionKey = this.sharedSecretKey.toByteArray();
    }
    public boolean hasSharedSecretKey(){
        return this.sharedSecretKey != null;
    }
    private byte[] createHash(byte[] data){
        return Hmac.doFinal(data);
    }
    public byte[] authenticate(byte[] incommingPacket){
        if (incommingPacket.length < 32){
            return null;
        }
        byte[] packetHmac = new byte[32];
        byte[] packetMessage = new byte[(incommingPacket.length-32)];  // the message to be hashed to see if the
        ByteBuffer packetBuffer = ByteBuffer.wrap(incommingPacket);
        packetBuffer.get(packetHmac).get(packetMessage); // get packetHmac
        byte[] hashedMessage = createHash(packetMessage); // hash message to see if packet hmac is the same as the hashed message
        boolean isHashValid = Arrays.equals(hashedMessage, packetHmac);
        return isHashValid ? packetMessage : null;
    }

    public byte[] createAuthenticatedPacket(byte[] outgoingData){ // incpmming data has to be already encrypted if not in handshake
        byte[] hmacData = createHash(outgoingData);
        ByteBuffer outBuffer = ByteBuffer.allocate(hmacData.length+outgoingData.length);
        outBuffer.put(hmacData).put(outgoingData);
        return outBuffer.array();
    }

    ///
    
}

