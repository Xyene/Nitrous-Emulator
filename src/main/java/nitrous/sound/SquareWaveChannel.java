package nitrous.sound;

import nitrous.Emulator;
import nitrous.R;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;

public class SquareWaveChannel extends SoundChannel
{
    private final int ioStart;
    private final boolean sweep;

    private int gbFreq;
    public int period = -1;
    public int length = 0x400000;
    public boolean useLength = false;
    private long clockStart = 0;
    public int envelopeInitial = 15;
    private boolean envelopeIncrease = true;
    private int envelopeSweep = 0;
    public int duty = 0;

    public SquareWaveChannel(Emulator core, int ioStart, boolean sweep)
    {
        super(core);

        this.ioStart = ioStart;
        this.sweep = sweep;
    }

    public int ___ = 0;

    public void handleUpdateRequest()
    {
        updateRequest = false;
        byte[] registers = core.mmu.registers;

        duty = (registers[ioStart] >> 6) & 0x3;
        length = (64 - (registers[ioStart] & 0x3F)) * 16384;

        int newInitial = (registers[ioStart + 1] >> 4) & 0xF;

        if (envelopeInitial != newInitial)
        {
            envelopeInitial = newInitial;
            currentVolume = -1;
        }

        envelopeIncrease = (registers[ioStart + 1] & 0x8) != 0;
        envelopeSweep = (registers[ioStart + 1] & 0x7) * 65536;

        gbFreq = (registers[ioStart + 2] & 0xFF) |
                ((registers[ioStart + 3] & 0x7) << 8);

        int n = gbFreqToCycles(gbFreq);
        if (period != n)
            clockStart = core.cycle;
        period = n;

        useLength = (registers[ioStart + 3] & 0x40) != 0;
    }

    boolean updateRequest = false;

    public void update()
    {
        updateRequest = true;
        requestedClockStart = core.cycle;
    }

    boolean restartRequest = false;
    long requestedClockStart;

    public void handleRestartRequest()
    {
        clockStart = core.cycle;
        restartRequest = false;
        lastCycle = -1;
    }

    public void restart()
    {
        restartRequest = true;
    }

    int currentVolume = -1;

    int lastCycle = -1;

    @Override
    public int render()
    {
        int delta = (int) (core.cycle - clockStart);
        if (useLength && delta > length)
        {
            return 0;
        }

        if (currentVolume == -1)
            currentVolume = envelopeInitial;

        int amplitude;
        if (envelopeSweep == 0)
            amplitude = currentVolume * 2;
        else
            currentVolume = amplitude = Math.min(15, Math.max(0, envelopeInitial + delta / envelopeSweep * (envelopeIncrease ? 1 : -1))) * 2;

        int cycle = (delta * 8 / period) & 7;
        if (lastCycle == -1)
            lastCycle = cycle;

        lastCycle = cycle;

        if (cycle == 0)
        {
            if (updateRequest)
                handleUpdateRequest();
            if (restartRequest)
                handleRestartRequest();
        }

        /**
         * Duty   Waveform    Ratio  Cycle
         * -------------------------------
         * 0      00000001    12.5%      6
         * 1      10000001    25%        5
         * 2      10000111    50%        3
         * 3      01111110    75%        1
         */
        switch (duty)
        {
            case 0:
                return (byte) (cycle == 7 ? amplitude : -amplitude);
            case 1:
                return (byte) (cycle == 0 || cycle == 7 ? amplitude : -amplitude);
            case 2:
                return (byte) (cycle == 0 || cycle > 4 ? amplitude : -amplitude);
            case 3:
                return (byte) (cycle != 0 && cycle != 7 ? amplitude : -amplitude);
        }
        return -1;
    }

    public static void main(String... args) throws LineUnavailableException, InterruptedException
    {
        SourceDataLine sdl = AudioSystem.getSourceDataLine(AUDIO_FORMAT);
        byte[] data = new byte[48000];

        SquareWaveChannel channel = new SquareWaveChannel(null, 0, false);
        channel.period = 4096;
        channel.length = 1048576;
        channel.useLength = false;
        channel.clockStart = 0;
        channel.envelopeInitial = 15;
        channel.envelopeIncrease = true;
        channel.envelopeSweep = 7 * 65536;
        channel.duty = 3;
        //channel.render(data, 0, data.length);

        byte[] sample = new byte[1000];
        System.arraycopy(data, 0, sample, 0, sample.length);
        System.out.println(Arrays.toString(sample));

        sdl.open(AUDIO_FORMAT);
        sdl.start();
        sdl.write(data, 0, data.length);
        sdl.drain();
        sdl.stop();
        sdl.close();
    }
}
