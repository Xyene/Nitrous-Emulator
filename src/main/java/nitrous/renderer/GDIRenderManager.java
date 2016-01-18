package nitrous.renderer;

import java.awt.peer.ComponentPeer;


/**
 * A renderer that uses Java's GDI support.
 */
public class GDIRenderManager extends AbstractRenderManager
{
    /**
     * Constructs a GDI renderer for the {@link java.awt.Panel} whose peer is given.
     *
     * @param peer the peer of the {@link java.awt.Panel} for which the renderer is constructed.
     */
    public GDIRenderManager(ComponentPeer peer)
    {
        super(peer, "sun.awt.windows.WComponentPeer", "sun.java2d.windows.GDIWindowSurfaceData");
    }//end GDIRenderManager(peer)

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getName()
    {
        return "GDI";
    }//end getName
}//end class GDIRenderManager
