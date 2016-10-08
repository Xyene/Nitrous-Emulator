package nitrous.lcd;

/**
 * Represents a Gameboy Color
 *
 * @author Tudor
 */
public class GBCPalette implements IPalette
{
    /**
     * An array of 4 RGBA palette entries.
     * Package-local for easier access from LCD.
     */
    final int[] colors;

    /**
     * Constructs a new GBCPalette.
     *
     * @param colors The palette, in RGBA format.
     */
    public GBCPalette(int[] colors)
    {
        if (colors.length != 4)
            throw new IllegalArgumentException("colors must be of length 4");
        this.colors = colors;
    }

    /**
     * {@inheritDoc}
     */
    public int getColor(int number)
    {
        return colors[number];
    }
}
