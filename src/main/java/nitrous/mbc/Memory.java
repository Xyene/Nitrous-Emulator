package nitrous.mbc;

import nitrous.Emulator;
import nitrous.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Represents the memory of a Gameboy.
 * <p>
 * The Gameboy has a 16-bit address bus, allowing it to use $0000-FFFF. Some parts of this memory range
 * are switchable, and may either be handled by the Gameboy (e.g., work ram on the Gameboy Color), or the
 * game cartridge (e.g., ROM banking).
 * </p>
 * General Memory Map
 * 0000-3FFF   16KB ROM Bank 00     (in cartridge, fixed at bank 00)
 * 4000-7FFF   16KB ROM Bank 01..NN (in cartridge, switchable bank number)
 * 8000-9FFF   8KB Video RAM (VRAM) (switchable bank 0-1 in CGB Mode)
 * A000-BFFF   8KB External RAM     (in cartridge, switchable bank, if any)
 * C000-CFFF   4KB Work RAM Bank 0 (WRAM)
 * D000-DFFF   4KB Work RAM Bank 1 (WRAM)  (switchable bank 1-7 in CGB Mode)
 * E000-FDFF   Same as C000-DDFF (ECHO)    (typically not used)
 * FE00-FE9F   Sprite Attribute Table (OAM)
 * FEA0-FEFF   Not Usable
 * FF00-FF7F   I/O Ports
 * FF80-FFFE   High RAM (HRAM)
 * FFFF        Interrupt Enable Register
 */
public class Memory
{
    /**
     * Size of a page of Video RAM, in bytes. 8kb.
     */
    public static final int VRAM_PAGESIZE = 0x2000;
    /**
     * Size of a page of Work RAM, in bytes. 4kb.
     */
    public static final int WRAM_PAGESIZE = 0x1000;
    /**
     * Size of a page of ROM, in bytes. 16kb.
     */
    public static final int ROM_PAGESIZE = 0x4000;
    /**
     * Register values, mapped from $FF00-$FF7F + HRAM ($FF80-$FFFE) + Interrupt Enable Register ($FFFF)
     */
    public final byte[] registers = new byte[0x100];
    /**
     * Sprite Attribute Table, mapped from $FE00-$FE9F.
     */
    public final byte[] oam = new byte[0xA0];
    /**
     * Video RAM, mapped from $8000-$9FFF.
     * <p>
     * On the GBC, this bank is switchable 0-1 by writing to $FF4F.
     */
    public final byte[] vram;
    /**
     * Work RAM, mapped from $C000-$CFFF and $D000-$DFFF.
     * <p>
     * On the GBC, this bank is switchable 1-7 by writing to $FF07.
     */
    public final byte[] wram;

    /**
     * The current page of Video RAM, always multiples of Memory.VRAM_PAGESIZE.
     * <p>
     * On non-GBC, this is always 0.
     */
    public int vramPageStart = 0;
    /**
     * The current page of Work RAM, always multiples of Memory.WRAM_PAGESIZE.
     * <p>
     * On non-GBC, this is always Memory.VRAM_PAGESIZE.
     */
    public int wramPageStart = WRAM_PAGESIZE;
    /**
     * The current page of ROM, always multiples of Memory.ROM_PAGESIZE.
     */
    public int romPageStart = ROM_PAGESIZE;
    /**
     * Reference to the main Emulator instance.
     */
    protected final Emulator core;

    /**
     * Instantiate a Memory instance.
     *
     * @param core the Emulator to bind to.
     */
    public Memory(Emulator core)
    {
        this.core = core;
        wram = new byte[WRAM_PAGESIZE * (core.cartridge.isColorGB ? 8 : 2)];
        vram = new byte[VRAM_PAGESIZE * (core.cartridge.isColorGB ? 2 : 1)];
    }

    /**
     * Convenience method for determining whether the current cartridge supports saving (i.e., whether or not
     * it has a battery).
     *
     * @return true if it does, false otherwise.
     */
    public boolean hasBattery()
    {
        return core.cartridge.hasBattery();
    }

    /**
     * Writes cart ram to the given OutputStream.
     *
     * @param out where to write raw cart ram.
     * @throws IOException                   if something goes wrong.
     * @throws UnsupportedOperationException if this cart has no battery.
     */
    public void save(OutputStream out) throws IOException
    {
        throw new UnsupportedOperationException("no battery");
    }

    public HDMA hdma;

    public class HDMA
    {
        private final int source;
        private final int dest;
        private int length;
        private int ptr;

        public HDMA(int source, int dest, int length)
        {
            this.source = source;
            this.dest = dest;
            this.length = length;
        }

        /**
         * Ticks DMA.
         */
        public void tick()
        {
            /**
             * The H-Blank DMA transfers 10h bytes of data during each H-Blank, ie. at LY=0-143, no data is transferred
             * during V-Blank (LY=144-153), but the transfer will then continue at LY=00. The execution of the program
             * is halted during the separate transfers, but the program execution continues during the 'spaces'
             * between each data block.
             *
             * Note that the program may not change the Destination VRAM bank (FF4F), or the Source ROM/RAM bank
             * (in case data is transferred from bankable memory) until the transfer has completed!
             * Reading from Register FF55 returns the remaining length (divided by 10h, minus 1), a value of 0FFh
             * indicates that the transfer has completed. It is also possible to terminate an active H-Blank transfer
             * by writing zero to Bit 7 of FF55. In that case reading from FF55 may return any value for the
             * lower 7 bits, but Bit 7 will be read as "1".
             */
            for (int i = ptr; i < ptr + 0x10; i++)
            {
                vram[vramPageStart + dest + i] = (byte) (getAddress(source + i) & 0xff);
            }
            core.cycle += 8;
            core.ac += 8;
            core.executed += 8;
            ptr += 0x10;
            length -= 0x10;
            System.err.printf("Ticked HDMA from %04X-%04X, %02X remaining\n", source, dest, length);
            if (length == 0)
            {
                Memory.this.hdma = null;
                registers[0x55] = (byte) 0xff;
                System.err.printf("Finished HDMA from %04X-%04X\n", source, dest);
            } else
            {
                registers[0x55] = (byte) (length / 0x10 - 1);
            }
        }
    }


    /**
     * Loads cart ram from the given InputStream.
     *
     * @param in where to read raw cart ram from.
     * @throws IOException                   if something goes wrong.
     * @throws UnsupportedOperationException if this cart has no battery, and hence no cart ram.
     */
    public void load(InputStream in) throws IOException
    {
        throw new UnsupportedOperationException("no battery");
    }

    public void setAddress(int addr, int _data)
    {
        byte data = (byte) (_data & 0xff);
        addr &= 0xFFFF;
        int block = addr & 0xF000;
        switch (block)
        {
            case 0x0000:
            case 0x1000:
            case 0x2000:
            case 0x3000:
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                // handled by external hardware, if any
                break;
            case 0x8000:
            case 0x9000:
                vram[vramPageStart + addr - 0x8000] = data;
                break;
            case 0xA000:
            case 0xB000:
                // handled by external hardware, if any
                break;
            case 0xC000:
                wram[addr - 0xC000] = data;
                break;
            case 0xD000:
                wram[wramPageStart + addr - 0xD000] = data;
                break;
            case 0xE000:
            case 0xF000:
                //  FEA0-FEFF is not usable
                if (0xFEA0 <= addr && addr <= 0xFEFF) break;
                if (addr < 0xFE00)
                {
                    // 7.5kb echo
                    setAddress(addr - 0xE000, data);
                } else if (addr < 0xFF00)
                {
                    oam[addr - 0xFE00] = data;
                } else
                {
                    setIO(addr - 0xFF00, data);
                }
                break;
        }
    }

    public void setIO(int addr, int data)
    {
//        if(addr == R.R_SCX) {
//            if(data == 0) return;//new IOException().printStackTrace();
//            core.lcd.scrollx = data;
//        }
        switch (addr)
        {
            case 0x69:
            {
                if (!core.cartridge.isColorGB) break;
                int ff68 = registers[0x68];
                int currentRegister = ff68 & 0x3f;
                core.lcd.setBackgroundPalette(currentRegister, data);
                if ((ff68 & 0x80) != 0)
                {
                    currentRegister++;
                    currentRegister %= 0x40;
                    registers[0x68] = (byte) (0x80 | currentRegister);
                }
                break;
            }
            case 0x6b:
            {
                if (!core.cartridge.isColorGB) break;
                int ff6a = registers[0x6a];
                int currentRegister = ff6a & 0x3f;
                core.lcd.setSpritePalette(currentRegister, data);
                if ((ff6a & 0x80) != 0)
                {
                    currentRegister++;
                    currentRegister %= 0x40;
                    registers[0x6a] = (byte) (0x80 | currentRegister);
                }
                break;
            }
            case 0x55: // HDMA start
            {
                if (!core.cartridge.isColorGB) break;
                int length = ((data & 0x7f) + 1) * 0x10;
                int source = ((registers[0x51] & 0xff) << 8) | (registers[0x52] & 0xF0);
                int dest = ((registers[0x53] & 0x1f) << 8) | (registers[0x54] & 0xF0);
                if ((data & 0x80) != 0)
                {
                    // H-Blank DMA
                    hdma = new HDMA(source, dest, length);
                    registers[0x55] = (byte) (length / 0x10 - 1);
                    break;
                } else
                {
                    if (hdma != null)
                    {
                        System.err.printf("!!! Terminated HDMA from %04X-%04X, %02X remaining\n", source, dest, length);
                        hdma = null;
                        registers[0x55] = (byte) 0x80;
                        break;
                    }
                    // General DMA
                    for (int i = 0; i < length; i++)
                    {
                        vram[vramPageStart + dest + i] = (byte) (getAddress(source + i) & 0xff);
                    }
                    registers[0x55] = (byte) 0xFF;
                }
                break;
            }
            case R.R_VRAM_BANK:
            {
                if (core.cartridge.isColorGB)
                {
                    vramPageStart = VRAM_PAGESIZE * (data & 0x3);
                    //  System.err.println("Selected vram bank " + (data & 0x3));
                }
                break;
            }
            case R.R_WRAM_BANK:
            {
                if (core.cartridge.isColorGB)
                {
                    wramPageStart = WRAM_PAGESIZE * Math.max(1, data & 0x7);
                }
                break;
            }
            case R.R_NR14:
                if ((registers[R.R_NR14] & 0x80) != 0) {
                    core.sound.channel1.enabled = true;
                    core.sound.channel1.update();
                }
                break;
            case R.R_NR24:
                if ((registers[R.R_NR24] & 0x80) != 0) {
                    core.sound.channel2.enabled = true;
                    core.sound.channel2.update();
                }
                break;
            case 0x1a: // Channel 3 sound on/off
//                System.err.print("Channel 3 sound: ");
//                if ((data & 0x80) != 0)
//                {
//                    System.err.print("on");
//                    // Channel3.start();
//                } else
//                {
//                    System.err.print("off");
//                    // Channel3.stop();
//                }
//                System.err.println();
                break;
            /**
             * Writing to this register launches a DMA transfer from ROM or RAM to OAM memory (sprite attribute table).
             * The written value specifies the transfer source address divided by 100h, ie. source & destination are:
             *
             *   Source:      XX00-XX9F   ;XX in range from 00-F1h
             *   Destination: FE00-FE9F
             *
             * http://hitmen.c02.at/files/releases/gbc/gbc_dma_transfers.txt
             *
             * length:      - always 4*40 (=160 / $a0) bytes
             */
            case R.R_DMA:
            {
                int addressBase = data * 0x100;

                for (int i = 0; i < 0xA0; i++)
                {
                    setAddress(0xFE00 + i, getAddress(addressBase + i));
                }
                break;
            }

            /**
             * DIV
             * Divider Register (R/W)
             *
             * This register is incremented 16384 times a second.
             * Writing any value sets it to $00
             */
            case R.R_DIV:
                data = 0;
                break;
            /**
             * Bit 2    - Timer Stop  (0=Stop, 1=Start)
             * Bits 1-0 - Input Clock Select
             * 00:   4096 Hz    (~4194 Hz SGB)
             * 01: 262144 Hz  (~268400 Hz SGB)
             * 10:  65536 Hz   (~67110 Hz SGB)
             * 11:  16384 Hz   (~16780 Hz SGB)
             */
            case R.R_TAC:
                System.err.println("Timer ctrl - " + core.cycle);
                if (((data >> 2) & 0b1) == 1)
                {
                    System.err.println("Enabled timer");
                    core.timerEnabled = true;
                } else
                {
                    core.timerEnabled = false;
                    System.err.println("Disabled timer");
                    core.timerCycle = 0;
                }
                switch (addr & 0b11)
                {
                    case 0b00:
                        core.timerFreq = 4096;
                        break;
                    case 0b01:
                        core.timerFreq = 262144;
                        break;
                    case 0b10:
                        core.timerFreq = 65536;
                        break;
                    case 0b11:
                        core.timerFreq = 16384;
                        break;
                }
                break;
            case R.R_LCD_STAT:
                break;
        }
        registers[addr] = (byte) data;
    }

    public short getAddress(int addr)
    {
        short ret = _mapMemory(addr);
        // System.out.printf("memread %04X=%02X\n", addr, ret & 0xFF);
        return ret;
    }

    public short _mapMemory(int addr)
    {
        addr &= 0xFFFF;
        int block = addr & 0xF000;
        switch (block)
        {
            case 0x0000:
            case 0x1000:
            case 0x2000:
            case 0x3000:
                return core.cartridge.rom[addr];
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                return core.cartridge.rom[romPageStart + addr - 0x4000];
            case 0x8000:
            case 0x9000:
                return vram[vramPageStart + addr - 0x8000];
            case 0xA000:
            case 0xB000:
                return 0;
            case 0xC000:
                return wram[addr - 0xc000];
            case 0xD000:
                return wram[wramPageStart + addr - 0xd000];
            case 0xE000:
            case 0xF000:
                //  FEA0-FEFF is not usable
                if (0xFEA0 <= addr && addr <= 0xFEFF) return 0xFF;
                if (addr < 0xFE00)
                {
                    // E000-FE00 echoes the main ram
                    // But wait, E000-FE00 contains just 7.5kb and hence
                    // does not echo the entire 8kb internal ram
                    return getAddress(addr - 0xE000);
                } else if (addr < 0xFF00)
                {
                    return oam[addr - 0xFE00];
                } else
                {
                    return getIO(addr - 0xFF00);
                }
        }
        return 0xFF;
    }


    public short getIO(int addr)
    {
        short ret = _readReg(addr);
        // System.out.printf("IO READ %04X=%02X\n", addr&0xFFFF, (byte)ret);
        return ret;
    }

    public short _readReg(int addr)
    {
        addr &= 0xFFFF;
        switch (addr)
        {
            case 0x00: // JOYPAD (FIXME not done)
            {
                byte reg = registers[0x00];
                short output = 0x0F;
                switch ((reg & 0b110000) >> 4)
                {
                    case 1:
                        if (core.buttonA) output &= ~0x01;
                        if (core.buttonB) output &= ~0x02;
                        if (core.buttonSelect) output &= ~0x04;
                        if (core.buttonStart) output &= ~0x08;
                        break;
                    case 2:
                    case 3:
                        if (core.buttonRight) output &= ~0x1;
                        if (core.buttonLeft) output &= ~0x2;
                        if (core.buttonUp) output &= ~0x4;
                        if (core.buttonDown) output &= ~0x8;
                        break;
                }
                // keep the last 2 bits as-is, in case someone wrote to them
                // I'm not sure if this is correct, but if its not it probably doesn't matter
                return (short) ((0x30 | output | (reg & 0b1100000)) & 0xff);
            }
            case R.R_LCD_STAT:
                byte reg = 0;
                // Bit 2 - Coincidence Flag
                // 0: LYC not equal to LY
                // 1: LYC = LY
                if (registers[R.R_LY] == registers[R.R_LYC])
                {
                    reg |= 0x04;
                }
                // When 144 <= LY <= 153, we're in vblank
                if ((registers[R.R_LY] & 0xFF) > 144)
                {
                    reg |= 0x01;
                } else
                {
//                    System.out.println("!!!");
                    // FIXME
//                    long _cycle = core.cycle % R.INSTRS_PER_HBLANK;
//                    int section = R.INSTRS_PER_HBLANK / 6;
//                    if (_cycle < section * 3)
//                    {
//                        // We're in hblank
//                    } else if (_cycle <= section * 4)
//                    {
//                        // We're in OAM ram search
//                        reg |= 0x02;
//                    } else
//                    {
//                        // We're transferring data to LCD Driver
//                        reg |= 0x03;
//                    }
                }
                // Add whatever else might've been set in the other bits
                reg |= registers[R.R_LCD_STAT] & 0xF8;
                return reg;
        }
        return registers[addr];
    }
}
