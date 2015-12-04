package nitrous.sound;

import com.sun.media.sound.WaveFileWriter;
import nitrous.Emulator;
import nitrous.R;

import javax.sound.sampled.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

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

    private static OutputStream out = null;
    private static Thread wavWriter;
    private static long written;

    public int volume = 100;

    static
    {
        if (System.getProperty("nox.soundFile") != null)
        {
            PipedInputStream in = new PipedInputStream();
            try
            {
                out = new BufferedOutputStream(new PipedOutputStream(in));
            } catch (IOException e)
            {
                e.printStackTrace();
            }

            File wav = new File(System.getProperty("nox.soundFile"));

            wavWriter = new Thread("WAV-Writer-Thread")
            {
                @Override
                public void run()
                {
                    try
                    {
                        // Internally, line 122 of WaveFileWriter casts the long length into an int, causing an overflow
                        // with some values
                        // Particularly, the operation it does is
                        // (int) stream.getFrameLength() * format.getFrameSize() + WaveFileFormat.getHeaderSize(...)
                        // getHeaderSize returns at most 46, so we can calculate the maximum length we can have without
                        // overflowing int - if we do, the entire application hangs for some obscure reason
                        int length = Integer.MAX_VALUE / SoundChannel.AUDIO_FORMAT.getFrameSize() - 46;
                        new WaveFileWriter().write(new AudioInputStream(in, SoundChannel.AUDIO_FORMAT, length),
                                AudioFileFormat.Type.WAVE, new FileOutputStream(wav));
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            };
            wavWriter.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try
                {
                    System.out.println("Saving: " + wav);
                    out.close();
                    RandomAccessFile raf = new RandomAccessFile(wav, "rw");
                    raf.seek(40);
                    raf.write(new byte[]{
                            (byte) (written & 0xff),
                            (byte) ((written >> 8) & 0xff),
                            (byte) ((written >> 16) & 0xff),
                            (byte) ((written >> 24) & 0xff)
                    });
                    raf.close();
//                    }
                } catch (IOException e)
                {
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
            int left = index * 4;
            int right = index * 4 + 2;

            buffer[left] = 0;
            buffer[right] = 0;

            int a = channel1.render();
            int b = channel2.render();
            int c = channel3.render();
            int d = channel4.render();

            int flags = core.mmu.registers[R.R_NR51];

            int dataLeft = 0;
            int dataRight = 0;

            if ((flags & 0x80) != 0) dataLeft += d;
            if ((flags & 0x40) != 0) dataLeft += c;
            if ((flags & 0x20) != 0) dataLeft += b;
            if ((flags & 0x10) != 0) dataLeft += a;

            if ((flags & 0x08) != 0) dataRight += d;
            if ((flags & 0x04) != 0) dataRight += c;
            if ((flags & 0x02) != 0) dataRight += b;
            if ((flags & 0x01) != 0) dataRight += a;

            dataLeft = (dataLeft << 8) * volume / 100;
            dataRight = (dataRight << 8) * volume / 100;

            buffer[left] = (byte) (dataLeft >> 8);
            buffer[left + 1] = (byte) (dataLeft & 0xFF);
            buffer[right] = (byte) (dataRight >> 8);
            buffer[right + 1] = (byte) (dataRight & 0xFF);

            //System.out.println(Integer.toBinaryString(flags & 0xff));

            if (usedSamples >= buffer.length / 4)
            {
                if (out != null)
                    try
                    {
                        out.write(buffer, 0, buffer.length);
                        written += buffer.length;
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }

                int written = 0;
                while ((written += sdl.write(buffer, written, buffer.length)) != buffer.length) ;
                usedSamples = 0;
            }
        }
    }
}
