package nitrous.sound;

import nitrous.Emulator;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
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

    private static DataOutputStream out;

    static
    {
        try
        {
            out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File("wave.bin"))));
        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
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
//            buffer[index] += channel1.render();
            buffer[index] += channel2.render();
//            buffer[index] += channel4.render();


//            System.out.print(buffer[index] + " ");

//            if(buffer[index] == 0) System.err.println("uhoh");

//            buffer[index] += channel3.render();
//            System.out.print(buffer[index] + " ");

            if (usedSamples >= buffer.length)
            {
                if (core.mmu.ICARE)
                {
                    System.out.println(Arrays.toString(buffer));
                    core.mmu.ICARE = false;
                }
                int written = 0;
                for (int i = 0; i < buffer.length; i++)
                    try
                    {
                        out.writeByte(buffer[i]);
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                while ((written += sdl.write(buffer, written, buffer.length)) != buffer.length)
                    System.out.printf("Added some more data");
                usedSamples = 0;
            }
        }
    }

    //public void play() {
    //sdl.write(buffer, 0, usedSamples);
    //usedSamples = 0;
    //}

//    public void render(int cycles) {
//        if (sdl == null)
//            return;
//
//        int samples = (int) (cycles * sampleClocks);
//
//        System.arraycopy(BLANK, 0, buffer, 0, samples);
//        /*channel1.render(buffer, 0, samples);
//        channel2.render(buffer, 0, samples);
//        channel3.render(buffer, 0, samples);*/
//
//        /*byte[] temp = new byte[samples];
//        System.arraycopy(buffer, 0, temp, 0, samples);
//        System.out.println(Arrays.toString(temp));*/
//
//        sdl.write(buffer, 0, samples);
//    }
}
