package nitrous.sound;

import nitrous.Emulator;
import nitrous.R;

import static nitrous.R.*;

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
        byte[] registers = core.mmu.registers;

        if ((registers[R.R_NR34] & 0x80) != 0)
            restart();

        enabled = (registers[R_NR30] & 0x80) != 0;
        length = registers[R_NR31] & 0xFF;

        int level = (registers[R_NR32] >> 5) & 0x3;
        shift = level == 0 ? 5 : level - 1;

        gbFreq = (registers[R_NR33] & 0xFF) |
                ((registers[R_NR34] & 0x7) << 8);
        period = 2 * gbFreqToCycles(gbFreq);

        useLength = (registers[R_NR34] & 0x40) != 0;
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
        if (enabled)
            return samples[((int) (((core.cycle - clockStart) / period) & 0x1F))] << 1 >> shift;
        else
            return 0;
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
