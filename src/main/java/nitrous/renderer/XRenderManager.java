package nitrous.renderer;

import nitrous.lcd.LCD;

import javax.swing.*;
import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A renderer that uses Java's rendering support on Linux.
 * <p/>
 * In order, it attempts to initialize XRender, GLX, and X11
 */
public class XRenderManager implements IRenderManager
{
    protected AbstractRenderManager backing;

    public XRenderManager(ComponentPeer peer)
    {
        Class[] attempts = {XRRenderManager.class, GLXRenderManager.class, X11RenderManager.class};

        for (Class renderer : attempts)
        {
            try
            {
                backing = (AbstractRenderManager) renderer.getConstructor(ComponentPeer.class).newInstance(peer);
                if (backing.getGraphics() != null) break;
            } catch (ReflectiveOperationException ignored)
            {
            }
        }
        if (backing == null || backing.getGraphics() == null) throw new RuntimeException("unable to draw!");
    }

    @Override
    public Graphics2D getGraphics()
    {
        return backing.getGraphics();
    }

    @Override
    public void update()
    {
        backing.update();
    }

    @Override
    public JRadioButtonMenuItem getRadioMenuItem(LCD lcd)
    {
        return new JRadioButtonMenuItem(backing.getName())
        {
            {
                addActionListener((x) ->
                {
                    System.err.println("Switched to " + backing.getName() + " renderer.");
                    lcd.currentRenderer = XRenderManager.this;
                });
            }
        };
    }

    /**
     * On most modern systems we'll get away with just this - XRender.
     */
    private static class XRRenderManager extends AbstractRenderManager
    {
        public XRRenderManager(ComponentPeer peer)
        {
            super(peer, "sun.awt.X11ComponentPeer", "sun.java2d.xr.XRSurfaceData");
        }

        @Override
        protected String getName()
        {
            return "X Render";
        }
    }

    /**
     * On an ancient system or if the JVM is started with -Dsun.java2d.xrender=false, X Render will fail to initialize
     * so we have to rely on X11 to render.
     */
    private static class X11RenderManager extends AbstractRenderManager
    {
        public X11RenderManager(ComponentPeer peer)
        {
            super(peer, "sun.awt.X11ComponentPeer", "sun.java2d.x11.X11SurfaceData");
        }

        @Override
        protected GraphicsConfiguration getGraphicsConfig()
        {
            GraphicsConfiguration old = super.getGraphicsConfig();

            try
            {
                Class<?> X11GraphicsConfig = Class.forName("sun.awt.X11GraphicsConfig");
                Class<?> X11GraphicsDevice = Class.forName("sun.awt.X11GraphicsDevice");

                int visual = (int) X11GraphicsConfig.getMethod("getVisual").invoke(old);

                Method getConfig = X11GraphicsConfig.getMethod("getConfig", X11GraphicsDevice, int.class, int.class, int.class,
                        boolean.class);

                // I pulled these values out of nowhere, may be wrong
                return (GraphicsConfiguration) getConfig.invoke(null, old.getDevice(), visual,
                        24, // depth
                        0, // colormap
                        false // double buffer
                );
            } catch (Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected String getName()
        {
            return "X11";
        }
    }

    public static class GLXRenderManager extends AbstractRenderManager
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
                //            System.out.println("!!!!!!!!!!!" + old);
                Class<?> GLXGraphicsConfig = Class.forName("sun.java2d.opengl.GLXGraphicsConfig");
                Class<?> X11GraphicsConfig = Class.forName("sun.awt.X11GraphicsConfig");
                Class<?> X11GraphicsEnvironment = Class.forName("sun.awt.X11GraphicsEnvironment");
                Class<?> X11GraphicsDevice = Class.forName("sun.awt.X11GraphicsDevice");

                Method isGLXAvailable = X11GraphicsEnvironment.getMethod("isGLXAvailable");
                if (!(boolean) isGLXAvailable.invoke(null))
                {
                    //                System.err.println("GLX unavailable!");
                    throw new RuntimeException("GLX unavailable");
                }

                System.out.println(old.getDevice());

                Method getVisual = X11GraphicsConfig.getMethod("getVisual");

                int visual = (int) getVisual.invoke(old);
                //            System.out.println(visual + " <<< visual");

                Method getConfig = GLXGraphicsConfig.getMethod("getConfig", X11GraphicsDevice, int.class);
                //            System.out.println(">>>>> " + gc);
                return (GraphicsConfiguration) getConfig.invoke(null, old.getDevice(), visual);
            } catch (Exception e)
            {
                //            e.printStackTrace();
                return null;
            }
        }

        @Override
        protected String getName()
        {
            return "GLX";
        }
    }
}
