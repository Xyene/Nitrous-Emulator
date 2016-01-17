package nitrous;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fluent helper class to create rich labels.
 */
public class LabelBuilder
{

    /**
     * Container for all label components.
     */
    private ArrayList<Component> bits = new ArrayList<Component>();

    /**
     * Default foreground color.
     */
    private Color foreground = UIManager.getColor("Label.foreground");

    /**
     * Default active color for action labels.
     */
    private Color active = new Color(0xF00000);

    /**
     * Default link color for action labels.
     */
    private Color link = new Color(0x0000EE);

    /**
     * Default font.
     */
    private Font font;

    /**
     * Sets the text font.
     *
     * @param font The font.
     * @return This object.
     */
    public LabelBuilder setFont(Font font)
    {
        this.font = font;
        return this;
    }

    /**
     * Appends a normal label.
     *
     * @param text The text to display,
     * @return This object.
     */
    public LabelBuilder append(String text)
    {
        bits.add(new JLabel(text)
        {
            {
                setForeground(foreground);
                if (font != null) setFont(font);
            }
        });
        return this;
    }

    /**
     * Appends an action label.
     *
     * @param text The text to display.
     * @param act  The action to perform on click.
     * @return This object.
     */
    public LabelBuilder action(String text, ActionListener act)
    {
        bits.add(new JLabel(text)
        {
            {
                setForeground(link);
                if (font != null) setFont(font);

                setOpaque(false);
                setFocusable(false);
                setBorder(null);

                addMouseListener(new MouseAdapter()
                {
                    Font font;
                    Color color;
                    boolean active, clicked;

                    @Override
                    public void mousePressed(MouseEvent e)
                    {
                        active = clicked = true;
                        color = e.getComponent().getForeground();
                        e.getComponent().setForeground(LabelBuilder.this.active);
                    }

                    @Override
                    public void mouseReleased(MouseEvent e)
                    {
                        if (active)
                        {
                            if (clicked)
                            {
                                SwingUtilities.invokeLater(() -> {
                                            act.actionPerformed(new ActionEvent(this,
                                                    ActionEvent.ACTION_PERFORMED, "label_action"));
                                        }
                                );
                            }
                            mouseExited(e);
                            active = false;
                        }
                    }

                    @Override
                    public void mouseEntered(MouseEvent e)
                    {
                        if (!active)
                        {
                            font = e.getComponent().getFont();
                            Map attributes = font.getAttributes();
                            attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                            e.getComponent().setFont(font.deriveFont(attributes));
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent e)
                    {
                        e.getComponent().setFont(font);
                        if (clicked)
                        {
                            e.getComponent().setForeground(color);
                            clicked = false;
                        }
                    }
                });
            }
        });
        return this;
    }

    /**
     * Generates a container for this compound label.
     *
     * @return a JPanel hosting all labels.
     */
    public JPanel create()
    {
        JPanel panel = new JPanel(new GridBagLayout());
        for (Component comp : bits)
        {
            panel.add(comp);
        }
        return panel;
    }
}