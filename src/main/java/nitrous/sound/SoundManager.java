package nitrous.sound;

import nitrous.Emulator;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class SoundManager
{
    private final static byte[] BLANK = new byte[48000];

    private final Emulator core;
    private byte[] buffer;
    private SourceDataLine sdl;

    public SquareWaveChannel channel1;
    public SquareWaveChannel channel2;

    public SoundManager(Emulator core)
    {
        this.core = core;
        channel1 = new SquareWaveChannel(core, 0x11, true);
        channel2 = new SquareWaveChannel(core, 0x16, false);

        buffer = new byte[BLANK.length];
        try
        {
            sdl = AudioSystem.getSourceDataLine(SoundChannel.AUDIO_FORMAT);
            sdl.open(SoundChannel.AUDIO_FORMAT);
            sdl.start();
        } catch (LineUnavailableException e)
        {
            e.printStackTrace();
            sdl = null;
        }
    }

    public void render(int cycles, int clockSpeed) {
        if (sdl == null)
            return;

        int samples = (int) (cycles * SoundChannel.AUDIO_FORMAT.getSampleRate() / clockSpeed);

        System.arraycopy(BLANK, 0, buffer, 0, samples);
        channel1.render(buffer, 0, samples);
        channel2.render(buffer, 0, samples);

        sdl.write(buffer, 0, samples);
    }
}
