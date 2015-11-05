package nitrous.lcd;

public class DMGPalette implements Palette
{
    private LCD lcd;
    protected final int register;
    protected final int[] colors;

    public DMGPalette(LCD lcd, int[] colors, int register)
    {
        this.lcd = lcd;
        this.register = register;
        this.colors = colors;
    }

    public int getColor(int number)
    {
        return colors[(lcd.core.mmu.registers[register] >> (number * 2)) & 0x3];
    }
}
