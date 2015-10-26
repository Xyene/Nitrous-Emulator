package nitrous.mbc;

import nitrous.Emulator;

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
