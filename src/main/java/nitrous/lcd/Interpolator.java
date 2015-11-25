package nitrous.lcd;

public enum Interpolator
{
    NEAREST("Nearest"),
    BILINEAR("Bilinear"),
    BICUBIC("Bicubic");

    public final String name;

    Interpolator(String name)
    {
        this.name = name;
    }
}
