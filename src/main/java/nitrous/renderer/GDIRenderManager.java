package nitrous.renderer;

import java.awt.peer.ComponentPeer;

public class GDIRenderManager extends AbstractRenderManager
{
    public GDIRenderManager(ComponentPeer peer)
    {
        super(peer, "sun.awt.windows.WComponentPeer", "sun.java2d.windows.GDIWindowSurfaceData");
    }

    @Override
    protected String getName()
    {
        return "GDI";
    }
}
