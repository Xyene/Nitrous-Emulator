package nitrous;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * A {@link JPanel} to manage keybinding selection.
 */
public class KeybindingSelectionPanel extends JPanel
{
    /**
     * Create the Panel.
     *
     * @param keybinding the underlying keybinding storage
     */
    public KeybindingSelectionPanel(Keybinding keybinding)
    {
        setLayout(new GridLayout(0, 2, 10, 0));

        // Loop over all keys.
        for (int i = 0; i < keybinding.getKeyCount(); i++)
        {
            // Use a new variable every time.
            // Otherwise, all inner classes will see i = key count by the time the loop finishes.
            int button = i;

            // Get the key name and current setting.
            String id = keybinding.getKeyName(i);
            int keystroke = keybinding.getKeystroke(i);

            // Create a panel to contain the button name label and a text field to display the binding.
            add(new JPanel()
            {
                {
                    // THe button name label.
                    add(new JLabel(id)
                    {
                        {
                            setPreferredSize(new Dimension(40, getPreferredSize().height));
                        }
                    });

                    // The binding text field.
                    add(new JTextField(10)
                    {
                        {
                            // Show the name of the current bound key.
                            setForeground(getDisabledTextColor());
                            setText(KeyEvent.getKeyText(keystroke));

                            // Event handler to update biding.
                            addKeyListener(new KeyAdapter()
                            {
                                @Override
                                public void keyTyped(KeyEvent e)
                                {
                                    e.consume();
                                }

                                @Override
                                public void keyPressed(KeyEvent e)
                                {
                                    e.consume();

                                    // When a key is pressed while the TextField is focused,
                                    // we attempt to bind it to the button. If this is successful
                                    // we update the TextField to reflect the new binding.
                                    if (keybinding.updateKey(button, e.getKeyCode()))
                                        setText(KeyEvent.getKeyText(e.getKeyCode()));
                                }

                                @Override
                                public void keyReleased(KeyEvent e)
                                {
                                    e.consume();
                                }
                            });
                        }
                    });
                }
            });
        }
    }
}
