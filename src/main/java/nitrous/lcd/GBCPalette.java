package nitrous.lcd;

public class GBCPalette implements IPalette
{
    final int[] colors;

    public GBCPalette(int[] colors)
    {
        this.colors = colors;
    }

    public int getColor(int number)
    {
        return colors[number];
    }
}
