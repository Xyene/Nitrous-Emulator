package nitrous.sound;

import nitrous.Emulator;

import static nitrous.R.*;

/**
 * This class implements channel 3 of the Gameboy's programmable sound chip.
 *
 * This channel can be used to output digital sound, the length of the sample buffer
 * (Wave RAM) is limited to 32 digits. This sound channel can be also used to output
 * normal tones when initializing the Wave RAM by a square wave. This channel doesn't
 * have a volume envelope register.

 * <strong>FF1A - NR30 - Channel 3 Sound on/off (R/W)</strong>
 *
 * <ul>
 *     <li>Bit 7 - Sound Channel 3 Off  (0=Stop, 1=Playback)  (Read/Write)</li>
 * </ul>
 *
 * <strong>FF1B - NR31 - Channel 3 Sound Length</strong>
 *
 * <ul>
 *     <li>Bit 7-0 - Sound length (t1: 0 - 255)</li>
 * </ul>
 *
 * Sound Length = (256-t1)*(1/256) seconds
 * This value is used only if Bit 6 in NR34 is set.
 *
 * <strong>FF1C - NR32 - Channel 3 Select output level (R/W)</strong>
 *
 * <ul>
 *     <li>Bit 6-5 - Select output level (Read/Write)</li>
 * </ul>
 *
 * Possible Output levels are:
 *
 * 0: Mute (No sound)
 * 1: 100% Volume (Produce Wave Pattern RAM Data as it is)
 * 2:  50% Volume (Produce Wave Pattern RAM data shifted once to the right)
 * 3:  25% Volume (Produce Wave Pattern RAM data shifted twice to the right)
 *
 *
 * <strong>FF1D - NR33 - Channel 3 Frequency's lower data (W)</strong>
 * Lower 8 bits of an 11 bit frequency (x).
 *
 * <strong>FF1E - NR34 - Channel 3 Frequency's higher data (R/W)</strong>
 *
 * <ul>
 *     <li>Bit 7   - Initial (1=Restart Sound)     (Write Only)</li>
 *     <li>Bit 6   - Counter/consecutive selection (Read/Write, 1=Stop output when length in NR31 expires)</li>
 *     <li>Bit 2-0 - Frequency's higher 3 bits (x) (Write Only)</li>
 * </ul>
 *
 * Frequency = 4194304/(64*(2048-x)) Hz = 65536/(2048-x) Hz
 *
 * <strong>FF30-FF3F - Wave Pattern RAM</strong>
 * Contents - Waveform storage for arbitrary sound data
 *
 * This storage area holds 32 4-bit samples that are played back upper 4 bits first.
 *
 * @see <a href="http://bgb.bircd.org/pandocs.htm#soundchannel3waveoutput">Sound Channel 3 - Wave Output - Pandocs</a>
 */
public class RawWaveChannel extends SoundChannel
{
    /**
     * Flag to indicate whether this channel is enabled.
     */
    private boolean enabled = false;

    /**
     * Length of the sound, in clock cycles.
     */
    private int length;

    /**
     * Whether we should respect the {@link #length} variable, or just play forever.
     */
    private boolean useLength;

    /**
     * Amount of time one sample is played for.
     *
     * Measured in clock cycles.
     */
    private int period;

    /**
     * The amount of bits to shift the sound, for Gameboy's crude parody of volume control.
     *
     * 3 = mute, which counts as playing.
     */
    private int shift;

    /**
     * The clock cycle that marks the start of the current sound.
     */
    private long clockStart = 0;

    /**
     * Array of samples which are being played.
     */
    private int[] samples = new int[32];

    /**
     * Array of samples that will replace {@link #samples} on the next update, if there is one.
     */
    private int[] updated = new int[32];

    /**
     * Flag to signal a sample update request.
     */
    private boolean requestCopy = false;

    /**
     * The clock cycle of the last update.
     */
    private long lastUpdate = Integer.MIN_VALUE;

    /**
     * Field that shows whether we are still playing the sound.
     *
     * i.e. whether {@link #length} is respected and exceeded, and the channel is enabled.
     */
    public boolean isPlaying;

    /**
     * Creates a {@link RawWaveChannel} instance.
     *
     * @param core the {@link Emulator} instance
     */
    public RawWaveChannel(Emulator core)
    {
        super(core);
    }

    /**
     * Handle sound update.
     *
     * This method rereads everything from memory.
     */
    public void handleUpdateRequest() {
        byte[] registers = core.mmu.registers;

        // Check whether the channel is enabled.
        enabled = (registers[R_NR30] & 0x80) != 0;

        // Get the length of the sound, and whether it is respected.
        useLength = (registers[R_NR34] & 0x40) != 0;
        length = (256 - (registers[R_NR31] & 0xFF)) * 16384;

        // Calculate shift value.
        shift = 3 - ((core.mmu.registers[0x1C] >> 5) & 0x3);

        // Get the Gameboy frequency value.
        int gbFreq = (registers[R_NR33] & 0xFF) |
                ((registers[R_NR34] & 0x7) << 8);

        // Convert frequency value to period.
        // Note that Gameboy frequency value for wave channel is halfed for this channel.
        // And that it refers to the time it takes to play all 32 samples, so we must divide by 16.
        int newPeriod = gbFreqToCycles(gbFreq) / 16;

        // If the period changed, restart the sound.
        if (period != newPeriod) {
            period = newPeriod;
            clockStart = core.cycle;
        }
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
     * Handle sample update request: update {@link #samples} with {@link #updated}.
     */
    private void handleCopyRequest()
    {
        // Copy samples.
        System.arraycopy(updated, 0, samples, 0, samples.length);

        // Set request flag to false.
        requestCopy = false;
    }

    /**
     * Update sample request, by byte. Each byte contains two samples.
     *
     * @param byteId the byte of the sample to update
     * @param value the new value of the byte
     */
    public void updateSample(int byteId, byte value)
    {
        // Do nothing if disabled.
        if (enabled) return;

        // Update two samples, converting them to signed PCM data.
        updated[byteId * 2] = ((value >> 4) & 0xF) - 8;
        updated[byteId * 2 + 1] = (value & 0xF) - 8;

        // Set request flag.
        requestCopy = true;
    }

    /**
     * {@inheritDoc}
     */
    public int render()
    {
        // If we have exceed one period since last update.
        if (core.cycle - lastUpdate > period)
        {
            // Handle update and restart requests.
            handleRequests();

            // Handle update sample request.
            if (requestCopy)
                handleCopyRequest();

            // Update last update time.
            lastUpdate = core.cycle;
        }

        // Calculate the amount of time elapsed since the sound started.
        long delta = core.cycle - clockStart;

        // If not playing, set isPlaying and return no sound.
        if (!enabled || (useLength && delta > length))
        {
            isPlaying = false;
            return 0;
        }

        // We are playing.
        isPlaying = true;

        if (shift == 3)
        {
            // If shift value is 3, we are muted.
            return 0;
        } else
        {
            // Otherwise, find the currently playing sample and shift it.
            return (samples[(int) (delta / period) & 0x1F] << shift);
        }
    }
}
