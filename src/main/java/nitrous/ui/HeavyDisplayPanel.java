package nitrous.ui;

import nitrous.cpu.Emulator;
import nitrous.cpu.R;

import java.awt.*;

/**
 * Heavyweight panel to display a game's LCD on.
 *
 * @author Tudor
 */
public class HeavyDisplayPanel extends Panel
{
    /**
     * The Emulator bound to this display.
     */
    private final Emulator core;

    /**
     * The magnification level of this display.
     */
    public final int magnification;

    /**
     * Creates a new HeavyDisplayPanel.
     *
     * @param core The Emulator to render.
     * @param mag  The magnification level to use.
     */
    public HeavyDisplayPanel(Emulator core, int mag)
    {
        this.core = core;
        this.magnification = mag;

        // Other parts of the code draw black for disabled areas
        setBackground(Color.BLACK);

        // Strict sizing based off magnification
        Dimension sz = new Dimension(R.W * mag, R.H * mag);
        setMaximumSize(sz);
        setMinimumSize(sz);
        setSize(sz);
        setPreferredSize(sz);

        // Painting is done by an IRenderManager directly on the component peer
        setIgnoreRepaint(true);
    }//end HeavyDisplayPanel(core, mag)

    /**
     * {@inheritDoc}
     */
    @Override
    public void addNotify()
    {
        super.addNotify();

        // Notify the Emulator to paint on this surface
        core.setDisplay(this);
    }//end addNotify

    /**
     * Disables painting of this component by throwing a RuntimeException: use an IRenderManager to draw instead.
     *
     * @param g An ignored graphics object.
     */
    @Override
    public void paint(Graphics g)
    {
        throw new RuntimeException();
    }//end paint
}
