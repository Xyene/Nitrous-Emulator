package nitrous.sound;

import nitrous.Emulator;

import javax.sound.sampled.AudioFormat;

public abstract class SoundChannel
{
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(48000, 8, 1, true, true);

    protected final Emulator core;

    public SoundChannel(Emulator core) {
        this.core = core;
    }
}
