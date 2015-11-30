package nitrous.sound;

import com.sun.media.sound.WaveFileWriter;
import nitrous.Emulator;

import javax.sound.sampled.*;
import java.io.*;
import java.util.Arrays;

import static nitrous.R.*;

public class SoundManager
{
    private final Emulator core;
    private byte[] buffer;
    private SourceDataLine sdl;

    public SquareWaveChannel channel1;
    public SquareWaveChannel channel2;
    public RawWaveChannel channel3;
    public NoiseChannel channel4;

    public SoundManager(Emulator core)
    {
        this.core = core;

        channel1 = new SquareWaveChannel(core, R_NR11, true);
        channel2 = new SquareWaveChannel(core, R_NR21, false);
        channel3 = new RawWaveChannel(core);
        channel4 = new NoiseChannel(core);

        buffer = new byte[480 * 4];
        System.out.println(buffer.length);
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

    private double sampleClocks;

    public double getSampleClocks()
    {
        return sampleClocks;
    }

    public void updateClockSpeed(int clockSpeed)
    {
        sampleClocks = clockSpeed / SoundChannel.AUDIO_FORMAT.getSampleRate();
    }

    private int usedSamples = 0;
    private double clockTicks = 0;

    private static ByteArrayOutputStream out = null;

    static
    {
        if (System.getProperty("nox.soundFile") != null) {
            out = new ByteArrayOutputStream();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.println("Saving: " + System.getProperty("nox.soundFile"));
                    out.close();
                    new WaveFileWriter().write(new AudioInputStream(new ByteArrayInputStream(out.toByteArray()), SoundChannel.AUDIO_FORMAT, out.size()),
                            AudioFileFormat.Type.WAVE, new FileOutputStream(System.getProperty("nox.soundFile")));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));
        }
    }

    public void tick(long delta)
    {
        clockTicks += delta;

        while (clockTicks >= sampleClocks)
        {
            clockTicks -= sampleClocks;

            int index = usedSamples++;
            buffer[index] = 0;
            buffer[index] += channel1.render();
            buffer[index] += channel2.render();
            buffer[index] += channel3.render();
            buffer[index] += channel4.render();

            if (usedSamples >= buffer.length)
            {
                if (out != null)
                    out.write(buffer, 0, buffer.length);

                int written = 0;
                while ((written += sdl.write(buffer, written, buffer.length)) != buffer.length);
                usedSamples = 0;
            }
        }
    }
}
