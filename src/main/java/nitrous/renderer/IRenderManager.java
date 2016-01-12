package nitrous.renderer;

import nitrous.lcd.LCD;

import javax.swing.*;
import java.awt.*;


/**
 * The interface for a render.
 *
 * A renderer is responsible for producing a {@link java.awt.Graphics2D}
 * to draw on and switching the {@link java.awt.Panel} to render with it.
 */
public interface IRenderManager
{
    /**
     * The {@link java.lang.Class} objects for each available renderer.
     */
    Class<? extends IRenderManager>[] RENDERERS = new Class[]{
            GDIRenderManager.class, // GDI is fastest for drawing just one image
            D3DRenderManager.class, // Followed by D3D (on most cards)
            WGLRenderManager.class, // Some cards may do GL better than D3D, but this is unlikely
            XRenderManager.class, // Linux!
    };

    /**
     * Called to produce a {@link java.awt.Graphics2D} for rendering.
     *
     * @return a {@link java.awt.Graphics2D} to render on.
     */
    Graphics2D getGraphics();

    /**
     * Called when switching to this renderer.
     *
     * This should set up our {@link java.awt.Panel} to make use of this renderer.
     * Among any other setup code this renderer needs.
     */
    void update();

    /**
     * Produces a radio menu item for selecting this renderer.
     *
     * @param lcd The {@link nitrous.lcd.LCD} whose renderer would be switched.
     * @return a {@link javax.swing.JRadioButtonMenuItem} to switch to this renderer.
     */
    JRadioButtonMenuItem getRadioMenuItem(LCD lcd);
}
