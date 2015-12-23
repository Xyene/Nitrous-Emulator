package nitrous.sound;

import nitrous.Emulator;

/**
 * This class implements channel 1 and 2 of the Gameboy's programmable sound chip.
 *
 * FF10 - NR10 - Channel 1 Sweep register (R/W)
 *
 * <ul>
 *     <li>Bit 6-4 - Sweep Time</li>
 *     <li>Bit 3   - Sweep Increase/Decrease (0=frequency increases, 1=frequency decreases)</li>
 *     <li>Bit 2-0 - Number of sweep shift (n: 0-7)</li>
 * </ul>
 *
 * Sweep Time:
 *
 * <ul>
 *     <li>000: sweep off - no freq change</li>
 *     <li>001: 7.8 ms  (1/128Hz)</li>
 *     <li>010: 15.6 ms (2/128Hz)</li>
 *     <li>011: 23.4 ms (3/128Hz)</li>
 *     <li>100: 31.3 ms (4/128Hz)</li>
 *     <li>101: 39.1 ms (5/128Hz)</li>
 *     <li>110: 46.9 ms (6/128Hz)</li>
 *     <li>111: 54.7 ms (7/128Hz)</li>
 * </ul>
 *
 * The change of frequency (NR13,NR14) at each shift is calculated by the following formula where X(0)
 * is initial freq & X(t-1) is last freq:
 *
 * X(t) = X(t-1) +/- X(t-1)/2^n
 *
 * <strong>FF11 - NR11 - Channel 1 Sound length/Wave pattern duty (R/W)</strong><br>
 * <strong>FF16 - NR21 - Channel 2 Sound Length/Wave Pattern Duty (R/W)</strong>
 *
 * <ul>
 *     <li>Bit 7-6 - Wave Pattern Duty (Read/Write)</li>
 *     <li>Bit 5-0 - Sound length data (Write Only) (t1: 0-63)</li>
 * </ul>
 *
 * Wave Duty:
 *
 * <ul>
 *     <li>00: 12.5% ( _-------_-------_------- )</li>
 *     <li>01: 25%   ( __------__------__------ )</li>
 *     <li>10: 50%   ( ____----____----____---- ) (normal)</li>
 *     <li>11: 75%   ( ______--______--______-- )</li>
 * </ul>
 *
 * Sound Length = (64-t1)*(1/256) seconds
 * The Length value is used only if Bit 6 in NR24 is set.
 *
 * <strong>FF12 - NR12 - Channel 1 Volume Envelope (R/W)</strong><br>
 * <strong>FF17 - NR22 - Channel 2 Volume Envelope (R/W)</strong>
 *
 * <ul>
 *     <li>Bit 7-4 - Initial Volume of envelope (0-0Fh) (0=No Sound)</li>
 *     <li>Bit 3   - Envelope Direction (0=Decrease, 1=Increase)</li>
 *     <li>Bit 2-0 - Number of envelope sweep (n: 0-7. If zero, stop envelope operation.)</li>
 * </ul>
 *
 * Length of 1 step = n*(1/64) seconds
 *
 * <strong>FF13 - NR13 - Channel 1 Frequency lo (Write Only)</strong><br>
 * <strong>FF18 - NR23 - Channel 2 Frequency lo data (W)</strong>
 *
 * Frequency's lower 8 bits of 11 bit data (x).
 * Next 3 bits are in NR24 ($FF19).
 *
 * <strong>FF14 - NR14 - Channel 1 Frequency hi (R/W)</strong><br>
 * <strong>FF19 - NR24 - Channel 2 Frequency hi data (R/W)</strong>
 *
 * <ul>
 *     <li>Bit 7   - Initial (1=Restart Sound)     (Write Only)</li>
 *     <li>Bit 6   - Counter/consecutive selection (Read/Write, 1=Stop output when length in NR21 expires)</li>
 *     <li>Bit 2-0 - Frequency's higher 3 bits (x) (Write Only)</li>
 * </ul>
 *
 * Frequency = 131072/(2048-x) Hz
 *
 * @see <a href="http://bgb.bircd.org/pandocs.htm#soundchannel3waveoutput">Sound Channel 3 - Wave Output - Pandocs</a>
 */
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

    public boolean isPlaying;

    @Override
    public int render()
    {
        int delta = (int) (core.cycle - clockStart);

        int cycle = (delta * 8 / period) & 7;
        if (lastCycle == -1)
            lastCycle = cycle;

        lastCycle = cycle;

        if (cycle == 0 && handleRequests())
            return render();

        if (useLength && delta > length)
        {
            isPlaying = false;
            return 0;
        }
        isPlaying = true;

        if (currentVolume == -1)
            currentVolume = envelopeInitial;

        int amplitude;
        if (envelopeSweep == 0)
            amplitude = currentVolume * 2;
        else
            currentVolume = amplitude = Math.min(15, Math.max(0, envelopeInitial + delta / envelopeSweep * (envelopeIncrease ? 1 : -1))) * 2;

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
