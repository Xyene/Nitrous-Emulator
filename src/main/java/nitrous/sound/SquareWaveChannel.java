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

    private long lastSweep = 0;
    private int sweepCycles = 0;
    private boolean sweepIncrease = true;
    private int sweepShift = 0;

    public SquareWaveChannel(Emulator core, int ioStart, boolean sweep)
    {
        super(core);

        this.ioStart = ioStart;
        this.sweep = sweep;
    }

    public int ___ = 0;

    public void handleUpdateRequest()
    {
        byte[] registers = core.mmu.registers;

        if (sweep) {
            int newTime = ((registers[ioStart - 1] >> 4) & 0x7) * 32768;
            if (sweepCycles != newTime)
                lastSweep = core.cycle;
            sweepCycles = newTime;
            sweepIncrease = (registers[ioStart - 1] & 0x8) != 0;
            sweepShift = registers[ioStart - 1] & 0x7;
        }

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

    @Override
    protected void handleRestartRequest()
    {
        clockStart = core.cycle;
        lastCycle = -1;
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

        if (sweep && sweepCycles > 0 && core.cycle - lastSweep >= sweepCycles) {
            int d = gbFreq >> sweepShift;
            if (sweepIncrease)
                gbFreq += d;
            else
                gbFreq -= d;
            gbFreq &= 0x7FF;

            period = gbFreqToCycles(gbFreq);
            if (period == 0)
                throw new RuntimeException();
            lastSweep = core.cycle;
        }

        if (cycle == 0 && handleRequests())
            return render();

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
}
