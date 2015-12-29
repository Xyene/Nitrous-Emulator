package nitrous;

public enum EmulateSpeed {
    QUARTER("0.25x", 1048576),
    HALF("0.5x", 2097152),
    SINGLE("1x", 4194304),
    DOUBLE("2x", 8388608),
    TRIPLE("3x", 12582912),
    QUADRUPLE("4x", 16777216),
    SEXTUPLE("6x", 25165824),
    OCTUPLE("8x", 33554432),
    DUODECUPLE("12x", 50331648),
    HEXADECUPLE("16x", 67108864);

    public final String name;
    public final int clockSpeed;

    EmulateSpeed(String name, int clockSpeed) {
        this.name = name;
        this.clockSpeed = clockSpeed;
    }
}
