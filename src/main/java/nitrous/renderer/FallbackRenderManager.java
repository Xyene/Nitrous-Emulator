package nitrous.renderer;

import nitrous.lcd.LCD;

import javax.swing.*;
import java.awt.*;
import java.awt.peer.ComponentPeer;

/**
 * This render manager uses whatever sketchy unknown renderer used by Java.
 */
public class FallbackRenderManager implements IRenderManager
{
    /**
     * Field to store the peer.
     */
    private final ComponentPeer peer;

    /**
     * Constructs a fallback renderer for the {@link java.awt.Panel} whose peer is given.
     *
     * @param peer the peer of the {@link java.awt.Panel} for which the renderer is constructed.
     */
    public FallbackRenderManager(ComponentPeer peer)
    {
        this.peer = peer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graphics2D getGraphics()
    {
        // Return the default peer.
        Toolkit.getDefaultToolkit().sync();
        return (Graphics2D) peer.getGraphics();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update()
    {
        // There is nothing needed to do when switching.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JRadioButtonMenuItem getRadioMenuItem(LCD lcd)
    {
        return new JRadioButtonMenuItem("???")
        {
            {
                // Switch to this renderer on click of its menu item.
                addActionListener((x) ->
                {
                    System.err.println("Switched to fallback renderer.");
                    lcd.currentRenderer = FallbackRenderManager.this;
                    lcd.currentRenderer.update();
                });
            }
        };
    }
}
