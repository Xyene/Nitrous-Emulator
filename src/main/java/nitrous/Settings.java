package nitrous;

import nitrous.cpu.R;
import nitrous.lcd.Interpolator;
import nitrous.ui.Keybinding;

import java.awt.*;
import java.util.HashSet;
import java.util.prefs.Preferences;

/**
 * Settings manager.
 *
 * @author Quantum
 */
public class Settings
{
    /**
     * Create the settings storage.
     */
    private static final Preferences storage = Preferences.userNodeForPackage(Settings.class);

    /**
     * The enabled state of each of the four sound output channels.
     */
    private static boolean channel1On, channel2On, channel3On, channel4On;

    /**
     * Whether the sound is muted or not.
     */
    private static boolean muted;

    /**
     * The volume, from 0 to 100 inclusive.
     */
    private static int volume;

    /**
     * The game magnification settings.
     */
    private static int magnification;

    /**
     * The full screen setting.
     */
    private static boolean fullScreen;

    /**
     * The interpolator used to scale the graphics output.
     */
    private static Interpolator interpolator;

    /**
     * Interface for a listener to activate on CPU clock speed change.
     *
     * #action our own action listener for emulation speed change
     */
    public interface SpeedListener
    {
        /**
         * Method called on clock speed change.
         *
         * @param speed the new speed
         */
        void updateSpeed(EmulateSpeed speed);
    }

    /**
     * The current execution speed.
     */
    private static EmulateSpeed speed;

    /**
     * Registered {@link SpeedListener} instances.
     *
     * #action our own action listener for emulation speed change
     */
    private static final HashSet<SpeedListener> speedListeners = new HashSet<>();

    /**
     * The keybinding storage, managed by a {@link Keybinding} instance.
     */
    public static final Keybinding keys = new Keybinding(storage.node("keys"));

    /**
     * The most frequently used ROMs, managed by a {@link ROMFrequencyManager} instance.
     */
    public static final ROMFrequencyManager rom = new ROMFrequencyManager(storage.node("rom"));

    static
    {
        // Load settings from storage.
        channel1On = storage.getBoolean("channel1", true);
        channel2On = storage.getBoolean("channel2", true);
        channel3On = storage.getBoolean("channel3", true);
        channel4On = storage.getBoolean("channel4", true);
        muted = storage.getBoolean("muted", false);
        volume = storage.getInt("volume", 100);

        speed = getEnum("speed", EmulateSpeed.SINGLE);
        interpolator = getEnum("interpolator", Interpolator.NEAREST);

        // Find the maximum possible magnification.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxMag = Math.min(screen.width / R.W, screen.height / R.H);

        // Cap the stored magnification with maximum possible.
        magnification = Math.max(1, Math.min(maxMag, storage.getInt("magnification", 2)));

        // Load full screen setting.
        fullScreen = storage.getBoolean("fullscreen", false);
    }//end static

    /**
     * Utility function to get enum values form settings storage with fallback.
     *
     * #static
     *
     * @param name the key name that stores the enum
     * @param def  the fallback enum value
     * @param <T>  the enum type
     * @return the stored enum value, or {@literal def} if unavailable
     */
    private static <T extends Enum<T>> T getEnum(String name, T def)
    {
        T[] values = def.getDeclaringClass().getEnumConstants();
        int index = storage.getInt(name, def.ordinal());
        if (index < 0 || index >= values.length)
            index = def.ordinal();
        return values[index];
    }//end getEnum

    /**
     * Checks if channel 1 is enabled.
     *
     * @return {@literal true} if channel 1 is enabled
     */
    public static boolean isChannel1On()
    {
        return channel1On;
    }//end isChannel1On

    /**
     * Alters the channel 1 state.
     *
     * @param channel1On the new state of channel 1
     */
    public static void setChannel1On(boolean channel1On)
    {
        Settings.channel1On = channel1On;
        storage.putBoolean("channel1", channel1On);
    }//end setChannel1On

    /**
     * Checks if channel 2 is enabled.
     *
     * @return {@literal true} if channel 2 is enabled
     */
    public static boolean isChannel2On()
    {
        return channel2On;
    }//end isChannel2On

    /**
     * Alters the channel 2 state.
     *
     * @param channel2On the new state of channel 2
     */
    public static void setChannel2On(boolean channel2On)
    {
        Settings.channel2On = channel2On;
        storage.putBoolean("channel2", channel2On);
    }//end setChannel2On

    /**
     * Checks if channel 3 is enabled.
     *
     * @return {@literal true} if channel 3 is enabled
     */
    public static boolean isChannel3On()
    {
        return channel3On;
    }//end isChannel3On

    /**
     * Alters the channel 3 state.
     *
     * @param channel3On the new state of channel 3
     */
    public static void setChannel3On(boolean channel3On)
    {
        Settings.channel3On = channel3On;
        storage.putBoolean("channel3", channel3On);
    }//end setChannel3On

    /**
     * Checks if channel 4 is enabled.
     *
     * @return {@literal true} if channel 4 is enabled
     */
    public static boolean isChannel4On()
    {
        return channel4On;
    }//end isChannel4On

    /**
     * Alters the channel 4 state.
     *
     * @param channel4On the new state of channel 4
     */
    public static void setChannel4On(boolean channel4On)
    {
        Settings.channel4On = channel4On;
        storage.putBoolean("channel4", channel4On);
    }//end setChannel4On

    /**
     * Check the enabled state of a channel.
     *
     * @param channel the channel to check
     * @return {@literal true} if the said channel is enabled
     */
    public static boolean isChannelOn(int channel)
    {
        switch (channel)
        {
            case 1:
                return channel1On;
            case 2:
                return channel2On;
            case 3:
                return channel3On;
            case 4:
                return channel4On;
            default:
                throw new IllegalArgumentException();
        }//end switch
    }//end isChannelOn

    /**
     * Alter the enabled state of a channel.
     *
     * @param channel the channel to alter
     * @param on      the new state of the said channel
     */
    public static void setChannelOn(int channel, boolean on)
    {
        switch (channel)
        {
            case 1:
                setChannel1On(on);
                break;
            case 2:
                setChannel2On(on);
                break;
            case 3:
                setChannel3On(on);
                break;
            case 4:
                setChannel4On(on);
                break;
            default:
                throw new IllegalArgumentException();
        }//end switch
    }//end setChannelOn

    /**
     * Checks the muted state.
     *
     * @return {@literal true} is sound is muted
     */
    public static boolean isMuted()
    {
        return muted;
    }//end isMuted

    /**
     * Alters the muted state.
     *
     * @param muted the new mute state
     */
    public static void setMuted(boolean muted)
    {
        Settings.muted = muted;
        storage.putBoolean("muted", muted);
    }//end setMuted

    /**
     * Retrieves the volume settings.
     *
     * @return the current volume setting, from 0 to 100 inclusive
     */
    public static int getVolume()
    {
        return volume;
    }//end getVolume

    /**
     * Alters the volume settings.
     *
     * @param volume the new volume setting, from 0 to 100 inclusive
     */
    public static void setVolume(int volume)
    {
        Settings.volume = volume;
    }//end setVolume

    /**
     * Save the altered volume setting.
     */
    public static void saveVolume()
    {
        storage.putInt("volume", volume);
    }//end saveVolume

    /**
     * Adds a listener to be notified of changes in emulation speed.
     *
     * #action our own action listener for emulation speed change
     *
     * @param listener the listener to add
     */
    public static void addSpeedListener(SpeedListener listener)
    {
        speedListeners.add(listener);
    }//end addSpeedListener

    /**
     * Removes a listener to changes in emulation speed.
     *
     * #action our own action listener for emulation speed change
     *
     * @param listener the listener to remove
     */
    public static void removeSpeedListener(SpeedListener listener)
    {
        speedListeners.remove(listener);
    }//end removeSpeedListener

    /**
     * Retrieve the current emulation speed.
     *
     * @return the current emulation speed
     */
    public static EmulateSpeed getSpeed()
    {
        return speed;
    }//end getSpeed

    /**
     * Alters the current emulation speed.
     *
     * #cheat alters game speed, in case the game was too fast or too slow (accessed through right click menu)
     *
     * @param speed the new emulation speed
     */
    public static void setSpeed(EmulateSpeed speed)
    {
        Settings.speed = speed;
        storage.putInt("speed", speed.ordinal());

        // Signal all SpeedListeners
        for (SpeedListener listener : speedListeners)
            listener.updateSpeed(speed);
    }//end setSpeed

    /**
     * Retrieves the current interpolator setting.
     *
     * @return the {@link Interpolator} enumeration that represents the current interpolator
     */
    public static Interpolator getInterpolator()
    {
        return interpolator;
    }//end getInterpolator

    /**
     * Alters the current interpolator setting.
     *
     * @param interpolator the {@link Interpolator} enumeration that represents the new interpolator
     */
    public static void setInterpolator(Interpolator interpolator)
    {
        Settings.interpolator = interpolator;
        storage.putInt("interpolator", interpolator.ordinal());
    }//end setInterpolator

    /**
     * Retrieves the current magnification setting.
     *
     * @return the current magnification setting
     */
    public static int getMagnification()
    {
        return magnification;
    }//end getMagnification

    /**
     * Alters the magnification setting.
     *
     * @param magnification the new magnification setting
     */
    public static void setMagnification(int magnification)
    {
        Settings.magnification = magnification;
        storage.putInt("magnification", magnification);
    }//end setMagnification

    /**
     * Retrieves the full screen settings.
     *
     * @return {@literal true} if full screen is enabled
     */
    public static boolean isFullScreen()
    {
        return fullScreen;
    }//end isFullScreen

    /**
     * Alters the full screen settings.
     *
     * @param fullScreen the new full screen settings
     */
    public static void setFullScreen(boolean fullScreen)
    {
        Settings.fullScreen = fullScreen;
        storage.putBoolean("fullscreen", fullScreen);
    }//end setFullScreen
}//end class Settings
