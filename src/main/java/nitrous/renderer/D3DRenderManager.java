package nitrous.renderer;

import sun.awt.Win32GraphicsConfig;
import sun.awt.Win32GraphicsDevice;
import sun.java2d.d3d.D3DGraphicsConfig;
import sun.java2d.d3d.D3DGraphicsDevice;
import sun.java2d.d3d.D3DSurfaceData;
import sun.java2d.opengl.WGLGraphicsConfig;
import sun.java2d.pipe.hw.ContextCapabilities;
import sun.java2d.windows.WindowsFlags;


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
        int screen = 0;

        try
        {
            Class D3DGraphicsDevice = Class.forName("sun.java2d.d3d.D3DGraphicsDevice");
            Class D3DGraphicsConfig = Class.forName("sun.java2d.d3d.D3DGraphicsConfig");

            Method getDeviceCaps = D3DGraphicsDevice.getDeclaredMethod("getDeviceCaps", int.class);
            getDeviceCaps.setAccessible(true);
            ContextCapabilities d3dCaps = (ContextCapabilities) getDeviceCaps.invoke(null, screen);

            int CAPS_OK = 1 << 18;
            if ((d3dCaps.getCaps() & CAPS_OK) == 0)
            {
                throw new RuntimeException("Could not enable Direct3D pipeline on " + "screen " + screen);
            }

            System.err.println("!!Direct3D pipeline enabled on screen " + screen);

            Constructor newD3DGraphicsDevice = D3DGraphicsDevice
                    .getDeclaredConstructor(int.class, ContextCapabilities.class);
            newD3DGraphicsDevice.setAccessible(true);
            Constructor newD3DGraphicsConfig = D3DGraphicsConfig.getDeclaredConstructor(D3DGraphicsDevice);
            newD3DGraphicsConfig.setAccessible(true);

            Object /* D3DGraphicsDevice */ device = newD3DGraphicsDevice.newInstance(screen, d3dCaps);
            return (GraphicsConfiguration) newD3DGraphicsConfig.newInstance(device);
        } catch (Exception e)
        {
            e.printStackTrace();
            return super.getGraphicsConfig();
        }
    }

    @Override
    protected String getName()
    {
        return "DirectDraw";
    }
}
