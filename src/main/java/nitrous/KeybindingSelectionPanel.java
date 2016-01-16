package nitrous;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class KeybindingSelectionPanel extends JPanel
{
    public KeybindingSelectionPanel(int[] keystrokes)
    {
        String[] ids = {"A", "B", "Right", "Left", "Up", "Down", "Start", "Select"};

        setLayout(new GridLayout(0, 2, 10, 0));

        for (int i = 0; i < ids.length; i++)
        {
            int button = i;
            String id = ids[i];
            int keystroke = keystrokes[i];
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

                                    // disallow double bindings
                                    int code = e.getKeyCode();
                                    for (int binding : keystrokes)
                                        if (binding == code)
                                            return;

                                    keystrokes[button] = code;
                                    setText(KeyEvent.getKeyText(code));
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
                int[] keystrokes = {
                        KeyEvent.VK_A, KeyEvent.VK_B,
                        KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
                        KeyEvent.VK_X, KeyEvent.VK_Y
                };
                add(new KeybindingSelectionPanel(keystrokes));
                pack();
                setLocationRelativeTo(null);
                setResizable(false);
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                setVisible(true);
            }
        };
    }
}
