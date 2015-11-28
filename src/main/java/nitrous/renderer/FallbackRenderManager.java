package nitrous.renderer;

import nitrous.lcd.LCD;

import javax.swing.*;
import java.awt.*;
import java.awt.peer.ComponentPeer;

public class FallbackRenderManager implements IRenderManager
{
    private final ComponentPeer peer;

    public FallbackRenderManager(ComponentPeer peer) {
        this.peer = peer;
    }

    @Override
    public Graphics2D getGraphics()
    {
        Toolkit.getDefaultToolkit().sync();
        return (Graphics2D) peer.getGraphics();
    }

    @Override
    public void update()
    {
    }

    @Override
    public JRadioButtonMenuItem getRadioMenuItem(LCD lcd)
    {
        return new JRadioButtonMenuItem("???")
        {
            {
                addActionListener((x) ->
                {
                    System.err.println("Switched to fallback renderer.");
                    lcd.currentRenderer = FallbackRenderManager.this;
                    lcd.currentRenderer.update();
                });
            }
        };
    }
}
