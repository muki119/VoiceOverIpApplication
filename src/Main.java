import CMPC3M06.AudioPlayer;
import CMPC3M06.AudioRecorder;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;


public class Main {
    public static App AppInstance;
    public static void main(String[] args) throws LineUnavailableException {
        System.out.println("Hello world!");
        System.out.println(Arrays.toString(args));
        // check argument for a port , if so start app using port , otherwise use default.
        if (args.length > 0) {
            AppInstance = new App(Integer.parseInt(args[0]));
        }
        else {
            AppInstance = new App(0);
        }

        while (true) {
            Menu();
        }


    }

    public static void Menu() {
        System.out.println(
                "Welcome to VoIPCLi \n" +
                        "You're currently listening for current requests\n" +
                        "Options:\n" +
                        "1:Call\n" +
                        "2:quit"
        );
        System.out.print(">>");
        int choice = new Scanner(System.in).nextInt();
        switch (choice) {
            case 1:
                CallMenu();
            case 2:
                System.exit(0);
        }
    }

    public static void CallMenu(){
        System.out.print("Ip>>");
        String ip = new Scanner(System.in).next();
        System.out.print("Port>>");
        int port = new Scanner(System.in).nextInt();
        AppInstance.Call(ip,port);
    }

    public static void inCallMenu(){
        while (AppInstance.isListening()){ // while listening ,
            System.out.println("Muted:"+AppInstance.isMuted());
            System.out.println("Options:" +
                    "Mute:mute/unmute mic"+
                    "exit:quit call");

            String choice = new Scanner(System.in).next();
            switch (choice) {
                case "mute":
                    AppInstance.toggleMute();
                case "exit":
                    AppInstance.Shutdown();
            }
        }


    }




}
