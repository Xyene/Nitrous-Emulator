package nitrous.renderer;

import nitrous.lcd.LCD;

import javax.swing.*;
import java.awt.*;

public interface IRenderManager
{
    Class<? extends IRenderManager>[] RENDERERS = new Class[]{
            GDIRenderManager.class, // GDI is fastest for drawing just one image
            WGLRenderManager.class, // Some cards may do GL better than D3D, but this is unlikely
            D3DRenderManager.class, // Followed by D3D (on most cards)
            XRenderManager.class, // Linux!
    };

    Graphics2D getGraphics();

    void update();

    JRadioButtonMenuItem getRadioMenuItem(LCD lcd);
}
