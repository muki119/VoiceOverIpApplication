# Voip App

This Branch contains a redesign of my VoIP app for my Networking coursework assignment.

Although the original coursework functioned as intended, the implementation left much to be desired.

This refactoring aims to improve the implementation, focusing primarily on the handshake logic, concurrency control, maintainability, and readability of the codebase, with a specific focus on the connection class.

## Changes Made

- Developed an App class to control connections and encapsulate the connection, audio capture, and playback.
  
- Handshake logic is split into two sections:
  - Receiver:
    - This section will listen for SYN, send SYN-ACK, and wait for ACK before transmitting.

  - Initiator:
    - Will initiate a connection attempt by sending SYN and waiting for SYN-ACK, then finally sending an ACK to establish the full connection.

- More Efficient flag encoding
  - For the handshake process, flags are no longer multi-byte representations of strings .They have now been replaced with single-byte flags which are compared using bitwise AND operations. The use of bitwise allows the use of multi-flag bytes, providing memory efficiency as a result.

- Non-blocking mode execution.
  - Both initiator and responder modes utilise threads to ensure they do not block the main thread. 

- Mode Initiation through constructors:

  - `newConnection(portToBindTo)` Starts in responder mode.

  - `newConnection(peerIpAddress,peerPort)` Starts in initiator mode.

- Thread-safe state management.
  - All control variables utilise their atomic variants for thread safety and consistent values.

- Time-controlled socket operations.
  - All socket receive operations have a timeout to not block indefinitely.

- Graceful shutdown.
  - Implemented a close function to close a connection safely.

## Future Improvements

- Reconnection logic.  
- Receiver-based packet loss compensation methods.
- Connection timeout logic to prevent indefinite waiting from a lost connection.


## Installation

### To clone the repository into a directory.
```bash
git clone --branch VoipImplementation2 https://github.com/muki119/VoiceOverIpApplication.git
```


### Compile the program. 
```bash 
cd VoiceOverIpApplication 
javac -cp ./src/AudioLib.jar ./src/*.java -d ./out 
```

### Run the program. 
By default, the program will run on port 2556.
However, you do have the option to select a port to listen to.

```bash 
cd VoiceOverIpApplication 
java -cp out:./src/AudioLib.jar Main [Port]
```

### Calling a peer. 
**When the program starts, the application will begin listening for incoming connections on the specified or default port** .
```text
Welcome to VoIPCLi 
You're currently listening for requests on 
2556
Options:
1:Call
2:quit
>> [Input] 
```

You will be allowed to initiate a connection and call or quit the application.

If Call is selected, you will be prompted to input the IP and port of the peer you would like to connect to.

```
Welcome to VoIPCLi 
You're currently listening for requests on 
2556
Options:
1:Call
2:quit
>>1
Ip>>localhost
localhost
Port>>2000
```