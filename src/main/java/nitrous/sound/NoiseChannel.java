package nitrous.sound;

import nitrous.Emulator;

import static nitrous.R.*;

/**
 * This class implements channel 4 of the Gameboy's programmable sound chip.
 *
 * This channel outputs white noise. This is done by randomly switching the amplitude between
 * high and low at a given frequency. Depending on the frequency the noise will appear 'harder'
 * or 'softer'.
 *
 * It is also possible to influence the function of the random generator, so the that the output becomes
 * more regular, resulting in a limited ability to output Tone instead of Noise.
 *
 * <strong>FF20 - NR41 - Channel 4 Sound Length (R/W)</strong>
 *
 * <ul>
 *     <li>Bit 5-0 - Sound length data (t1: 0-63)</li>
 * </ul>
 *
 * Sound Length = (64-t1)*(1/256) seconds
 * The Length value is used only if Bit 6 in NR44 is set.
 *
 * <strong>FF21 - NR42 - Channel 4 Volume Envelope (R/W)</strong>
 *
 * <ul>
 *     <li>Bit 7-4 - Initial Volume of envelope (0-0Fh) (0=No Sound)</li>
 *     <li>Bit 3   - Envelope Direction (0=Decrease, 1=Increase)</li>
 *     <li>Bit 2-0 - Number of envelope sweep (n: 0-7). If zero, stop envelope operation.</li>
 * </ul>
 *
 * Length of 1 step = n*(1/64) seconds
 *
 * <strong>FF22 - NR43 - Channel 4 Polynomial Counter (R/W)</strong>
 * The amplitude is randomly switched between high and low at the given frequency.
 * A higher frequency will make the noise to appear 'softer'.
 *
 * When Bit 3 is set, the output will become more regular,
 * and some frequencies will sound more like Tone than Noise.
 *
 * <ul>
 *     <li>Bit 7-4 - Shift Clock Frequency (s)</li>
 *     <li>Bit 3   - Counter Step/Width (0=15 bits, 1=7 bits)</li>
 *     <li>Bit 2-0 - Dividing Ratio of Frequencies (r)</li>
 * </ul>
 *
 * Frequency = 524288 Hz / r / 2^(s+1) ;For r=0 assume r=0.5 instead

 * <strong>FF23 - NR44 - Channel 4 Counter/consecutive; Inital (R/W)</strong>
 * <ul>
 *     <li>Bit 7   - Initial (1=Restart Sound)     (Write Only)</li>
 *     <li>Bit 6   - Counter/consecutive selection (Read/Write, 1=Stop output when length in NR41 expires)</li>
 * </ul>
 *
 * @see <a href="http://bgb.bircd.org/pandocs.htm#soundchannel4noise">Sound Channel 4 - Noise - Pandocs</a>
 */
public class NoiseChannel extends SoundChannel
{
    private int gbFreq;
    public int period = -1;
    public int length = 0x400000;
    public boolean useLength = false;
    private long clockStart = 0;
    public int envelopeInitial = 15;
    private boolean envelopeIncrease = true;
    private int envelopeSweep = 0;

    public NoiseChannel(Emulator core)
    {
        super(core);
    }

    @Override
    protected void handleUpdateRequest()
    {
        byte[] registers = core.mmu.registers;

        length = (64 - (registers[R_NR41] & 0x3F)) * 16384;

        int newEnvelope = (registers[R_NR42] >> 4) & 0xF;

        if (envelopeInitial != newEnvelope)
        {
            envelopeInitial = newEnvelope;
            currentVolume = -1;
        }

        envelopeIncrease = (registers[R_NR42] & 0x8) != 0;
        envelopeSweep = (registers[R_NR42] & 0x7) * 65536;

        int s = (registers[R_NR43] >> 4) & 0xF;
        int r = registers[R_NR43] & 0x7;
        boolean _7bits = (registers[R_NR43] & 0x8) != 0;

        int freq = (r == 0 ? 1048576 : 524288 * r) >> (s + 1);
        period = 4194304 / freq;

        useLength = (registers[R_NR44] & 0x40) != 0;
    }

    @Override
    protected void handleRestartRequest()
    {
        clockStart = core.cycle;
    }

    int currentVolume = -1;
    boolean high = false;
    long lastToggle = Integer.MIN_VALUE;

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
        {
            amplitude = currentVolume * 2;
        } else
        {
            currentVolume = amplitude = Math.min(15, Math.max(0, envelopeInitial + delta / envelopeSweep * (envelopeIncrease ? 1 : -1))) * 2;
        }

        if (core.cycle - lastToggle >= period)
        {
            if (handleRequests())
                return render();

            high ^= Math.random() >= 0.5;
            lastToggle = core.cycle;
        }

        return high ? amplitude : -amplitude;
    }
}
