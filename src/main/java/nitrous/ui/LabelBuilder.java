package nitrous.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Map;

/**
 * Fluent helper class to create rich labels.
 *
 * @author Tudor
 */
public class LabelBuilder
{
    /**
     * Container for all label components.
     */
    private final ArrayList<Component> bits = new ArrayList<>();

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
    }//end setFont

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
    }//end append

    /**
     * Appends an action label.
     *
     * #action our fluent interface of adding action listeners
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

                // #action handle mouse movement and clicks on the label
                addMouseListener(new MouseAdapter()
                {
                    Font font;
                    Color color;
                    boolean active, clicked;

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void mousePressed(MouseEvent e)
                    {
                        active = clicked = true;
                        color = e.getComponent().getForeground();
                        e.getComponent().setForeground(LabelBuilder.this.active);
                    }//end mousePressed

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void mouseReleased(MouseEvent e)
                    {
                        if (active)
                        {
                            if (clicked)
                            {
                                // #action we use mouseReleased to invoke actionPerformed
                                SwingUtilities.invokeLater(() -> act.actionPerformed(new ActionEvent(this,
                                        ActionEvent.ACTION_PERFORMED, "label_action"))
                                );
                            }//end if
                            mouseExited(e);
                            active = false;
                        }//end if
                    }//end mouseReleased

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    @SuppressWarnings("unchecked")
                    public void mouseEntered(MouseEvent e)
                    {
                        if (!active)
                        {
                            font = e.getComponent().getFont();
                            Map attributes = font.getAttributes();
                            attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                            e.getComponent().setFont(font.deriveFont(attributes));
                        }//end if
                    }//end mouseEntered

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void mouseExited(MouseEvent e)
                    {
                        e.getComponent().setFont(font);
                        if (clicked)
                        {
                            e.getComponent().setForeground(color);
                            clicked = false;
                        }//end if
                    }//end mouseExited
                });
            }
        });
        return this;
    }//end action

    /**
     * Generates a container for this compound label.
     *
     * @return a JPanel hosting all labels.
     */
    public JPanel create()
    {
        JPanel panel = new JPanel(new GridBagLayout());
        bits.forEach(panel::add);
        return panel;
    }//end create
}//end class LabelBuilder