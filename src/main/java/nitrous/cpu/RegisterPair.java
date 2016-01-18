package nitrous.cpu;

/**
 * Defines a register pair; that is, a 16-bit value made up of two 8-bit values.
 */
public enum RegisterPair
{
    BC, DE, HL, SP;

    /**
     * CPU id -> RegisterPair conversion.
     */
    public static final RegisterPair[] byValue = {BC, DE, HL, SP};
}
