package nitrous.renderer;

import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class D3DRenderManager extends AbstractRenderManager
{
    public D3DRenderManager(ComponentPeer peer)
    {
        super(peer, "sun.awt.windows.WComponentPeer", "sun.java2d.d3d.D3DSurfaceData");
    }

    @Override
    protected GraphicsConfiguration getGraphicsConfig()
    {
        try
        {
            Class<?> D3DGraphicsDevice = Class.forName("sun.java2d.d3d.D3DGraphicsDevice");
            Class<?> D3DGraphicsConfig = Class.forName("sun.java2d.d3d.D3DGraphicsConfig");
            Class<?> ContextCapabilities = Class.forName("sun.java2d.pipe.hw.ContextCapabilities");

            int screen = (int) D3DGraphicsDevice.getMethod("getScreen").invoke(super.getGraphicsConfig().getDevice());

            Method getDeviceCaps = D3DGraphicsDevice.getDeclaredMethod("getDeviceCaps", int.class);
            getDeviceCaps.setAccessible(true);
            Object d3dCaps = getDeviceCaps.invoke(null, screen);

            int CAPS_OK = 1 << 18;
            if (((int) ContextCapabilities.getMethod("getCaps").invoke(d3dCaps) & CAPS_OK) == 0)
            {
                throw new RuntimeException("Could not enable Direct3D pipeline on " + "screen " + screen);
            }

//            System.err.println("!!Direct3D pipeline enabled on screen " + screen);

            Constructor<?> newD3DGraphicsDevice =
                    D3DGraphicsDevice.getDeclaredConstructor(int.class, ContextCapabilities);
            newD3DGraphicsDevice.setAccessible(true);
            Constructor<?> newD3DGraphicsConfig =
                    D3DGraphicsConfig.getDeclaredConstructor(D3DGraphicsDevice);
            newD3DGraphicsConfig.setAccessible(true);

            Object /* D3DGraphicsDevice */ device = newD3DGraphicsDevice.newInstance(screen, d3dCaps);
            return (GraphicsConfiguration) newD3DGraphicsConfig.newInstance(device);
        } catch (Exception e)
        {
//            e.printStackTrace();
            return super.getGraphicsConfig();
        }
    }

    @Override
    protected String getName()
    {
        return "DirectDraw";
    }
}
