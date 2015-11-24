package nitrous.renderer;

import java.awt.peer.ComponentPeer;

public class X11RenderManager extends AbstractRenderManager
{
    public X11RenderManager(ComponentPeer peer)
    {
        super(peer, "sun.awt.X11ComponentPeer", "sun.java2d.xr.XRSurfaceData");
    }

    @Override
    protected String getName()
    {
        return "X Render";
    }
}
