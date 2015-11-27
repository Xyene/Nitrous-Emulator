package nitrous.sound;

import nitrous.Emulator;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;

public class SoundManager
{
    private final static byte[] BLANK = new byte[48000];

    private final Emulator core;
    private byte[] buffer;
    private SourceDataLine sdl;

    public SquareWaveChannel channel1;
    public SquareWaveChannel channel2;
    public RawWaveChannel channel3;

    public SoundManager(Emulator core)
    {
        this.core = core;
        channel1 = new SquareWaveChannel(core, 0x11, true);
        channel2 = new SquareWaveChannel(core, 0x16, false);
        channel3 = new RawWaveChannel(core);

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
        channel3.render(buffer, 0, samples);

        /*byte[] temp = new byte[samples];
        System.arraycopy(buffer, 0, temp, 0, samples);
        System.out.println(Arrays.toString(temp));*/

        sdl.write(buffer, 0, samples);
    }
}
