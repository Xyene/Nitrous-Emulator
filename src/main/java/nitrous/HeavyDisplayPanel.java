package nitrous;

import nitrous.cpu.Emulator;

import java.awt.*;

public class HeavyDisplayPanel extends Panel
{
    private final Emulator core;
    public final int magnification;

    public HeavyDisplayPanel(Emulator core, int mag)
    {
        this.core = core;
        this.magnification = mag;
        setBackground(Color.BLACK);
        setMaximumSize(new Dimension(160 * mag, 144 * mag));
        setMinimumSize(new Dimension(160 * mag, 144 * mag));
        setSize(new Dimension(160 * mag, 144 * mag));
        setPreferredSize(new Dimension(160 * mag, 144 * mag));
        setIgnoreRepaint(true);
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        core.setDisplay(this);
    }

    @Override
    public void paint(Graphics g)
    {
        throw new RuntimeException();
    }
}
