package nitrous;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;

public class Keybinding
{
    public static final int KEY_A = 0;
    public static final int KEY_B = 1;
    public static final int KEY_RIGHT = 2;
    public static final int KEY_LEFT = 3;
    public static final int KEY_UP = 4;
    public static final int KEY_DOWN = 5;
    public static final int KEY_START = 6;
    public static final int KEY_SELECT = 7;

    private static final int[] defaultKeys = {
            KeyEvent.VK_A, KeyEvent.VK_B,
            KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT,
            KeyEvent.VK_UP, KeyEvent.VK_DOWN,
            KeyEvent.VK_X, KeyEvent.VK_Y
    };

    private final Preferences node;
    private int[] keystrokes = new int[8];
    private String[] keyNames = {"A", "B", "Right", "Left", "Up", "Down", "Start", "Select"};
    private HashMap<Integer, Integer> keyMap = new HashMap<>();

    public Keybinding(Preferences node)
    {
        this.node = node;

        for (int i = 0; i < keystrokes.length; ++i)
        {
            keystrokes[i] = node.getInt("key" + i, defaultKeys[i]);
            keyMap.put(keystrokes[i], i);
        }
    }

    public int getKeyCount()
    {
        return keystrokes.length;
    }

    public String getKeyName(int button)
    {
        return keyNames[button];
    }

    public List<String> getKeyNames()
    {
        return Collections.unmodifiableList(Arrays.asList(keyNames));
    }

    public boolean isKeyUsed(int keyCode)
    {
        for (int keystroke : keystrokes)
            if (keystroke == keyCode)
                return true;
        return false;
    }

    public boolean updateKey(int button, int keyCode)
    {
        if (isKeyUsed(keyCode))
            return false;

        keyMap.remove(keystrokes[button]);
        keystrokes[button] = keyCode;
        keyMap.put(keyCode, button);

        node.putInt("key" + button, keyCode);
        return true;
    }

    public int getKey(int keyCode)
    {
        Integer value = keyMap.get(keyCode);
        if (value == null)
            return -1;
        else
            return value;
    }

    public int getKeystroke(int button)
    {
        return keystrokes[button];
    }
}
