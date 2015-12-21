package nitrous.lcd;

/**
 * Definitions for screen interpolation functions.
 * <p>
 * A Gameboy screen is 160x144 pixels in size, but these pixels are significantly larger than modern screen pixels.
 * As such, a 160x144 game is unplayable due to its small size - the sensible thing to do is scale it upwards (by a
 * factor of 2x, for example). When doing this, we can the possibility of interpolating the stretch with various functions
 * to improve the graphics quality.
 */
public enum Interpolator
{
    /**
     * No interpolation; blocky pixels.
     */
    NEAREST("None"),

    /**
     * Bilinear interpolation; "blurs" a bit.
     */
    BILINEAR("Bilinear"),

    /**
     * Similar effect to BILINEAR, but fetches more samples per pixel (looks better, but is slower).
     */
    BICUBIC("Bicubic");

    /**
     * Name of the interpolation function as displayed in the GUI.
     */
    public final String name;

    /**
     * Creates an Interpolator.
     *
     * @param name The name of the Interpolator as shown to the user in the GUI.
     */
    Interpolator(String name)
    {
        this.name = name;
    }
}
