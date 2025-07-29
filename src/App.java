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
    private AudioRecorder audioRecorder = null;
    private AudioPlayer audioPlayer = null;

    private Thread audioRecorderThread = null; // thread for the recording loop
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
            if (audioRecorderThread != null){
                audioRecorderThread.interrupt();
                try {
                    audioRecorderThread.join(500); // Wait up to 500 millisecond
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            appConnection.close();
        }
        closeAudio();

    }

    private void closeAudio(){
        if (audioPlayer != null){
            audioPlayer.close();
        }
        if (audioRecorder != null){
            audioRecorder.close();
        }
        audioPlayer = null;
        audioRecorder = null;
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
        closeAudio();
         // start a new call as an initiator
        if (audioRecorderThread != null && audioRecorderThread.isAlive()) {
            audioRecorderThread.interrupt();
            try {
                audioRecorderThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        
        if (this.appConnection != null) {
            System.out.println("Closing existing connection before starting a new one.");
            appConnection.close();
        }
        appConnection = null;
        appConnection = new newConnection(ip, port); // try to connect to the peer
        this.ip = ip;
        this.port = port;
        run();
        // save ip and port for reconnect purposes
        // call run function

    }

    public void run(){
        try{
            audioRecorder = new AudioRecorder();
            audioPlayer = new AudioPlayer();
            // Capture connection reference to prevent race conditions
            final newConnection currentConnection = appConnection;
            currentConnection.listen((data)->{
                try{
                    audioPlayer.playBlock(data);
                } catch (IOException e) {
                    System.out.println("Audio playback error: " + e);
                }
            });

            audioRecorderThread = new Thread(()-> {
                try {
                    while (currentConnection.isConnected()) {
                        if (Thread.currentThread().isInterrupted()) {
                            System.out.println("Audio recorder thread interrupted, stopping.");
                            break; // Exit the loop if the thread is interrupted
                        }
                        if (!muted.get()) { // if not muted
                            byte[] audioData = audioRecorder.getBlock();
                            System.out.println("sending audio");
                            if (audioData != null) {
                                currentConnection.sendAudio(audioData); // send the recorded audio data
                            }
                        } else {
                            Thread.sleep(100); // sleep for a short time to avoid busy waiting
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Audio recorder thread error: " + e);
                } finally {
                    System.out.println("Audio recorder thread stopped");
                }
            }, "AudioRecorderThread");

            audioRecorderThread.start();

        } catch (Exception e) {
            System.out.println("Error in run(): " + e);
            e.printStackTrace();
        }
    }

    public void toggleMute(){
        muted.set(!muted.get());
    }
    public boolean isMuted(){
        return muted.get();
    }
    public boolean isConnected(){
        if (appConnection != null){
            return appConnection.isConnected();
        }
        return false;
    }




}
