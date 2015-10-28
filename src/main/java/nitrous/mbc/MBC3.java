package nitrous.mbc;

import nitrous.Emulator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.lang.Math.max;

public class MBC3 extends MBC
{
    private int ramBank;
    private boolean rtcEnabled;
    private byte[] rtc = new byte[4];

    public MBC3(Emulator core)
    {
        super(core);
        cartRam = new byte[RAM_PAGESIZE * 4];
    }

    @Override
    public boolean hasBattery()
    {
        return true;
    }

    @Override
    public void save(OutputStream out) throws IOException
    {
        out.write(cartRam);
    }

    @Override
    public void load(InputStream in) throws IOException
    {
        while (in.read(cartRam) != -1) ;
    }

    @Override
    public void setAddress(int addr, int _data)
    {
        addr &= 0xffff;
        byte data = (byte) (_data & 0xff);
        switch (addr & 0xF000)
        {
            case 0x0000:
            case 0x1000:
                /**
                 * Mostly the same as for MBC1, a value of 0Ah will enable reading and writing to external
                 * RAM - and to the RTC Registers! A value of 00h will disable either.
                 */
                if (core.cartridge.ramBanks != 0)
                    ramEnabled = (data & 0x0F) == 0x0A;
                rtcEnabled = (data & 0x0F) == 0x0A;
                break;
            case 0x2000:
            case 0x3000:
                /**
                 * Same as for MBC1, except that the whole 7 bits of the RAM Bank Number are written directly to this
                 * address. As for the MBC1, writing a value of 00h, will select Bank 01h instead.
                 * All other values 01-7Fh select the corresponding ROM Banks.
                 */
                int bank = max((data & 0x7F), 1);
                //     bank &= core.cartridge.romBanks - 1;
                romPageStart = Memory.ROM_PAGESIZE * bank;
                break;
            case 0x4000:
            case 0x5000:
                /**
                 * As for the MBC1s RAM Banking Mode, writing a value in range for 00h-03h maps the corresponding
                 * external RAM Bank (if any) into memory at A000-BFFF. When writing a value of 08h-0Ch, this will
                 * map the corresponding RTC register into memory at A000-BFFF. That register could then be read/written
                 * by accessing any address in that area, typically that is done by using address A000.
                 */
                // TODO: allow writing to rtc
                if ((data >= 0x08) && (data <= 0x0C))
                {
                    // RTC
                    if (rtcEnabled)
                    {
                        ramBank = -1;
                    }
                } else if (data <= 0x03)
                {
                    ramBank = data;
                    //  ramBank &= core.cartridge.ramBanks - 1;
                    ramPageStart = ramBank * RAM_PAGESIZE;
                }
                break;
            case 0xA000:
            case 0xB000:
                /**
                 * Depending on the current Bank Number/RTC Register selection (see above), this memory space is used
                 * to access an 8KByte external RAM Bank, or a single RTC Register.
                 */
                if (ramEnabled && ramBank >= 0)
                {
                    cartRam[addr - 0xA000 + ramPageStart] = data;
                } else if (rtcEnabled)
                {
                    rtc[ramBank - 0x08] = data;
                }
                break;
            default:
                super.setAddress(addr, _data);
                break;
        }
    }
}
