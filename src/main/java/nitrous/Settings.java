package nitrous;

import java.util.HashSet;
import java.util.prefs.Preferences;

public class Settings
{
    private static Preferences storage = Preferences.userNodeForPackage(Settings.class);

    private static boolean channel1On;
    private static boolean channel2On;
    private static boolean channel3On;
    private static boolean channel4On;

    public interface SpeedListener
    {
        void updateSpeed(EmulateSpeed speed);
    }

    private static EmulateSpeed speed;
    private static HashSet<SpeedListener> speedListeners = new HashSet<>();

    static
    {
        channel1On = storage.getBoolean("channel1", true);
        channel2On = storage.getBoolean("channel2", true);
        channel3On = storage.getBoolean("channel3", true);
        channel4On = storage.getBoolean("channel4", true);

        int speedIndex = storage.getInt("speed", EmulateSpeed.SINGLE.ordinal());
        if (speedIndex < 0 || speedIndex >= EmulateSpeed.values().length)
            speedIndex = EmulateSpeed.SINGLE.ordinal();
        speed = EmulateSpeed.values()[speedIndex];
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
        storage.putBoolean("channel2", channel1On);
    }

    public static boolean isChannel3On()
    {
        return channel3On;
    }

    public static void setChannel3On(boolean channel3On)
    {
        Settings.channel3On = channel3On;
        storage.putBoolean("channel3", channel1On);
    }

    public static boolean isChannel4On()
    {
        return channel4On;
    }

    public static void setChannel4On(boolean channel4On)
    {
        Settings.channel4On = channel4On;
        storage.putBoolean("channel4", channel1On);
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
}
