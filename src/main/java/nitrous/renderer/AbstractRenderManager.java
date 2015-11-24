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
    private ComponentPeer peer;
    private Class<?> peerClass;
    private Class<?> surfaceClass;
    private Graphics2D graphics2D;
    private GraphicsConfiguration graphicsConfiguration;

    public AbstractRenderManager(ComponentPeer peer, String peerClass, String surfaceClass)
    {
        this.peer = peer;

        try
        {
            this.peerClass = Class.forName(peerClass);
            this.surfaceClass = Class.forName(surfaceClass);
        } catch (ClassNotFoundException e)
        {
            return;
        }

        graphicsConfiguration = getGraphicsConfig();

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
                System.err.println("Could not create SurfaceData");
                return null;
            }

            System.err.println(surf);

            return new SunGraphics2D(surf, Color.BLACK, Color.BLACK, null);
        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    protected abstract String getName();

    @Override
    public Graphics2D getGraphics()
    {
        peer.updateGraphicsData(graphicsConfiguration);
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
                    System.err.println(AbstractRenderManager.this.getName());
                    lcd.currentRenderer = AbstractRenderManager.this;
                });
            }
        };
    }
}