package nitrous;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class KeybindingSelectionPanel extends JPanel
{
    public KeybindingSelectionPanel(Keybinding keybinding)
    {
        setLayout(new GridLayout(0, 2, 10, 0));

        for (int i = 0; i < keybinding.getKeyCount(); i++)
        {
            int button = i;
            String id = keybinding.getKeyName(i);
            int keystroke = keybinding.getKeystroke(i);

            add(new JPanel()
            {
                {
                    add(new JLabel(id)
                    {
                        {
                            setPreferredSize(new Dimension(40, getPreferredSize().height));
                        }
                    });

                    add(new JTextField(10)
                    {
                        {
                            setForeground(getDisabledTextColor());
                            setText(KeyEvent.getKeyText(keystroke));

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

    public static void main(String[] argv) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException
    {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        new JFrame("test")
        {
            {
                add(new KeybindingSelectionPanel(Settings.keys));
                pack();
                setLocationRelativeTo(null);
                setResizable(false);
                setDefaultCloseOperation(EXIT_ON_CLOSE);
                setVisible(true);
            }
        };
    }
}
