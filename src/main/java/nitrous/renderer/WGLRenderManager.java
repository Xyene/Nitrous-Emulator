package nitrous.renderer;

import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Method;

public class WGLRenderManager extends AbstractRenderManager
{
    public WGLRenderManager(ComponentPeer peer)
    {
        super(peer, "sun.awt.windows.WComponentPeer", "sun.java2d.opengl.WGLSurfaceData");
    }

    @Override
    protected GraphicsConfiguration getGraphicsConfig()
    {
        GraphicsConfiguration old = super.getGraphicsConfig();

        try
        {
            Class<?> WGLGraphicsConfig = Class.forName("sun.java2d.opengl.WGLGraphicsConfig");
            Class<?> Win32GraphicsDevice = Class.forName("sun.awt.Win32GraphicsDevice");
            Class<?> Win32GraphicsConfig = Class.forName("sun.awt.Win32GraphicsConfig");

            int visual = (int) Win32GraphicsConfig.getMethod("getVisual").invoke(old);

            Method getConfig = WGLGraphicsConfig.getMethod("getConfig", Win32GraphicsDevice, int.class);
            return (GraphicsConfiguration) getConfig.invoke(null, old.getDevice(), visual);
        } catch (ReflectiveOperationException e)
        {
//            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected String getName()
    {
        return "OpenGL";
    }
}
