package nitrous.ui;

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
     * Creates a new KeybindingSelectionPanel.
     *
     * @param keybinding the underlying keybinding storage
     */
    public KeybindingSelectionPanel(Keybinding keybinding)
    {
        // 2x4 matrix of buttons
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
                    // The button name label.
                    add(new JLabel(id)
                    {
                        {
                            // Should have a bit of fixed padding between label and textbox.
                            setPreferredSize(new Dimension(40, getPreferredSize().height));
                        }
                    });

                    // The binding text field, big enough for 10 characters.
                    add(new JTextField(10)
                    {
                        {
                            // Show the name of the current bound key.
                            setForeground(getDisabledTextColor());
                            setText(KeyEvent.getKeyText(keystroke));

                            // Event handler to update binding: it consumes absolutely all events, and updates
                            // the text field itself.
                            addKeyListener(new KeyAdapter()
                            {
                                /**
                                 * {@inheritDoc}
                                 */
                                @Override
                                public void keyTyped(KeyEvent e)
                                {
                                    e.consume();
                                }//end keyTyped

                                /**
                                 * {@inheritDoc}
                                 */
                                @Override
                                public void keyPressed(KeyEvent e)
                                {
                                    e.consume();

                                    // When a key is pressed while the TextField is focused,
                                    // we attempt to bind it to the button. If this is successful
                                    // we update the TextField to reflect the new binding.
                                    if (keybinding.updateKey(button, e.getKeyCode()))
                                        setText(KeyEvent.getKeyText(e.getKeyCode()));
                                }//end keyPressed

                                /**
                                 * {@inheritDoc}
                                 */
                                @Override
                                public void keyReleased(KeyEvent e)
                                {
                                    e.consume();
                                }//end keyReleased
                            });
                        }
                    });
                }
            });
        }//end for
    }//end KeybindingSelectionPanel(keybinding)
}//end class KeybindingSelectionPanel
