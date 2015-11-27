package nitrous.sound;

import nitrous.Emulator;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class SoundManager
{
    public SquareWaveChannel channel1;
    public SquareWaveChannel channel2;
    private SourceDataLine sdl;
    private final byte[] buffer;
    private final static byte[] BLANK = new byte[48000];

    public SoundManager(Emulator core)
    {
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

    public void render(int cycles) {
        if (sdl == null)
            return;

        int samples = (int) (cycles / SoundChannel.samplePeriod);

        System.arraycopy(BLANK, 0, buffer, 0, samples);
        channel1.render(buffer, 0, samples);
        channel2.render(buffer, 0, samples);

        sdl.write(buffer, 0, buffer.length);
    }
}
