package nitrous.sound;

import nitrous.Emulator;

public class RawWaveChannel extends SoundChannel {
    private boolean enabled = false;
    private int length;
    private int gbFreq;
    private int period;
    private int shift;
    private boolean useLength;

    private long clockStart = 0;
    private byte[] samples = new byte[32];

    public RawWaveChannel(Emulator core) {
        super(core);
    }

    public void update() {
        enabled = (core.mmu.getIO(0x1A) & 0x80) != 0;
        length = core.mmu.getIO(0x1B) & 0xFF;

        int level = (core.mmu.registers[0x1C] >> 5) & 0x3;
        shift = level == 0 ? 5 : level - 1;

        gbFreq = (core.mmu.registers[0x1D] & 0xFF) |
                ((core.mmu.registers[0x1E] & 0x7) << 8);
        period = 2 * gbFreqToCycles(gbFreq);

        useLength = (core.mmu.registers[0x1E] & 0x40) != 0;
    }

    public void restart() {
        clockStart = core.cycle;
    }

    public void updateSample(int byteId, byte value) {
        samples[byteId * 2] = (byte) ((value >> 4) & 0xF);
        samples[byteId * 2 + 1] = (byte) (value & 0xF);
    }

    @Override
    public int render() {
        return samples[((int) (((core.cycle - clockStart) / period) & 0x1F))] << 1 >> shift;
    }

    public void render(byte[] output, int off, int len) {
        if (!enabled)
            return;

        float samplePeriod = core.clockSpeed / AUDIO_FORMAT.getSampleRate();

        for (int i = 0; i < len; ++i)
        {
            /*int clock = clockStart + (int) (i * samplePeriod);
            if (useLength && clock > length)
                break;

            output[i] += (byte) (samples[(clock / period) & 0x1F] << 1 >> shift);*/
        }
        clockStart += len * samplePeriod;
    }
}
