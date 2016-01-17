package nitrous.sound;

import com.sun.media.sound.WaveFileWriter;
import nitrous.cpu.Emulator;
import nitrous.R;
import nitrous.Settings;

import javax.sound.sampled.*;
import java.io.*;

import static nitrous.R.*;

/**
 * Sound manager. Manages all four sound channels:
 *
 * <ul>
 *     <li>{@link SquareWaveChannel} - channel 1 and 2</li>
 *     <li>{@link RawWaveChannel} - channel 3</li>
 *     <li>{@link NoiseChannel} - channel 4</li>
 * </ul>
 */
public class SoundManager
{
    /**
     * Link to {@link Emulator} instance.
     */
    private final Emulator core;

    /**
     * Sound buffer.
     */
    private byte[] buffer;

    /**
     * {@link SourceDataLine} for output.
     */
    private SourceDataLine sdl;

    /**
     * {@link SquareWaveChannel} instance for channel 1.
     */
    public SquareWaveChannel channel1;

    /**
     * {@link SquareWaveChannel} instance for channel 2.
     */
    public SquareWaveChannel channel2;

    /**
     * {@link RawWaveChannel} instance for channel 3.
     */
    public RawWaveChannel channel3;

    /**
     * {@link NoiseChannel} instance for channel 4.
     */
    public NoiseChannel channel4;

    /**
     * Amount of CPU cycles per sample of audio.
     */
    private double sampleClocks;

    /**
     * Current volume, from 0 to 100.
     */
    public int volume = 100;

    /**
     * Samples used in buffer.
     */
    private int usedSamples = 0;

    /**
     * Amount of CPU cycles since the last sample.
     */
    private double clockTicks = 0;

    /**
     * {@link OutputStream} for sound output to file.
     *
     * {@literal null} means no output.
     */
    private static OutputStream out = null;

    /**
     * A {@link Thread} to write live sound output to file.
     */
    private static Thread wavWriter;

    /**
     * The amount of samples written to the file.
     */
    private static long written;

    /**
     * {@link RandomAccessFile} version of the file,
     * so that we can update the file length.
     */
    public static RandomAccessFile soundFile;

    /**
     * Constructs a {@link SoundManager} for {@link Emulator} instance.
     *
     * @param core the emulator instance
     */
    public SoundManager(Emulator core)
    {
        this.core = core;

        // Create channels.
        channel1 = new SquareWaveChannel(core, R_NR11, true);
        channel2 = new SquareWaveChannel(core, R_NR21, false);
        channel3 = new RawWaveChannel(core);
        channel4 = new NoiseChannel(core);

        // Create buffer.
        buffer = new byte[480];

        // Create and start SourceDataLine.
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

    /**
     * Gets the amount of CPU cycles per sample of audio.
     *
     * @return the amount of CPU cycles per sample of audio.
     */
    public double getSampleClocks()
    {
        return sampleClocks;
    }


    /**
     * Sets the amount of CPU cycles per sample of audio.
     *
     * @param clockSpeed the clock speed of the CPU
     */
    public void updateClockSpeed(int clockSpeed)
    {
        sampleClocks = clockSpeed / SoundChannel.AUDIO_FORMAT.getSampleRate();
    }

    /**
     * Updates the length stored in the sound output file.
     *
     * @throws IOException
     */
    public static void updateSoundFileLength() throws IOException
    {
        soundFile.seek(4);
        long x = written + 36;
        soundFile.write(new byte[]{
                (byte) (x & 0xff),
                (byte) ((x >> 8) & 0xff),
                (byte) ((x >> 16) & 0xff),
                (byte) ((x >> 24) & 0xff)
        });
        soundFile.seek(40);
        soundFile.write(new byte[]{
                (byte) (written & 0xff),
                (byte) ((written >> 8) & 0xff),
                (byte) ((written >> 16) & 0xff),
                (byte) ((written >> 24) & 0xff)
        });
    }

    static
    {
        // The system property, nox.soundFile, if declared, sets the wave output file.
        if (System.getProperty("nox.soundFile") != null)
        {
            // Using pipes to handle sound output.
            PipedInputStream in = new PipedInputStream();
            try
            {
                out = new BufferedOutputStream(new PipedOutputStream(in));
            } catch (IOException e)
            {
                e.printStackTrace();
            }

            // Create a File object to represent the output file.
            File wav = new File(System.getProperty("nox.soundFile"));

            // Define and start the wave writer thread.
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
                        // This will only ever exit with a "Pipe closed" exception
                    }
                }
            };
            wavWriter.start();

            // Open output file for random access.
            try
            {
                soundFile = new RandomAccessFile(wav, "rwd");
            } catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }

            // Add shutdown hook for final length update.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try
                {
                    System.out.println("Saving: " + wav);
                    out.close();
                    updateSoundFileLength();
                    soundFile.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }));
        }
    }

    /**
     * Called every time when the CPU clock advances to generate new samples, if available.
     *
     * @param delta the amount of CPU clock elapsed
     */
    public void tick(long delta)
    {
        // Increase amount of time since last sample.
        clockTicks += delta;

        // As long as the time exceeds the sample time.
        while (clockTicks >= sampleClocks)
        {
            // Take away the time for one sample.
            // If we missed multiple samples, this will ensure they get rendered.
            clockTicks -= sampleClocks;

            // Increase used sample count, and find the indices for the
            // left and right sound channel output.
            // The output is stereo, and each sample is two bytes.
            int index = usedSamples++;
            int left = index * 4;
            int right = index * 4 + 2;

            // The raw PCM sample for the left and right channels.
            int dataLeft = 0;
            int dataRight = 0;

            // Render the four channels.
            int a = channel1.render();
            if (!Settings.isChannel1On()) a = 0;
            int b = channel2.render();
            if (!Settings.isChannel2On()) b = 0;
            int c = channel3.render();
            if (!Settings.isChannel3On()) c = 0;
            int d = channel4.render();
            if (!Settings.isChannel4On()) d = 0;

            if (!Settings.isMuted())
            {
                // Get the channel mapping flags.
                int flags = core.mmu.registers[R.R_NR51];

                // If the channel is played on the left side, we add it to the sample there.
                if ((flags & 0x80) != 0) dataLeft += d;
                if ((flags & 0x40) != 0) dataLeft += c;
                if ((flags & 0x20) != 0) dataLeft += b;
                if ((flags & 0x10) != 0) dataLeft += a;

                // If the channel is played on the right side, we add it to the sample there.
                if ((flags & 0x08) != 0) dataRight += d;
                if ((flags & 0x04) != 0) dataRight += c;
                if ((flags & 0x02) != 0) dataRight += b;
                if ((flags & 0x01) != 0) dataRight += a;

                // Scale the 8 bit sample to 16-bit, then apply volume scaling.
                dataLeft = (dataLeft << 8) * volume / 100;
                dataRight = (dataRight << 8) * volume / 100;
            }

            // Convert the left and right samples to signed big endian bytes.
            buffer[left] = (byte) (dataLeft >> 8);
            buffer[left + 1] = (byte) (dataLeft & 0xFF);
            buffer[right] = (byte) (dataRight >> 8);
            buffer[right + 1] = (byte) (dataRight & 0xFF);

            // If we used up the buffer:
            if (usedSamples >= buffer.length / 4)
            {
                // Write to file if necessary.
                if (out != null)
                    try
                    {
                        out.write(buffer, 0, buffer.length);
                        updateSoundFileLength();
                        written += buffer.length;
                    } catch (IOException e)
                    {
                        out = null;
                    }

                // Write to sound output.
                int written = 0;
                while ((written += sdl.write(buffer, written, buffer.length)) != buffer.length) ;

                // Reset used samples.
                usedSamples = 0;
            }
        }
    }
}
