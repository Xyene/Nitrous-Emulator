package nitrous;

import nitrous.lcd.Interpolator;

import java.awt.*;
import java.util.HashSet;
import java.util.prefs.Preferences;

public class Settings
{
    private static Preferences storage = Preferences.userNodeForPackage(Settings.class);

    private static <T extends Enum<T>> T getEnum(String name, T def)
    {
        T[] values = def.getDeclaringClass().getEnumConstants();
        int index = storage.getInt(name, def.ordinal());
        if (index < 0 || index >= values.length)
            index = def.ordinal();
        return values[index];
    }

    private static boolean channel1On;
    private static boolean channel2On;
    private static boolean channel3On;
    private static boolean channel4On;
    private static boolean muted;
    private static int volume;
    private static int magnification;
    private static boolean fullScreen;
    private static Interpolator interpolator;

    public interface SpeedListener
    {
        void updateSpeed(EmulateSpeed speed);
    }

    private static EmulateSpeed speed;
    private static HashSet<SpeedListener> speedListeners = new HashSet<>();

    public static final Keybinding keys = new Keybinding(storage.node("keys"));

    static
    {
        channel1On = storage.getBoolean("channel1", true);
        channel2On = storage.getBoolean("channel2", true);
        channel3On = storage.getBoolean("channel3", true);
        channel4On = storage.getBoolean("channel4", true);
        muted = storage.getBoolean("muted", false);
        volume = storage.getInt("volume", 100);

        speed = getEnum("speed", EmulateSpeed.SINGLE);
        interpolator = getEnum("interpolator", Interpolator.NEAREST);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxMag = Math.min(screen.width / 160, screen.height / 144);
        magnification = Math.max(1, Math.min(maxMag, storage.getInt("magnification", 2)));
        fullScreen = storage.getBoolean("fullscreen", false);
    }

    public static boolean isChannel1On()
    {
        return channel1On;
    }

    public static void setChannel1On(boolean channel1On)
    {
        Settings.channel1On = channel1On;
        storage.putBoolean("channel1", channel1On);
    }

    public static boolean isChannel2On()
    {
        return channel2On;
    }

    public static void setChannel2On(boolean channel2On)
    {
        Settings.channel2On = channel2On;
        storage.putBoolean("channel2", channel2On);
    }

    public static boolean isChannel3On()
    {
        return channel3On;
    }

    public static void setChannel3On(boolean channel3On)
    {
        Settings.channel3On = channel3On;
        storage.putBoolean("channel3", channel3On);
    }

    public static boolean isChannel4On()
    {
        return channel4On;
    }

    public static void setChannel4On(boolean channel4On)
    {
        Settings.channel4On = channel4On;
        storage.putBoolean("channel4", channel4On);
    }

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
        }
    }

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
        }
    }

    public static boolean isMuted()
    {
        return muted;
    }

    public static void setMuted(boolean muted)
    {
        Settings.muted = muted;
        storage.putBoolean("muted", muted);
    }

    public static int getVolume()
    {
        return volume;
    }

    public static void setVolume(int volume)
    {
        Settings.volume = volume;
    }

    public static void saveVolume()
    {
        storage.putInt("volume", volume);
    }

    public static void addSpeedListener(SpeedListener listener)
    {
        speedListeners.add(listener);
    }

    public static void removeSpeedListener(SpeedListener listener)
    {
        speedListeners.remove(listener);
    }

    public static EmulateSpeed getSpeed()
    {
        return speed;
    }

    public static void setSpeed(EmulateSpeed speed)
    {
        Settings.speed = speed;
        storage.putInt("speed", speed.ordinal());

        for (SpeedListener listener : speedListeners)
            listener.updateSpeed(speed);
    }

    public static Interpolator getInterpolator()
    {
        return interpolator;
    }

    public static void setInterpolator(Interpolator interpolator)
    {
        Settings.interpolator = interpolator;
        storage.putInt("interpolator", interpolator.ordinal());
    }

    public static int getMagnification()
    {
        return magnification;
    }

    public static void setMagnification(int magnification)
    {
        Settings.magnification = magnification;
        storage.putInt("magnification", magnification);
    }

    public static boolean isFullScreen()
    {
        return fullScreen;
    }

    public static void setFullScreen(boolean fullScreen)
    {
        Settings.fullScreen = fullScreen;
        storage.putBoolean("fullscreen", fullScreen);
    }
}
