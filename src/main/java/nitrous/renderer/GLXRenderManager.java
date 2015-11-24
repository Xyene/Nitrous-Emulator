package nitrous.renderer;

import sun.awt.Win32GraphicsConfig;
import sun.awt.Win32GraphicsDevice;

import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Method;

public class GLXRenderManager extends AbstractRenderManager
{
    public GLXRenderManager(ComponentPeer peer)
    {
        super(peer, "sun.awt.X11ComponentPeer", "sun.java2d.opengl.GLXSurfaceData");
    }

    @Override
    protected GraphicsConfiguration getGraphicsConfig()
    {
        GraphicsConfiguration old = super.getGraphicsConfig();

        try
        {
            Class<?> GLXGraphicsConfig = Class.forName("sun.java2d.opengl.GLXGraphicsConfig");
            Class<?> X11GraphicsConfig = Class.forName("sun.awt.X11GraphicsConfig");
            Class<?> X11GraphicsEnvironment = Class.forName("sun.awt.X11GraphicsEnvironment");
            Class<?> X11GraphicsDevice = Class.forName("sun.awt.X11GraphicsDevice");

            Method isGLXAvailable = X11GraphicsEnvironment.getMethod("isGLXAvailable");
            if (!(boolean) isGLXAvailable.invoke(null))
            {
                System.err.println("GLX unavailable!");
                throw new RuntimeException("GLX unavailable");
            }

            System.out.println(old.getDevice());

            Method getVisual = X11GraphicsConfig.getMethod("getVisual");

            int visual = (int) getVisual.invoke(old);
            System.out.println(visual + " <<< visual");

            Method getConfig = GLXGraphicsConfig.getMethod("getConfig", X11GraphicsDevice, int.class);
            GraphicsConfiguration gc = (GraphicsConfiguration) getConfig.invoke(null, old.getDevice(), visual);
            System.out.println(">>>>> " + gc);
            return gc;

        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    protected String getName()
    {
        return "GLX";
    }
}
