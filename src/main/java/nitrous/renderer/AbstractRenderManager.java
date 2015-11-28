package nitrous.renderer;

import nitrous.lcd.LCD;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;

import javax.swing.*;
import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Method;

public abstract class AbstractRenderManager implements IRenderManager
{
    protected final ComponentPeer peer;
    protected Class<?> peerClass;
    protected Class<?> surfaceClass;
    protected Graphics2D graphics2D;
    protected GraphicsConfiguration graphicsConfiguration;

    public AbstractRenderManager(ComponentPeer peer, String peerClass, String surfaceClass)
    {
        this.peer = peer;

        try
        {
            this.peerClass = Class.forName(peerClass);
            this.surfaceClass = Class.forName(surfaceClass);
        } catch (ClassNotFoundException e)
        {
            throw new RuntimeException(e);
        }

        graphicsConfiguration = getGraphicsConfig();

        update();

        if (graphicsConfiguration != null)
            graphics2D = createGraphics();
    }

    protected GraphicsConfiguration getGraphicsConfig()
    {
        return peer.getGraphicsConfiguration();
    }

    protected Graphics2D createGraphics()
    {
        try
        {
            GraphicsConfiguration oldGC = peer.getGraphicsConfiguration();
            peer.updateGraphicsData(graphicsConfiguration);

            Method createData = surfaceClass.getDeclaredMethod("createData", peerClass);
            SurfaceData surf = (SurfaceData) createData.invoke(null, peer);

            peer.updateGraphicsData(oldGC);

            if (surf == null)
            {
                return null;
            }

            return new SunGraphics2D(surf, Color.BLACK, Color.BLACK, null);
        } catch (Exception e)
        {
            return null;
        }
    }

    protected abstract String getName();

    @Override
    public void update()
    {
        peer.updateGraphicsData(graphicsConfiguration);
    }

    @Override
    public Graphics2D getGraphics()
    {
        if (graphics2D != null && ((SunGraphics2D) graphics2D).surfaceData.isSurfaceLost())
        {
            createGraphics();
            new RuntimeException("surface lost").printStackTrace();
        }
        return graphics2D;
    }

    @Override
    public JRadioButtonMenuItem getRadioMenuItem(LCD lcd)
    {
        return new JRadioButtonMenuItem(getName())
        {
            {
                addActionListener((x) ->
                {
                    System.err.println("Switched to " + AbstractRenderManager.this.getName() + " renderer.");
                    lcd.currentRenderer = AbstractRenderManager.this;
                    lcd.currentRenderer.update();
                });
            }
        };
    }
}