import javax.sound.sampled.LineUnavailableException;
import java.util.Arrays;
import java.util.Scanner;


public class Main {
    public static App AppInstance;
    private static Scanner scanner = new Scanner(System.in);
    public static void main(String[] args) throws LineUnavailableException {
        System.out.println("Hello world!");
        System.out.println(Arrays.toString(args));
        // check argument for a port , if so start app using port , otherwise use default.
        if (args.length > 0) {
            // app instances run on init so handshake will block without threading
            AppInstance = new App(Integer.parseInt(args[0]));
        }
        else {
            AppInstance = new App(0);
        }
            Menu();


    }

    public static void Menu() {
        System.out.println(
                "Welcome to VoIPCLi \n" +
                        "You're currently listening for requests on \n" +AppInstance.getPortToBind()+"\n"+
                        "Options:\n" +
                        "1:Call\n" +
                        "2:quit"
        );
        System.out.print(">>");
        int choice =scanner.nextInt();
        switch (choice) {
            case 1:
                CallMenu();
                break;
            case 2:
                System.exit(0);
                break;
            default:
                System.out.println("Invalid choice");
                Menu();
        }
    }

    public static void CallMenu(){
        System.out.print("Ip>>");
        String ip = scanner.next();
        System.out.println(ip);
        System.out.print("Port>>");
        int port = scanner.nextInt();
        System.out.println(port);
        AppInstance.Call(ip,port);
        inCallMenu();
    }

    public static void inCallMenu(){
        while (AppInstance.isConnected()){ // while listening ,
            System.out.println("Muted:"+AppInstance.isMuted());
            System.out.println("Options:" +
                    "Mute:mute/unmute mic"+
                    "exit:quit call");

            String choice = scanner.next();
            switch (choice) {
                case "mute":
                    AppInstance.toggleMute();
                    break;
                case "exit":
                    AppInstance.Shutdown();
                    Menu();
                    return;
                default:
                    System.out.println("Invalid option");
                    break;
            }

        }
        Menu();


    }




}
