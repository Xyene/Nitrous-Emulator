package nitrous.mbc;

import nitrous.cpu.Emulator;

import java.io.IOException;
import java.io.OutputStream;

public class MBC5 extends MBC {
    private int romBank = 1;

    public MBC5(Emulator core) {
        super(core);
        cartRam = new byte[RAM_PAGESIZE * 16];
    }

    @Override
    public boolean hasBattery() {
        return true;
    }

    @Override
    public void save(OutputStream out) throws IOException {
        out.write(cartRam);
    }

    /**
     * Maps a ROM bank to be accessed.
     *
     * @param bank The bank number.
     */
    private void mapRom(int bank) {
        romBank = bank;
        romPageStart = Memory.ROM_PAGESIZE * bank;
    }

    @Override
    public void setAddress(int addr, int _data) {
        addr &= 0xffff;
        byte data = (byte) (_data & 0xff);
        switch (addr & 0xF000) {
            case 0x0000:
            case 0x1000:
                /**
                 * Same as for MBC1.
                 */
                if (core.cartridge.ramBanks > 0)
                    ramEnabled = (data & 0x0F) == 0x0A;
                break;
            case 0xA000:
            case 0xB000:
                if (ramEnabled) {
                    cartRam[addr - 0xA000 + ramPageStart] = data;
                }
                break;
            case 0x2000:
                /**
                 * The lower 8 bits of the ROM bank number goes here.
                 * Writing 0 will indeed give bank 0 on MBC5, unlike other MBCs.
                 */
                mapRom((romBank & 0b100000000) | (data & 0xff));
                break;
            case 0x3000:
                /**
                 * The 9th bit of the ROM bank number goes here.
                 */
                mapRom((romBank & 0xff) | ((data & 0x1) << 8));
                break;
            case 0x4000:
            case 0x5000:
                ramPageStart = (data & 0x03) * RAM_PAGESIZE;
                break;
            default:
                super.setAddress(addr, data);
                break;
        }
    }
}
