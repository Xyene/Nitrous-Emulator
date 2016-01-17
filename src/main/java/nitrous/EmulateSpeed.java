package nitrous;

import nitrous.cpu.Emulator;

import static nitrous.cpu.Emulator.BASE_CLOCK_SPEED;

/**
 * Definitions for supported emulation speeds.
 * <p/>
 * All speeds are based off of a 4.194304MHz clock.
 */
public enum EmulateSpeed {
    QUARTER("0.25x", BASE_CLOCK_SPEED / 4),
    HALF("0.5x", BASE_CLOCK_SPEED / 2),
    SINGLE("1x", BASE_CLOCK_SPEED),
    DOUBLE("2x", BASE_CLOCK_SPEED * 2),
    TRIPLE("3x", BASE_CLOCK_SPEED * 3),
    QUADRUPLE("4x", BASE_CLOCK_SPEED * 4),
    SEXTUPLE("6x", BASE_CLOCK_SPEED * 6),
    OCTUPLE("8x", BASE_CLOCK_SPEED * 8),
    DUODECUPLE("12x", BASE_CLOCK_SPEED * 12),
    HEXADECUPLE("16x", BASE_CLOCK_SPEED * 16);

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
    EmulateSpeed(String name, int clockSpeed) {
        this.name = name;
        this.clockSpeed = clockSpeed;
    }
}
