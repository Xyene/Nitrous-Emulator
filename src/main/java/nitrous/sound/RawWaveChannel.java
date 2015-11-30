package nitrous.sound;

import nitrous.Emulator;

import static nitrous.R.*;

public class RawWaveChannel extends SoundChannel
{
    private boolean enabled = false;
    private int length;
    private int gbFreq;
    private int period;
    private int shift;
    private boolean useLength;

    private long clockStart = 0;
    private int[] samples = new int[32];
    private int[] updated = new int[32];

    public RawWaveChannel(Emulator core)
    {
        super(core);
    }

    private boolean requestUpdate = false;
    private boolean requestRestart = false;
    private boolean requestCopy = false;

    public void handleUpdateRequest() {
        requestUpdate = false;
        byte[] registers = core.mmu.registers;

        enabled = (registers[R_NR30] & 0x80) != 0;

        useLength = (registers[R_NR34] & 0x40) != 0;
        length = (256 - (registers[R_NR31] & 0xFF)) * 16384;

        shift = 0;//((core.mmu.registers[R_NR32] >> 5) & 0x3);

        gbFreq = (registers[R_NR33] & 0xFF) |
                ((registers[R_NR34] & 0x7) << 8);
        int n = gbFreqToCycles(gbFreq) / 32;

        if (period != n) {
            period = n;
            clockStart = core.cycle;
        }

        //System.out.printf("period=%d, length=%d, enabled=%b, shift=%d\n", period, useLength ? length : -1, enabled, shift);
    }

    public void update()
    {
        requestUpdate = true;
    }

    public void handleRestartRequest()
    {
        clockStart = core.cycle;
        requestRestart = false;
    }

    public void restart()
    {
        requestRestart = true;
    }

    public void handleCopyRequest()
    {
        System.arraycopy(updated, 0, samples, 0, samples.length);
        requestCopy = false;
    }

    public void updateSample(int byteId, byte value)
    {
        if (enabled) return;
        updated[byteId * 2] = ((value >> 4) & 0xF);
        updated[byteId * 2 + 1] = (value & 0xF);
        requestCopy = true;
    }

    private long lastUpdate = Integer.MIN_VALUE;

    public int render()
    {
        if (core.cycle - lastUpdate >= period)
        {
            if (requestUpdate)
                handleUpdateRequest();
            if (requestCopy)
                handleCopyRequest();
            if (requestRestart)
                handleRestartRequest();
            lastUpdate = core.cycle;
        }
        if (!enabled)
            return 0;
        long delta = core.cycle - clockStart;
        if (useLength && delta > length)
            return 0;

        return samples[(int) (delta / period) & 0x1F] << shift;
    }
}
