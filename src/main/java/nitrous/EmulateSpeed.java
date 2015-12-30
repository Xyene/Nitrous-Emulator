package nitrous;

/**
 * Definitions for supported emulation speeds.
 * <p>
 * All speeds are based off of a 4.194304MHz clock.
 */
public enum EmulateSpeed
{
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

    /**
     * The name of the speed as show in the GUI.
     */
    public final String name;

    /**
     * The clock speed.
     */
    public final int clockSpeed;

    /**
     * Creates a new EmulateSpeed.
     *
     * @param name       The identifier to show to the user in the GUI.
     * @param clockSpeed The clock speed.
     */
    EmulateSpeed(String name, int clockSpeed)
    {
        this.name = name;
        this.clockSpeed = clockSpeed;
    }
}
