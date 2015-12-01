package nitrous.sound;

import nitrous.Emulator;

import static nitrous.R.*;

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

    boolean updateRequest = false;
    boolean restartRequest = false;

    private void handleUpdateRequest()
    {
        updateRequest = false;
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

    public void update()
    {
        updateRequest = true;
    }

    public void handleRestartRequest()
    {
        clockStart = core.cycle;
        restartRequest = false;
    }

    public void restart()
    {
        restartRequest = true;
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
            boolean didSomething = false;

            if (updateRequest)
            {
                handleUpdateRequest();
                didSomething = true;
            }
            if (restartRequest)
            {
                handleRestartRequest();
                didSomething = true;
            }

            if(didSomething) return render();

            high ^= Math.random() >= 0.5;
            lastToggle = core.cycle;
        }

        return high ? amplitude : -amplitude;
    }
}
