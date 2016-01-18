package nitrous.renderer;

import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Method;

/**
 * A renderer that uses Java's Windows OpenGL support.
 * <p/>
 * Works with system property {@literal sun.java2d.opengl} set to {@literal false}.
 *
 * @author Quantum
 * @author Tudor
 */
public class WGLRenderManager extends AbstractRenderManager
{
    /**
     * Constructs a Windows OpenGL renderer for the {@link java.awt.Panel} whose peer is given.
     *
     * @param peer the peer of the {@link java.awt.Panel} for which the renderer is constructed.
     */
    public WGLRenderManager(ComponentPeer peer)
    {
        super(peer, "sun.awt.windows.WComponentPeer", "sun.java2d.opengl.WGLSurfaceData");
    }//end WGLRenderManager(peer)

    /**
     * {@inheritDoc}
     */
    @Override
    protected GraphicsConfiguration getGraphicsConfig()
    {
        // Cache the old graphics configuration.
        GraphicsConfiguration old = super.getGraphicsConfig();

        try
        {
            // Get the Windows OpenGL implementation classes with reflection.
            Class<?> WGLGraphicsConfig = Class.forName("sun.java2d.opengl.WGLGraphicsConfig");
            Class<?> Win32GraphicsDevice = Class.forName("sun.awt.Win32GraphicsDevice");
            Class<?> Win32GraphicsConfig = Class.forName("sun.awt.Win32GraphicsConfig");

            // Get the visual id.
            int visual = (int) Win32GraphicsConfig.getMethod("getVisual").invoke(old);

            // Gets the method to get the GraphicsConfiguration for Windows OpenGL.
            Method getConfig = WGLGraphicsConfig.getMethod("getConfig", Win32GraphicsDevice, int.class);

            // Get the GraphicsConfiguration for the graphics device and visual id.
            return (GraphicsConfiguration) getConfig.invoke(null, old.getDevice(), visual);
        } catch (ReflectiveOperationException e)
        {
            // #error If reflection fails, we fail.
            return null;
        }//end try
    }//end getGraphicsConfig

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getName()
    {
        return "OpenGL";
    }//end getName
}//end class WGLRenderManager
