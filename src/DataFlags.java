public enum DataFlags { // there should be no 0 value flag as all data will come in.
    SYN((byte) 1), // = 1
    SYNACK((byte) (1<<1)), // = 2
    ACK((byte) (1<<2)), // = 4
    FIN((byte) (1<<3)), // = 8
    DATA((byte) (1<<4)); // = 16

    final byte value;
    DataFlags(byte i) {
        this.value = i;
    }
}
