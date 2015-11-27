package nitrous.sound;

public class Channel3
{
//    public static boolean soundOn;
//    public static int soundLength;
//
//    static SourceDataLine sdl;
//    static AudioFormat af;
//
//    static
//    {
//        af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 8, 2, 2, 44100, true);
//        try
//        {
//            sdl = AudioSystem.getSourceDataLine(af);
//            sdl.open(af, (44100 / 1000) * 200);
//            sdl.start();
//        } catch (LineUnavailableException e)
//        {
//            e.printStackTrace();
//        }
//    }
//
//    public static int cycle = 0;
//
//    public static void start()
//    {
//        byte[] wave = new byte[32];
//        for (int i = 0; i < 15; i++)
//        {
//            int hi = (MMU.registers[0x30+i] & 0xF0) >> 4;
//            int lo = (MMU.registers[0x30+i] & 0x0F);
//            wave[i * 2] = (byte) hi;
//            wave[i * 2 + 1] = (byte) lo;
//        }
//
//        int numSamples = Math.min(44100 / 14, sdl.available());
//
//        byte[] out = new byte[numSamples];
//        for (int i = 0; i < numSamples / 2; i++)
//        {
//            cycle++;
//            int val = wave[cycle % 32];
//            out[i * 2] += val;
//            out[i * 2+1] += val;
//            out[i] += val;
//        }
//        sdl.write(out, 0, out.length);
//    }
//
//    public static float getFrequency()
//    {
//        int x = ((MMU.registers[0x1E] & 0x7) << 8) | (MMU.registers[0x1d] & 0xff);
//        return 65536f / (2048 - x);
//    }
}
