package nitrous.mbc;

import nitrous.Emulator;

import java.io.IOException;
import java.io.OutputStream;

public class MBC5 extends MBC
{
    private int romBank = 1;

    public MBC5(Emulator core)
    {
        super(core);
        cartRam = new byte[RAM_PAGESIZE * 16];
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
    public void setAddress(int addr, int _data)
    {
        addr &= 0xffff;
        byte data = (byte) (_data & 0xff);
        switch (addr & 0xF000)
        {
            case 0x0000:
            case 0x1000:
                /**
                 * Before external RAM can be read or written, it must be enabled by writing to this address space.
                 * It is recommended to disable external RAM after accessing it, in order to protect its contents
                 * from damage during power down of the gameboy. Usually the following values are used:
                 *
                 * 00h  Disable RAM (default)
                 * 0Ah  Enable RAM
                 *
                 * Practically any value with 0Ah in the lower 4 bits enables RAM, and any other value disables RAM.
                 */
                if (core.cartridge.ramBanks > 0)
                    ramEnabled = (data & 0x0F) == 0x0A;
                break;
            case 0xA000:
            case 0xB000:
                /**
                 * This area is used to address external RAM in the cartridge (if any).
                 * External RAM is often battery buffered, allowing to store game positions or high score tables,
                 * even if the gameboy is turned off, or if the cartridge is removed from the gameboy.
                 * Available RAM sizes are: 2KByte (at A000-A7FF), 8KByte (at A000-BFFF), and 32KByte
                 * (in form of four 8K banks at A000-BFFF).
                 */
                if (ramEnabled)
                {
                    cartRam[addr - 0xA000 + ramPageStart] = data;
                }
                break;
            case 0x2000:
            case 0x3000:
                if (addr < 0x3000)
                {
                    romBank = data | (currentRomBankHi << 8);
                } else
                {
                    currentRomBankHi = data & 0x01;
                    romBank = (romBank & 0xFF) | (currentRomBankHi << 8);
                }
                // romBank &= (core.cartridge.romBanks - 1);
                romPageStart = romBank * 0x4000;
                break;
            case 0x4000:
            case 0x5000:
                ramPageStart = (data & 0x0F) * RAM_PAGESIZE;
                break;
            case 0x6000:
            case 0x7000:
                System.err.println("Invalid write!");
                break;
            default:
                super.setAddress(addr, data);
                break;
        }
    }

    int currentRomBankHi;
}
