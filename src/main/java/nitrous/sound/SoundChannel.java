package nitrous.sound;

import nitrous.Emulator;

import javax.sound.sampled.AudioFormat;

public class SoundChannel
{
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(48000, 8, 1, true, true);
    public static final int cpuFreq = 4194304;
    public static final float samplePeriod = cpuFreq / AUDIO_FORMAT.getSampleRate();

    protected final Emulator core;

    public SoundChannel(Emulator core) {
        this.core = core;
    }
}
