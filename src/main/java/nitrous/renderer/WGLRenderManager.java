package nitrous.renderer;

import sun.awt.Win32GraphicsConfig;
import sun.awt.Win32GraphicsDevice;

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
            Method getConfig = WGLGraphicsConfig.getMethod("getConfig", Win32GraphicsDevice.class, int.class);
            return (GraphicsConfiguration) getConfig.invoke(null, old.getDevice(),
                    ((Win32GraphicsConfig) old).getVisual());
        } catch (ReflectiveOperationException e)
        {e.printStackTrace();
            return null;
        }
    }

    @Override
    protected String getName()
    {
        return "OpenGL";
    }
}
