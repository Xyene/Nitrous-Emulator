package nitrous.mbc;

import nitrous.cpu.Emulator;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Implementation of Memory Bank Chip 1.
 *
 * @author Tudor
 */
public class MBC1 extends MBC
{
    /**
     * Flag of whether to access RAM (0) or ROM (1) from 5000h-6000h.
     */
    private int modeSelect;

    /**
     * Currently mapped ROM bank.
     */
    private int romBank = 1;

    /**
     * {@inheritDoc}
     */
    public MBC1(Emulator core)
    {
        super(core);
        cartRam = new byte[RAM_PAGESIZE * 4];
    }

    /**
     * Maps a ROM bank to be accessed.
     *
     * @param bank The bank number.
     */
    private void mapRom(int bank)
    {
        /**
         * Banks $00, $20, $40, and $60 are not usable, and redirect to the next bank.
         */
        if (bank == 0x00 || bank == 0x20 || bank == 0x40 || bank == 0x60)
            bank++;
        romBank = bank;
        romPageStart = Memory.ROM_PAGESIZE * bank;
    }

    /**
     * {@inheritDoc}
     */
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

                /**
                 * Writing to this address space selects the lower 5 bits of the ROM Bank Number (in range 01-1Fh).
                 * When 00h is written, the MBC translates that to bank 01h also. That doesn't harm [anything]
                 * so far, because ROM Bank 00h can be always directly accessed by reading from 0000-3FFF.
                 * But (when using the register below to specify the upper ROM Bank bits), the same happens for
                 * Bank 20h, 40h, and 60h. Any attempt to address these ROM Banks will select Bank 21h, 41h, and 61h instead.
                 */
                mapRom((romBank & 0x60) | (data & 0x1F));
                break;
            case 0x4000:
            case 0x5000:

                /**
                 * 2bit register can be used to select a RAM Bank in range from 00-03h, or to specify the
                 * upper two bits (Bit 5-6) of the ROM Bank number, depending on the current ROM/RAM Mode. (See below.)
                 */
                if (modeSelect == 0)
                {
                    ramPageStart = (data & 0x03) * RAM_PAGESIZE;
                } else
                {
                    mapRom((romBank & 0x1F) | ((data & 0x03) << 4));
                }
                break;
            case 0x6000:
            case 0x7000:

                /**
                 * This 1bit Register selects whether the two bits of the above register
                 * should be used as upper two bits of the ROM Bank, or as RAM Bank Number.
                 *
                 * 00h = ROM Banking Mode (up to 8KByte RAM, 2MByte ROM) (default)
                 * 01h = RAM Banking Mode (up to 32KByte RAM, 512KByte ROM)
                 */
                if (core.cartridge.ramBanks == 3)
                    modeSelect = (data & 0x01);
                break;
            default:
                super.setAddress(addr, data);
                break;
        }
    }
}
