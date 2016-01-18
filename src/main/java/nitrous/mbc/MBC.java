package nitrous.mbc;

import nitrous.cpu.Emulator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Abstract implementation of features shared by all Memory Banking Chips.
 *
 * @author Tudor
 */
public abstract class MBC extends Memory
{
    /**
     * The size of a page of cart RAM, 8k in size.
     */
    public static final int RAM_PAGESIZE = 0x2000;

    /**
     * The current offset (page) into cart ram.
     */
    protected int ramPageStart;

    /**
     * Whether or not accessing RAM is currently enabled.
     */
    protected boolean ramEnabled;

    /**
     * Raw cart ram.
     */
    protected byte[] cartRam;

    /**
     * Default constructor for all extending classes.
     *
     * @param core The Emulator to operate on.
     */
    MBC(Emulator core)
    {
        super(core);
    }//end MBC(core)

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(InputStream in) throws IOException
    {
        if(!hasBattery()) throw new IllegalStateException("no battery!");
        int read = 0;
        while (true)
        {
            int n = in.read(cartRam, read, cartRam.length);
            if (n == -1) break;
            read += n;
        }//end while
        if (read != cartRam.length) throw new IOException("cart data invalid");
    }//end load

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(OutputStream out) throws IOException
    {
        if(!hasBattery()) throw new IllegalStateException("no battery!");
        out.write(cartRam);
    }//end save

    /**
     * {@inheritDoc}
     */
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
                    // Return an invalid value
                    return 0xff;
                }//end if
        }//end switch
        return super.getAddress(addr);
    }//end getAddress
}//end class MBC
