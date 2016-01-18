package nitrous.renderer;

import nitrous.lcd.LCD;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;

import javax.swing.*;
import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Method;

/**
 * An basic implementation of {@link nitrous.renderer.IRenderManager} that
 * works for most situations involving renderers that Java support.
 *
 * @author Quantum
 * @author Tudor
 */
public abstract class AbstractRenderManager implements IRenderManager
{
    /**
     * The native peer of our {@link java.awt.Panel}, upon which we render.
     */
    protected final ComponentPeer peer;

    /**
     * This is the class for our peer.
     */
    protected Class<?> peerClass;

    /**
     * This is the surface that would be used by this render manager to render.
     */
    protected Class<?> surfaceClass;

    /**
     * This field caches the {@link java.awt.Graphics2D} object so that
     * {@link #getGraphics} doesn't have to create one every frame.
     */
    protected Graphics2D graphics2D;

    /**
     * The {@link java.awt.GraphicsConfiguration} that describes the environment
     * used by this renderer.
     */
    protected GraphicsConfiguration graphicsConfiguration;

    /**
     * Sets up the most common renderers.
     *
     * @param peer         the native peer of our {@link java.awt.Panel} upon which we operate
     * @param peerClass    the full class name of the peer class we support
     * @param surfaceClass the full class name of the surface used by this render
     */
    public AbstractRenderManager(ComponentPeer peer, String peerClass, String surfaceClass)
    {
        this.peer = peer;

        // Attempt to get the class object for the classes.
        try
        {
            this.peerClass = Class.forName(peerClass);
            this.surfaceClass = Class.forName(surfaceClass);
        } catch (ClassNotFoundException e)
        {
            // #error this renderer is not supported on current platform
            // Avoid checked exceptions, they make interfaces much more complicated than necessary.
            throw new RuntimeException(e);
        }//end try

        // Cache the current GraphicsConfiguration.
        graphicsConfiguration = getGraphicsConfig();

        // Switch to this renderer for the first time to test.
        update();

        // If this is available, attempt to create the Graphics2D.
        if (graphicsConfiguration != null)
            graphics2D = createGraphics();
    }//end AbstractRenderManager(peer, peerClass, surfaceClass)

    /**
     * Retrieves the {@link java.awt.GraphicsConfiguration} used by this renderer.
     *
     * @return the {@link java.awt.GraphicsConfiguration}
     */
    protected GraphicsConfiguration getGraphicsConfig()
    {
        return peer.getGraphicsConfiguration();
    }//end getGraphicsConfig

    /**
     * Attempt to create a surface and convert it to a {@link java.awt.Graphics2D} object.
     * <p/>
     * In doing so, this method temporarily switches to the new {@link java.awt.GraphicsConfiguration}.
     * This method does not cache the newly created {@link java.awt.Graphics2D} object.
     *
     * #method
     *
     * @return a {@link java.awt.Graphics2D} object.
     */
    protected Graphics2D createGraphics()
    {
        try
        {
            // Store the old GraphicsConfiguration.
            GraphicsConfiguration oldGC = peer.getGraphicsConfiguration();

            // Switch to the new GraphicsConfiguration.
            peer.updateGraphicsData(graphicsConfiguration);

            // Creates the surface.
            Method createData = surfaceClass.getDeclaredMethod("createData", peerClass);
            SurfaceData surf = (SurfaceData) createData.invoke(null, peer);

            // Switch back to the old GraphicsConfiguration.
            peer.updateGraphicsData(oldGC);

            // Fail if the surface is not created.
            if (surf == null)
            {
                return null;
            }//end if

            // Use unofficial APi to convert the surface to Graphics2D.
            return new SunGraphics2D(surf, Color.BLACK, Color.BLACK, null);
        } catch (Exception e)
        {
            // #error If anything bad happens, we fail.
            return null;
        }//end try
    }//end createGraphics

    /**
     * Called to get the name of the current renderer.
     *
     * @return the name as a string
     */
    protected abstract String getName();

    /**
     * {@inheritDoc}
     */
    @Override
    public void update()
    {
        // Switch to the GraphicsConfiguration used by this renderer.
        peer.updateGraphicsData(graphicsConfiguration);
    }//end update

    /**
     * {@inheritDoc}
     */
    @Override
    public Graphics2D getGraphics()
    {
        // If not cached, or the cached Graphics2D is no longer valid:
        if (graphics2D != null && ((SunGraphics2D) graphics2D).surfaceData.isSurfaceLost())
        {
            // Recreate the Graphics2D.
            graphics2D = createGraphics();
            new RuntimeException("surface lost").printStackTrace();
        }//end if
        // Return the cached Graphics2D.
        return graphics2D;
    }//end getGraphics

    /**
     * {@inheritDoc}
     */
    @Override
    public JRadioButtonMenuItem getRadioMenuItem(LCD lcd)
    {
        // Create a radio menu item with the name
        return new JRadioButtonMenuItem(getName())
        {
            {
                // On click, we do switch renderer and call its update() method.
                // #action on click of the menu item
                addActionListener((x) ->
                {
                    System.err.println("Switched to " + AbstractRenderManager.this.getName() + " renderer.");
                    lcd.currentRenderer = AbstractRenderManager.this;
                    lcd.currentRenderer.update();
                });
            }
        };
    }//end getRadioMenuItem
}//end class AbstractRenderManager