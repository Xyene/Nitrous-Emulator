package nitrous.sound;

import nitrous.Emulator;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;

public class SquareWaveChannel extends SoundChannel
{

    private static final int dutyConvert[] = {6, 5, 3, 1};

    private final int ioStart;
    private final boolean sweep;

    private int gbFreq;
    private int period = 4096;
    private int length = 1048576;
    private boolean useLength = false;
    private int clockBase = 0;
    private int envelopeInitial = 15;
    private boolean envelopeIncrease = true;
    private int envelopeSweep = 0;
    private int duty = 6;

    public boolean enabled = false;

    public SquareWaveChannel(Emulator core, int ioStart, boolean sweep)
    {
        super(core);

        this.ioStart = ioStart;
        this.sweep = sweep;
    }

    private static int gbFreqToCycles(int gbFreq) {
        return 32 * (2048 - gbFreq);
    }

    public void update()
    {
        duty = dutyConvert[(core.mmu.registers[ioStart] >> 6) & 0x3];
        length = (64 - (core.mmu.registers[ioStart] & 0x3F)) * 16384;
        envelopeInitial = (core.mmu.registers[ioStart + 1] >> 4) & 0xF;
        envelopeIncrease = (core.mmu.registers[ioStart + 1] & 0x8) != 0;
        envelopeSweep = (core.mmu.registers[ioStart + 1] & 0x7) * 65536;

        gbFreq = (core.mmu.registers[ioStart + 2] & 0xFF) |
                ((core.mmu.registers[ioStart + 3] & 0x7) << 8);
        period = gbFreqToCycles(gbFreq);

        useLength = (core.mmu.registers[ioStart + 3] & 0x40) != 0;
    }

    public void render(byte[] output, int off, int len)
    {
        if (!enabled)
            return;

        for (int i = 0; i < len; ++i)
        {
            int clock = clockBase + (int) (i * samplePeriod);
            //System.out.println(clock);
            if (useLength && clock > length)
                break;
            int volume;
            if (envelopeSweep == 0)
                volume = envelopeInitial;
            else
                volume = Math.min(15, Math.max(0, envelopeInitial + clock / envelopeSweep * (envelopeIncrease ? 1 : -1))) * 2;
            int cycle = (clock * 8 / period) & 7;

            /**
             * Duty   Waveform    Ratio  Cycle
             * -------------------------------
             * 0      00000001    12.5%      6
             * 1      10000001    25%        5
             * 2      10000111    50%        3
             * 3      01111110    75%        1
             */
            output[off+i] += (byte) (cycle > duty ? volume : -volume);
            /*switch (duty) {
                case 0:
                    output[i] = (byte) (cycle == 7 ? volume : -volume);
                    break;
                case 1:
                    output[i] = (byte) (cycle == 0 || cycle == 7 ? volume : -volume);
                    break;
                case 2:
                    output[i] = (byte) (cycle == 0 || cycle > 4 ? volume : -volume);
                    break;
                case 3:
                    output[i] = (byte) (cycle != 0 && cycle != 7 ? volume : -volume);
                    break;
            }*/
        }
        clockBase += len * samplePeriod;
    }

    public static void main(String... args) throws LineUnavailableException, InterruptedException
    {
        SourceDataLine sdl = AudioSystem.getSourceDataLine(AUDIO_FORMAT);
        byte[] data = new byte[48000];

        SquareWaveChannel channel = new SquareWaveChannel(null, 0, false);
        channel.enabled = true;
        channel.period = 4096;
        channel.length = 1048576;
        channel.useLength = false;
        channel.clockBase = 0;
        channel.envelopeInitial = 15;
        channel.envelopeIncrease = true;
        channel.envelopeSweep = 7 * 65536;
        channel.duty = 3;
        channel.render(data, 0, data.length);

        byte[] sample = new byte[1000];
        System.arraycopy(data, 0, sample, 0, sample.length);
        System.out.println(Arrays.toString(sample));

        sdl.open(AUDIO_FORMAT);
        sdl.start();
        sdl.write(data, 0, data.length);
        sdl.drain();
        sdl.stop();
        sdl.close();
    }
}
