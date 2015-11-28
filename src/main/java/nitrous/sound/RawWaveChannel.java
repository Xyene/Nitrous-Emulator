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
    private int[] samples = new int[32];

    public RawWaveChannel(Emulator core) {
        super(core);
    }

    public void update() {
        byte[] registers = core.mmu.registers;

        if ((registers[R.R_NR34] & 0x80) != 0)
            restart();

        enabled = (registers[R_NR30] & 0x80) != 0;
        length = registers[R_NR31] & 0xFF;

        shift = 3 - ((core.mmu.registers[0x1C] >> 5) & 0x3);

        gbFreq = (registers[R_NR33] & 0xFF) |
                ((registers[R_NR34] & 0x7) << 8);
        period = 2 * gbFreqToCycles(gbFreq);

        useLength = (registers[R_NR34] & 0x40) != 0;
    }

    public void restart() {
        clockStart = core.cycle;
    }

    public void updateSample(int byteId, byte value) {
        samples[byteId * 2] = ((value >> 4) & 0xF) - 8;
        samples[byteId * 2 + 1] = (value & 0xF) - 8;
    }

    @Override
    public int render() {
        if (!enabled || shift == 3)
            return 0;
        long delta = core.cycle - clockStart;
        if (useLength && delta > length)
            return 0;
        return samples[(int) (delta / period) & 0x1F] << shift;
    }
}
