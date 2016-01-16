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
 * @author Tudor
 */
public class LabelBuilder
{

    private ArrayList<Component> bits = new ArrayList<Component>();
    private Color foreground = UIManager.getColor("Label.foreground"), active = new Color(240, 0, 0);
    private Color link = new Color(0x0000EE);
    private Font font;
    private int cursor;

    /**
     * Sets the text foreground color
     *
     * @param color the color
     */
    public LabelBuilder setForeground(Color color)
    {
        foreground = color;
        return this;
    }

    /**
     * Sets the active text color
     *
     * @param color the color;
     * @return this object
     */
    public LabelBuilder setActive(Color color)
    {
        active = color;
        return this;
    }

    /**
     * Sets the text font
     *
     * @param fon the font
     * @return this object
     */
    public LabelBuilder setFont(Font fon)
    {
        font = fon;
        return this;
    }

    /**
     * Sets the mouseover cursor
     *
     * @param curse the cursor id
     * @return this object
     */
    public LabelBuilder setCursor(int curse)
    {
        cursor = curse;
        return this;
    }

    /**
     * Appends a normal label
     *
     * @param text the text to display
     * @return this object
     */
    public LabelBuilder append(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(foreground);
        label.setFont(font);
        label.setCursor(new Cursor(cursor));
        bits.add(label);
        return this;
    }

    /**
     * Appends an action label
     *
     * @param text the text to display
     * @param act  the action to perform on click
     * @return this object
     */
    public LabelBuilder action(String text, final ActionListener act)
    {
        final JLabel button = new JLabel(text);
        button.setForeground(link);
        button.setFont(font);
        button.setCursor(new Cursor(cursor));

        button.setOpaque(false);
        button.setFocusable(false);
        button.setBorder(null);

        button.addMouseListener(new MouseAdapter()
        {
            Font font;
            Color color;
            boolean active,
                    clicked;

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
                        EventQueue.invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                act.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, "label_action"));
                            }
                        });
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
        bits.add(button);
        return this;
    }

    /**
     * Appends a hyperlinked label
     *
     * @param text the text to display
     * @param uri  the link, in String form
     * @return this object
     */
    public LabelBuilder hyperlink(String text, String uri)
    {
        try
        {
            return hyperlink(text, new URI(uri));
        } catch (URISyntaxException ex)
        {
            throw new RuntimeException("invalid uri", ex);
        }
    }

    /**
     * Appends a hyperlinked label
     *
     * @param text the text to display
     * @param uri  the link
     * @return this object
     */
    public LabelBuilder hyperlink(String text, final URI uri)
    {
        action(text, new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (Desktop.isDesktopSupported())
                {
                    try
                    {
                        Desktop.getDesktop().browse(uri);
                    } catch (IOException io)
                    {
                        throw new RuntimeException("IO error while opening URI", io);
                    }
                } else
                {
                    throw new UnknownError("desktop not supported");
                }
            }
        });
        return this;
    }

    /**
     * Generates a container for this compound label
     *
     * @return a JPanel hosting all labels
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

    public List<Component> createRaw()
    {
        return bits;
    }
}