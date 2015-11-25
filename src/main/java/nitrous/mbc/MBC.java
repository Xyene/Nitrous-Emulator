package nitrous.mbc;

import nitrous.Emulator;

import java.io.IOException;
import java.io.InputStream;

public abstract class MBC extends Memory
{
    public static final int RAM_PAGESIZE = 0x2000;
    protected int ramPageStart;
    protected boolean ramEnabled;
    protected byte[] cartRam;

    public MBC(Emulator core)
    {
        super(core);
    }

    @Override
    public void load(InputStream in) throws IOException
    {
        int read = 0;
        while (true)
        {
            int n = in.read(cartRam, read, cartRam.length);
            if (n == -1) break;
            read += n;
        }
        if (read != cartRam.length) throw new IOException("cart data invalid");
    }


    @Override
    public short getAddress(int addr)
    {
        addr &= 0xffff;
        switch (addr & 0xF000)
        {
            case 0xA000:
            case 0xB000:
                if (ramEnabled)
                {
                    return cartRam[addr - 0xA000 + ramPageStart];
                } else
                {
                    return 0xff;
                }
        }
        return super.getAddress(addr);
    }
}
