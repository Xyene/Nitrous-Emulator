package nitrous.renderer;

import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * A renderer that uses Java's Direct3D support.
 * <p/>
 * Requires system property {@literal sun.java2d.d3d} to be set to {@literal true}.
 */
public class D3DRenderManager extends AbstractRenderManager
{
    /**
     * Constructs a Direct3D renderer for the {@link java.awt.Panel} whose peer is given.
     *
     * @param peer the peer of the {@link java.awt.Panel} for which the renderer is constructed.
     */
    public D3DRenderManager(ComponentPeer peer)
    {
        super(peer, "sun.awt.windows.WComponentPeer", "sun.java2d.d3d.D3DSurfaceData");
    }//end D3DRenderManager(peer)

    /**
     * {@inheritDoc}
     */
    @Override
    protected GraphicsConfiguration getGraphicsConfig()
    {
        try
        {
            // We use reflection so that this class loads on linux.
            Class<?> D3DGraphicsDevice = Class.forName("sun.java2d.d3d.D3DGraphicsDevice");
            Class<?> D3DGraphicsConfig = Class.forName("sun.java2d.d3d.D3DGraphicsConfig");
            Class<?> ContextCapabilities = Class.forName("sun.java2d.pipe.hw.ContextCapabilities");

            // Find the screen id.
            int screen = (int) D3DGraphicsDevice.getMethod("getScreen").invoke(super.getGraphicsConfig().getDevice());

            // Find the device capability.
            Method getDeviceCaps = D3DGraphicsDevice.getDeclaredMethod("getDeviceCaps", int.class);
            getDeviceCaps.setAccessible(true);
            Object d3dCaps = getDeviceCaps.invoke(null, screen);

            // Check Direct3D pipeline support.
            int CAPS_OK = 1 << 18;
            if (((int) ContextCapabilities.getMethod("getCaps").invoke(d3dCaps) & CAPS_OK) == 0)
            {
                throw new RuntimeException("Could not enable Direct3D pipeline on " + "screen " + screen);
            }//end if

            // Find constructors for Direct3D graphics device and configuration
            Constructor<?> newD3DGraphicsDevice =
                    D3DGraphicsDevice.getDeclaredConstructor(int.class, ContextCapabilities);
            newD3DGraphicsDevice.setAccessible(true);
            Constructor<?> newD3DGraphicsConfig =
                    D3DGraphicsConfig.getDeclaredConstructor(D3DGraphicsDevice);
            newD3DGraphicsConfig.setAccessible(true);

            // Create the Direct3D graphics device.
            Object /* D3DGraphicsDevice */ device = newD3DGraphicsDevice.newInstance(screen, d3dCaps);

            // Create the Direct3D graphics configuration.
            return (GraphicsConfiguration) newD3DGraphicsConfig.newInstance(device);
        } catch (Exception e)
        {
            // Otherwise, we fall back to the default and hope for the best.
            return super.getGraphicsConfig();
        }//end try
    }//end getGraphicsConfig

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getName()
    {
        return "DirectDraw";
    }//end getName
}//end class D3DRenderManager
