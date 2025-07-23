import CMPC3M06.AudioPlayer;
import CMPC3M06.AudioRecorder;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class App {
    private newConnection appConnection = null;
    public AtomicBoolean muted  = new AtomicBoolean(false);
    private String ip; // for reconnect purposes
    private int port; // for reconnect purposes
    private int portToBind = 0;
    private int defaultPort = 2556;
    // have a graceful shutdown

    App(int portToBindTo) {
        Runtime.getRuntime().addShutdownHook(new Thread(this::Shutdown));
        // by default start a listener on
        this.portToBind = portToBindTo;
        this.appConnection = new newConnection(getPortToBind()); //
        run();
        // call run func  - run func should start the listening and messaging functions
    }

    public void Shutdown(){
        System.out.println("App is shutting down");
        if (appConnection != null){
            appConnection.close();
        }
    }

    public int getPortToBind (){
        if (portToBind == 0){
            return defaultPort;
        }
        return portToBind;
    }

    public void Call(String ip , int port){
        // make a new connection as initiator
        if(portToBind == 0){ // if the user never specified a port to bind
            portToBind = defaultPort; // bind to 2556 (default port)
        }
         // start a new call as an initiator
        appConnection = new newConnection(ip, port, getPortToBind());
        this.ip = ip;
        this.port = port;
        run();
        // save ip and port for reconnect purposes
        // call run function

    }

    public void run(){
        try{
            AudioRecorder audioRecorder = new AudioRecorder();
            AudioPlayer audioPlayer = new AudioPlayer();
            appConnection.listen((data)->{
                try{
                    audioPlayer.playBlock(data);
                } catch (IOException e) {
                    System.out.println(e);
                }
            });
            new Thread(()->{
                while(appConnection.isListening() && !muted.get()){
                    //record audio block and send it on sendEncrypted(bytes)
                    //String text = new Scanner(System.in).nextLine()+'\n';
                    try {
                        appConnection.sendEncrypted(audioRecorder.getBlock()); // main thread blocking
                    } catch (IOException e) {
                        System.out.println(e);
                    }
    //            System.out.println(Arrays.toString(bs));
    //            System.out.println(Arrays.toString(text.getBytes()));
    //            System.out.println(Arrays.toString(testConnection.testDecrypt(bs)));
                }
            });


        }catch (Exception e ){
            System.out.println(e);
        }

    }

    public void toggleMute(){
        muted.set(!muted.get());
    }
    public boolean isMuted(){
        return muted.get();
    }
    public boolean isListening(){
        if (appConnection != null){
            return appConnection.isListening();
        }
        return false;
    }

    public void newConnection(){
        if (appConnection != null){
            appConnection.close();
        }
        appConnection = new newConnection(getPortToBind());
    }



}
