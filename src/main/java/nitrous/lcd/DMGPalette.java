package nitrous.lcd;

import static nitrous.cpu.R.*;

/**
 * Represents a Gameboy color palette. This is a shim for a GBCPalette-like interface.
 *
 * @author Tudor
 */
public class DMGPalette implements IPalette
{
    /**
     * Parent LCD object.
     */
    private final LCD lcd;

    /**
     * Which register this palette is read from.
     */
    protected final int register;

    /**
     * Palette colors in RGBA format - generally static, but may be read from PaletteColors.
     */
    protected final int[] colors;

    /**
     * Constructs a new DMGPalette.
     *
     * @param lcd      The parent LCD.
     * @param colors   A 4-element array containing RGBA entries for the palette.
     * @param register Which register to read from (one of R.R_BGP, R.R_OBP0, or R.R_OBP1).
     */
    public DMGPalette(LCD lcd, int[] colors, int register)
    {
        if (register != R_BGP && register != R_OBP0 && register != R_OBP1)
            throw new IllegalArgumentException("register must be one of R.R_BGP, R.R_OBP0, or R.R_OBP1");
        if (colors.length != 4)
            throw new IllegalArgumentException("colors must be of length 4");
        if (lcd == null)
            throw new IllegalArgumentException("lcd must not be null");

        this.lcd = lcd;
        this.register = register;
        this.colors = colors;
    }//end DMGPalette(LCD, colors, register)

    /**
     * {@inheritDoc}
     */
    public int getColor(int number)
    {
        // a palette color is defined in the byte form 33221100: to get any color n, we shift right by 2n and mask with 0x3
        return colors[(lcd.core.mmu.registers[register] >> (number * 2)) & 0x3];
    }//end getColor
}//end class DMGPalette
