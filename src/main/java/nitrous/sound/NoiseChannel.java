package nitrous.sound;

import nitrous.cpu.Emulator;

import static nitrous.cpu.R.*;

/**
 * This class implements channel 4 of the Gameboy's programmable sound chip.
 * <p/>
 * This channel outputs white noise. This is done by randomly switching the amplitude between
 * high and low at a given frequency. Depending on the frequency the noise will appear 'harder'
 * or 'softer'.
 * <p/>
 * It is also possible to influence the function of the random generator, so the that the output becomes
 * more regular, resulting in a limited ability to output Tone instead of Noise.
 * <p/>
 * <strong>FF20 - NR41 - Channel 4 Sound Length (R/W)</strong>
 * <p/>
 * <ul>
 * <li>Bit 5-0 - Sound length data (t1: 0-63)</li>
 * </ul>
 * <p/>
 * Sound Length = (64-t1)*(1/256) seconds
 * The Length value is used only if Bit 6 in NR44 is set.
 * <p/>
 * <strong>FF21 - NR42 - Channel 4 Volume Envelope (R/W)</strong>
 * <p/>
 * <ul>
 * <li>Bit 7-4 - Initial Volume of envelope (0-0Fh) (0=No Sound)</li>
 * <li>Bit 3   - Envelope Direction (0=Decrease, 1=Increase)</li>
 * <li>Bit 2-0 - Number of envelope sweep (n: 0-7). If zero, stop envelope operation.</li>
 * </ul>
 * <p/>
 * Length of 1 step = n*(1/64) seconds
 * <p/>
 * <strong>FF22 - NR43 - Channel 4 Polynomial Counter (R/W)</strong>
 * The amplitude is randomly switched between high and low at the given frequency.
 * A higher frequency will make the noise to appear 'softer'.
 * <p/>
 * When Bit 3 is set, the output will become more regular,
 * and some frequencies will sound more like Tone than Noise.
 * <p/>
 * <ul>
 * <li>Bit 7-4 - Shift Clock Frequency (s)</li>
 * <li>Bit 3   - Counter Step/Width (0=15 bits, 1=7 bits)</li>
 * <li>Bit 2-0 - Dividing Ratio of Frequencies (r)</li>
 * </ul>
 * <p/>
 * Frequency = 524288 Hz / r / 2^(s+1) ;For r=0 assume r=0.5 instead
 * <p/>
 * <strong>FF23 - NR44 - Channel 4 Counter/consecutive; Inital (R/W)</strong>
 * <ul>
 * <li>Bit 7   - Initial (1=Restart Sound)     (Write Only)</li>
 * <li>Bit 6   - Counter/consecutive selection (Read/Write, 1=Stop output when length in NR41 expires)</li>
 * </ul>
 *
 * @author Quantum
 * @author Tudor
 * @see <a href="http://bgb.bircd.org/pandocs.htm#soundchannel4noise">Sound Channel 4 - Noise - Pandocs</a>
 */
public class NoiseChannel extends SoundChannel
{
    /**
     * Time between potential toggles in the wave form.
     * <p/>
     * Measured in clock cycles.
     */
    public int period = -1;

    /**
     * Length of the sound, in clock cycles.
     */
    public int length = 0x400000;

    /**
     * Whether we should respect the {@link #length} variable, or just play forever.
     */
    public boolean useLength = false;

    /**
     * The clock cycle that marks the start of the current sound.
     */
    private long clockStart = 0;

    /**
     * The initial volume for envelope.
     */
    public int envelopeInitial = 15;

    /**
     * Whether to increase or decrease the volume after {@link #envelopeSweep} cycles later.
     */
    private boolean envelopeIncrease = true;

    /**
     * Time between volume change, measured in clock cycles.
     */
    private int envelopeSweep = 0;

    /**
     * Whether the wave should be more regular.
     */
    private boolean regular;

    /**
     * Current volume field.
     * <p/>
     * -1 means update, hence recalculate from {@link #envelopeInitial}
     */
    int currentVolume = -1;

    /**
     * The current state of the waveform.
     * <p/>
     * {@literal true} means a positive amplitude, and {@literal false} means a negative one.
     */
    boolean high = false;

    /**
     * The clock cycle when the waveform was last toggled.
     */
    long lastToggle = Integer.MIN_VALUE;

    /**
     * Field that shows whether we are still playing the sound.
     * <p/>
     * i.e. whether {@link #length} is respected and exceeded.
     */
    public boolean isPlaying;

    /**
     * Creates a {@link NoiseChannel} class.
     *
     * @param core the {@link Emulator} instance
     */
    public NoiseChannel(Emulator core)
    {
        super(core);
    }//end NoiseChannel(core)

    /**
     * Handle sound update.
     * <p/>
     * This method rereads everything from memory.
     */
    @Override
    protected void handleUpdateRequest()
    {
        byte[] registers = core.mmu.registers;

        // Update length.
        length = (64 - (registers[R_NR41] & 0x3F)) * 16384;

        // Check if we should respect sound length.
        useLength = (registers[R_NR44] & 0x40) != 0;

        // Get envelope initial settings.
        int newEnvelope = (registers[R_NR42] >> 4) & 0xF;

        // If changed, we update and reset the envelope.
        if (envelopeInitial != newEnvelope)
        {
            envelopeInitial = newEnvelope;
            currentVolume = -1;
        }//end if

        // Update envelope increase flag and sweep time.
        envelopeIncrease = (registers[R_NR42] & 0x8) != 0;
        envelopeSweep = (registers[R_NR42] & 0x7) * 65536;

        // Get parameters s and r used in frequency calculation.
        int s = (registers[R_NR43] >> 4) & 0xF;
        int r = registers[R_NR43] & 0x7;

        // Check if the noise should be more regular.
        regular = (registers[R_NR43] & 0x8) != 0;

        // Calculate frequency.
        int freq;
        if (r == 0)
        {
            // If r == 0, we use 1048576 Hz / 2^(s+1).
            freq = 1048576 >> (s + 1);
        } else
        {
            // Otherwise we use 524288 Hz / r / 2^(s+1)
            freq = 524288 * r >> (s + 1);
        }//end if

        // Convert frequency to period.
        period = 4194304 / freq;
    }//end handleUpdateRequest

    /**
     * Handle restart request: update {@link #clockStart}.
     */
    @Override
    protected void handleRestartRequest()
    {
        clockStart = core.cycle;
    }//end handleRestartRequest

    /**
     * {@inheritDoc}
     */
    @Override
    public int render()
    {
        // Calculate the amount of clock cycles elapsed since the sound was started.
        int delta = (int) (core.cycle - clockStart);

        // If this exceeds the sound length, stop playing and return no sound.
        if (useLength && delta > length)
        {
            isPlaying = false;
            return 0;
        }//end if

        // Otherwise the sound is still playing.
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
        }//end if

        // If it has been one entire period since last toggle.
        if (core.cycle - lastToggle >= period)
        {
            // Handle update and restart requests, then restart processing.
            if (handleRequests())
                return render();

            // Toggle the waveform randomly.
            high ^= Math.random() >= 0.5;

            // Update the last toggle time.
            lastToggle = core.cycle;
        }//end if

        // Return a positive amplitude if the wave is high, otherwise a negative one.
        return high ? amplitude : -amplitude;
    }//end render
}//end class NoiseChannel
