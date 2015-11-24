package nitrous.renderer;

import java.awt.peer.ComponentPeer;

public class GLXRenderManager extends AbstractRenderManager
{
    public GLXRenderManager(ComponentPeer peer)
    {
        super(peer, "sun.awt.X11ComponentPeer", "sun.java2d.opengl.GLXSurfaceData");
    }

    @Override
    protected String getName()
    {
        return "GLX";
    }
}
