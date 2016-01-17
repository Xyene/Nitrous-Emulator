package nitrous.sound;

import nitrous.cpu.Emulator;

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
    /**
     * The start index of NRx1, where x is either 1 or 2.
     *
     * This allows this class to handle two channels.
     */
    private final int ioStart;

    /**
     * Whether frequency sweep is enabled, i.e. whether this is channel 1 or 2.
     */
    private final boolean sweep;

    /**
     * The current Gameboy frequency value, for sweeping.
     */
    private int gbFreq;

    /**
     * The length of each wave cycle, measured in clock cycles.
     */
    private int period = -1;

    /**
     * Length of the sound, in clock cycles.
     */
    private int length = 0x400000;

    /**
     * Whether we should respect the {@link #length} variable, or just play forever.
     */
    private boolean useLength = false;

    /**
     * The clock cycle that marks the start of the current sound.
     */
    private long clockStart = 0;

    /**
     * The initial volume for envelope.
     */
    private int envelopeInitial = 15;

    /**
     * Whether to increase or decrease the volume after {@link #envelopeSweep} cycles later.
     */
    private boolean envelopeIncrease = true;

    /**
     * Time between volume change, measured in clock cycles.
     */
    private int envelopeSweep = 0;

    /**
     * Sound wave duty.
     *
     * Duty   Waveform    Ratio  Cycle
     * -------------------------------
     * 0      00000001    12.5%      6
     * 1      10000001    25%        5
     * 2      10000111    50%        3
     * 3      01111110    75%        1
     */
    private int duty = 0;

    /**
     * The clock cycle when the last frequency sweep happened.
     */
    private long lastSweep = 0;

    /**
     * The amount of CPU cycles between frequency sweeps.
     */
    private int sweepCycles = 0;

    /**
     * Whether the frequency sweeping causes an increase or decrease.
     */
    private boolean sweepIncrease = true;

    /**
     * The amount of frequency to shift.
     *
     * This is the value the original frequency is right shifted by, before increased or decrease.
     */
    private int sweepShift = 0;

    /**
     * Current volume field.
     *
     * -1 means update, hence recalculate from {@link #envelopeInitial}
     */
    int currentVolume = -1;

    /**
     * Field that shows whether we are still playing the sound.
     *
     * i.e. whether {@link #length} is respected and exceeded.
     */
    public boolean isPlaying;

    /**
     * Constructs a {@link SquareWaveChannel} instance.
     *
     * @param core the {@link Emulator} instance.
     * @param ioStart the register index of NRx1, where x is 1 or 2
     * @param sweep whether sweep is enabled, i.e. whether this instance is channel 1.
     */
    public SquareWaveChannel(Emulator core, int ioStart, boolean sweep)
    {
        super(core);

        // Store extra parameters.
        this.ioStart = ioStart;
        this.sweep = sweep;
    }

    /**
     * Handle sound update.
     *
     * This method rereads everything from memory.
     */
    public void handleUpdateRequest()
    {
        byte[] registers = core.mmu.registers;

        // If sweep is enabled:
        if (sweep) {
            // Get the sweep time.
            int newTime = ((registers[ioStart - 1] >> 4) & 0x7) * 32768;

            // Reset lastSweep if the new time is different.
            if (sweepCycles != newTime)
                lastSweep = core.cycle;

            // Update sweep time, direction, and shift value.
            sweepCycles = newTime;
            sweepIncrease = (registers[ioStart - 1] & 0x8) != 0;
            sweepShift = registers[ioStart - 1] & 0x7;
        }

        // Update wave duty and sound length.
        duty = (registers[ioStart] >> 6) & 0x3;
        length = (64 - (registers[ioStart] & 0x3F)) * 16384;

        // Calculate the new initial envelope.
        int newInitial = (registers[ioStart + 1] >> 4) & 0xF;

        // If changed, we update and reset the envelope.
        if (envelopeInitial != newInitial)
        {
            envelopeInitial = newInitial;
            currentVolume = -1;
        }

        // Update envelope increase flag and sweep time.
        envelopeIncrease = (registers[ioStart + 1] & 0x8) != 0;
        envelopeSweep = (registers[ioStart + 1] & 0x7) * 65536;

        // Get the Gameboy frequency value.
        gbFreq = (registers[ioStart + 2] & 0xFF) |
                ((registers[ioStart + 3] & 0x7) << 8);

        // Convert frequency value to period.
        int newPeriod = gbFreqToCycles(gbFreq);

        // If the period changed, restart the sound.
        if (period != newPeriod)
            clockStart = core.cycle;

        // Update period, and whether length is respected.
        period = newPeriod;
        useLength = (registers[ioStart + 3] & 0x40) != 0;
    }


    /**
     * Handle restart request: update {@link #clockStart}.
     */
    @Override
    protected void handleRestartRequest()
    {
        clockStart = core.cycle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int render()
    {
        // Calculate the since start of the note.
        int delta = (int) (core.cycle - clockStart);

        // Calculate the position in the duty cycle.
        int cycle = (delta * 8 / period) & 7;

        // If we are at the beginning of a duty cycle, process requests.
        // If there were requests, restart rendering.
        if (cycle == 0 && handleRequests())
            return render();

        // If the length is respected and exceeded, we are no longer playing.
        // Return no sound.
        if (useLength && delta > length)
        {
            isPlaying = false;
            return 0;
        }

        // So we are playing.
        isPlaying = true;

        // If currentVolume == -1, we are updating the envelope, so we reset the volume to initial.
        if (currentVolume == -1)
            currentVolume = envelopeInitial;

        // Calculate the amplitude of the wave.
        // Note that our amplitude is twice as big Gameboy's since we have more bits of output.
        int amplitude;
        if (envelopeSweep == 0)
        {
            // If there is no sweep, use current volume.
            amplitude = currentVolume * 2;
        } else
        {
            // Otherwise, calculate the amount of sweeping done and predict what the envelope would be at,
            // then capping it to fit between 0 and 15 inclusive.
            currentVolume = amplitude = Math.min(15, Math.max(0, envelopeInitial + delta / envelopeSweep * (envelopeIncrease ? 1 : -1))) * 2;
        }

        // If we are channel 1, and frequency sweeping is enabled, and it's time for another sweep:
        if (sweep && sweepCycles > 0 && core.cycle - lastSweep >= sweepCycles) {
            // Calculate the frequency change, which is the current frequency right shifted by sweepShift.
            int d = gbFreq >> sweepShift;

            // Increase or decrease the freqyency.
            if (sweepIncrease)
                gbFreq += d;
            else
                gbFreq -= d;

            // Make frequency wrap around.
            gbFreq &= 0x7FF;

            // Convert the frequency to period.
            period = gbFreqToCycles(gbFreq);

            // If period is zero, fail.
            if (period == 0)
                throw new RuntimeException();

            // Update last sweep time.
            lastSweep = core.cycle;
        }

        /**
         * Duty   Waveform    Ratio  Cycle
         * -------------------------------
         * 0      00000001    12.5%      6
         * 1      10000001    25%        5
         * 2      10000111    50%        3
         * 3      01111110    75%        1
         *
         * Here, we switch on duty, and if cycle (position in duty cycle) is at a place
         * where the waveform is 1, we return positive amplitude, otherwise, we return negative.
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
