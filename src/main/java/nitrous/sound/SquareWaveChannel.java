package nitrous.sound;

import nitrous.Emulator;
import nitrous.R;

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
    public int period = -1;
    public int length = 0x400000;
    public boolean useLength = false;
    private long clockStart = 0;
    public int envelopeInitial = 15;
    private boolean envelopeIncrease = true;
    private int envelopeSweep = 0;
    public int duty = 6;

    public SquareWaveChannel(Emulator core, int ioStart, boolean sweep)
    {
        super(core);

        this.ioStart = ioStart;
        this.sweep = sweep;
    }

    public int ___ = 0;

    public void handleUpdateRequest()
    {
        updateRequest = false;
        byte[] registers = core.mmu.registers;

        /**
         * Duty   Waveform    Ratio  Cycle
         * -------------------------------
         * 0      00000001    12.5%      6
         * 1      10000001    25%        5
         * 2      10000111    50%        3
         * 3      01111110    75%        1
         */
        duty = (registers[ioStart] >> 6) & 0x3;//dutyConvert[(registers[ioStart] >> 6) & 0x3];

        length = (64 - (registers[ioStart] & 0x3F)) * core.clockSpeed / 256;


        int xy = (registers[ioStart + 1] >> 4) & 0xF;

        if (envelopeInitial != xy)
        {
            System.out.println("Changed volume to " + xy);
//            clockStart = core.cycle;
            envelopeInitial = xy;
            currentVolume = -1;
        }

        envelopeIncrease = (registers[ioStart + 1] & 0x8) != 0;
        envelopeSweep = (registers[ioStart + 1] & 0x7) * core.clockSpeed / 64;

        int _gbFreq = (registers[ioStart + 2] & 0xFF) |
                ((registers[ioStart + 3] & 0x7) << 8);

        int n = gbFreqToCycles(_gbFreq);
        if (period != n)
        {
            clockStart = core.cycle;
            shortC = true;
        }
        period = n;
        core.sound.channel2.___++;
        core.sound.channel2.___ %= 10;
//        System.out.printf("%speriod=%d, length=%d, duty=%d, volume=%d, clock=%d\n", (core.sound.channel2.___ == 0) ? "*" : " ",
//                core.sound.channel2.period, core.sound.channel2.useLength ? core.sound.channel2.length : -1, core.sound.channel2.duty,
//                core.sound.channel2.envelopeInitial, core.cycle);
//        if(Math.abs(n-period) > 64) period = n;


        /**
         * period=14272, length=16384, duty=1
         * period=14304, length=16384, duty=1
         */

//        int diff = Math.abs(period - newPeriod);

//        if((newPeriod & 0xff) == 0x40) period = newPeriod;
//        if((newPeriod & 0x7f) == 0x20) period = newPeriod;

//        if (diff > 64)
//        {
//            period = newPeriod;
//            gbFreq = _gbFreq;
//        }
//        period = newPeriod;
//        period = newPeriod;
        //else System.out.println(gbFreq + " ---> " + _gbFreq + " with a sweep of " + envelopeSweep);


//        if(period == 12032) period = 12000;
//        if(period == 10688) period = 10720;
//        if(period == 8032) period = 80aaxaa00;
//        if(period == 14272) period = 14304;
//        if(period == 10112) period = 10080;
//                     0b100000
//        if((period & 0b111111) == 0b100000)
//         period = period & ~0b100000;
//        if((period & 0b1111111) == 0b1000000)
//         period = period & ~0b1000000;

//        int x= 0b10100111000000;


//        System.out.println(period);

        useLength = (registers[ioStart + 3] & 0x40) != 0;
    }

    boolean updateRequest = false;

    public void update()
    {
        updateRequest = true;
        requestedClockStart = core.cycle;
    }

    boolean restartRequest = false;
    long requestedClockStart;

    public void handleRestartRequest()
    {
        clockStart = core.cycle;
        restartRequest = false;
        lastCycle = -1;
    }

    boolean shortC;

    public void restart()
    {
        restartRequest = true;

        /*System.out.println("Note:");
        System.out.printf("Frequency: %d, %fHz, %d, %fHz\n", gbFreq, 131072.0 / (2048 - gbFreq), period, 4194304.0 / period);
        System.out.printf("Length: %d, %fs, %fs\n", length, length / 4194304.0, (64 - (core.mmu.registers[ioStart] & 0x3F)) / 256.);
        System.out.println();*/
    }

    int currentVolume = -1;

    int lastCycle = -1;

    @Override
    public int render()
    {
        int delta = (int) (core.cycle - clockStart);
        if (useLength && delta > length)
        {
            return 0;
        }

        if (currentVolume == -1) currentVolume = envelopeInitial;

        int amplitude;
        if (envelopeSweep == 0)
        {
            amplitude = currentVolume;//envelopeInitial;
        } else
        {
//            if ((delta / envelopeSweep) > 1)
//                System.out.println((delta / envelopeSweep) + " envelope cycles with delta=" + delta + ", envelopeSweep=" + envelopeSweep);
            currentVolume = amplitude = Math.min(15, Math.max(0, envelopeInitial + delta / envelopeSweep * (envelopeIncrease ? 1 : -1)));
            //new RuntimeException().printStackTrace();
        }

//        if(true) {
//            System.out.println(delta + "----" + envelopeSweep);
//        }

        int cycle = (delta * 8 / period) & 7;
        if(lastCycle == -1) lastCycle = cycle;

        int diff = (Math.abs(cycle - lastCycle));
//        if(diff > 1 && diff != 7) System.out.println("you .... " + diff);
        lastCycle = cycle;

        if (cycle == 0)
        {
            if (updateRequest) handleUpdateRequest();
            if (restartRequest)
            {
                handleRestartRequest();
            }
//            if(shortC)
//            {
//                shortC = false;
//                return 200;
//            }
        }
        /**
         * Duty   Waveform    Ratio  Cycle
         * -------------------------------
         * 0      00000001    12.5%      6
         * 1      10000001    25%        5
         * 2      10000111    50%        3
         * 3      01111110    75%        1
         */
        switch (duty)
        {
            case 0:
                return (byte) (cycle == 7 ? amplitude * 2 : 0);
            case 1:
                return (byte) (cycle == 0 || cycle == 7 ? amplitude * 2 : 0);
            case 2:
                return (byte) (cycle == 0 || cycle > 4 ? amplitude * 2 : 0);
            case 3:
                return (byte) (cycle != 0 && cycle != 7 ? amplitude * 2 : 0);
        }
        return -1;

//        return cycle > (duty-1) ? 2 * amplitude : 0;
    }

//    public void render(byte[] output, int off, int len)
//    {
//        float samplePeriod = core.clockSpeed / AUDIO_FORMAT.getSampleRate();
//
//        for (int i = 0; i < len; ++i)
//        {
//            int clock = clockStart + (int) (i * samplePeriod);
//            if (useLength && clock > length)
//                break;
//            int volume;
//            if (envelopeSweep == 0)
//                volume = envelopeInitial;
//            else
//                volume = Math.min(15, Math.max(0, envelopeInitial + clock / envelopeSweep * (envelopeIncrease ? 1 : -1))) * 2;
//            int cycle = (clock * 8 / period) & 7;
//
//            /**
//             * Duty   Waveform    Ratio  Cycle
//             * -------------------------------
//             * 0      00000001    12.5%      6
//             * 1      10000001    25%        5
//             * 2      10000111    50%        3
//             * 3      01111110    75%        1
//             */
//            output[off+i] += (byte) (cycle > duty ? volume : -volume);
//            /*switch (duty) {
//                case 0:
//                    output[i] = (byte) (cycle == 7 ? volume : -volume);
//                    break;
//                case 1:
//                    output[i] = (byte) (cycle == 0 || cycle == 7 ? volume : -volume);
//                    break;
//                case 2:
//                    output[i] = (byte) (cycle == 0 || cycle > 4 ? volume : -volume);
//                    break;
//                case 3:
//                    output[i] = (byte) (cycle != 0 && cycle != 7 ? volume : -volume);
//                    break;
//            }*/
//        }
//        clockStart += len * samplePeriod;
//    }

    public static void main(String... args) throws LineUnavailableException, InterruptedException
    {
        SourceDataLine sdl = AudioSystem.getSourceDataLine(AUDIO_FORMAT);
        byte[] data = new byte[48000];

        SquareWaveChannel channel = new SquareWaveChannel(null, 0, false);
        channel.period = 4096;
        channel.length = 1048576;
        channel.useLength = false;
        channel.clockStart = 0;
        channel.envelopeInitial = 15;
        channel.envelopeIncrease = true;
        channel.envelopeSweep = 7 * 65536;
        channel.duty = 3;
        //channel.render(data, 0, data.length);

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
