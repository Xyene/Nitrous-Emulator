package nitrous.ui;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.prefs.Preferences;

/**
 * Keybinding manager.
 *
 * @author Quantum
 */
public class Keybinding
{
    /**
     * Indices for each button in the array.
     */
    public static final int KEY_A = 0;
    public static final int KEY_B = 1;
    public static final int KEY_RIGHT = 2;
    public static final int KEY_LEFT = 3;
    public static final int KEY_UP = 4;
    public static final int KEY_DOWN = 5;
    public static final int KEY_START = 6;
    public static final int KEY_SELECT = 7;

    /**
     * The default key settings.
     */
    public static final int[] DEFAULT_KEY_BINDINGS = {
            KeyEvent.VK_A, KeyEvent.VK_B,
            KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT,
            KeyEvent.VK_UP, KeyEvent.VK_DOWN,
            KeyEvent.VK_X, KeyEvent.VK_Y
    };

    /**
     * The storage node for keybinding settings.
     */
    private final Preferences node;

    /**
     * The current keybinding settings, as an array indexed by constants in {@link Keybinding}.
     */
    private final int[] keystrokes = new int[8];

    /**
     * The names of each button, in the same order as {@link #keystrokes}.
     */
    private final String[] keyNames = {"A", "B", "Right", "Left", "Up", "Down", "Start", "Select"};

    /**
     * The mapping of key codes to the button id.
     */
    private final HashMap<Integer, Integer> keyMap = new HashMap<>();

    /**
     * Creates a keybinding manager.
     *
     * @param node the {@link Preferences} node to store the data in
     */
    public Keybinding(Preferences node)
    {
        this.node = node;

        // Gets the keybinding settings from the store, with fallback to DEFAULT_KEY_BINDINGS
        for (int i = 0; i < keystrokes.length; ++i)
        {
            keystrokes[i] = node.getInt("key" + i, DEFAULT_KEY_BINDINGS[i]);

            // Populate the reverse mapping.
            keyMap.put(keystrokes[i], i);
        }
    }

    /**
     * Gets the number of keys.
     *
     * @return the number of keys on the Gameboy, i.e. 8
     */
    public int getKeyCount()
    {
        return keystrokes.length;
    }

    /**
     * Gets the name of the button from the button id.
     *
     * @param button the button id.
     * @return the button name
     */
    public String getKeyName(int button)
    {
        return keyNames[button];
    }

    /**
     * Checks if the key code is already bound to a button.
     *
     * @param keyCode the key code to check
     * @return {@literal} true if it is
     */
    public boolean isKeyUsed(int keyCode)
    {
        // Linear search to see if the key is used.
        for (int keystroke : keystrokes)
            if (keystroke == keyCode)
                return true;
        return false;
    }

    /**
     * Update the keybinding of a button.
     *
     * @param button  the button id of the button whose binding is to be changed
     * @param keyCode the key code of the key to be bound to the button
     * @return {@literal true} if the key was able to be bound to this button
     */
    public boolean updateKey(int button, int keyCode)
    {
        // Fail if the key is used.
        if (isKeyUsed(keyCode))
            return false;

        // Update keystrokes and the reverse mapping.
        keyMap.remove(keystrokes[button]);
        keystrokes[button] = keyCode;
        keyMap.put(keyCode, button);

        // Alters the storage.
        node.putInt("key" + button, keyCode);
        return true;
    }

    /**
     * Get the button bound to the key code.
     *
     * @param keyCode the key code to check
     * @return the id of the button bound, or -1 if none
     */
    public int getKey(int keyCode)
    {
        Integer value = keyMap.get(keyCode);
        if (value == null)
            return -1;
        else
            return value;
    }

    /**
     * Get the key code of the button.
     *
     * @param button the button id to check
     * @return the key code of the button
     */
    public int getKeystroke(int button)
    {
        return keystrokes[button];
    }
}
