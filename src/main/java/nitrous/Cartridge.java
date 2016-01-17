package nitrous;

import nitrous.cpu.Emulator;
import nitrous.mbc.*;

import java.nio.charset.StandardCharsets;

/**
 * An internal information area located at 0100-014F in each cartridge.
 */
public class Cartridge
{
    public final String gameTitle;
    public final boolean isColorGB, isSuperGB;

    // might be wrong!
    public static final String[] CARTRIDGE_TYPES = {
    /* 0x00 */  "ROM ONLY",
    /* 0x01 */  "ROM+MBC1",
    /* 0x02 */  "ROM+MBC1+RAM",
    /* 0x03 */  "ROM+MBC1+RAM+BATT",
    /* 0x04 */  null,
    /* 0x05 */  "ROM+MBC2",
    /* 0x06 */  "ROM+MBC2+BATTERY",
    /* 0x07 */  null,
    /* 0x08 */  "ROM+RAM",
    /* 0x09 */  "ROM+RAM+BATTERY",
    /* 0x0a */  null,
    /* 0x0b */  "ROM+MMM01",
    /* 0x0c */  "ROM+MMM01+SRAM",
    /* 0x0d */  "ROM+MMM01+SRAM+BATT",
    /* 0x0e */  "ROM+MMM01+TIMER+BATT",
    /* 0x0f */  "ROM+MBC3+TIMER+BATT",
    /* 0x10 */  "ROM+MBC3+TIMER+RAM+BATT",
    /* 0x11 */  "ROM+MBC3",
    /* 0x12 */  "ROM+MBC3+RAM",
    /* 0x13 */  "ROM+MBC3+RAM+BAT",
    /* 0x14 */  null,
    /* 0x15 */  null,
    /* 0x16 */  null,
    /* 0x17 */  null,
    /* 0x18 */  null,
    /* 0x19 */  "ROM+MBC5",
    /* 0x1a */  "ROM+MBC5+RAM",
    /* 0x1b */  "ROM+MBC5+RAM+BATT",
    /* 0x1c */  "ROM+MBC5+RUMBLE",
    /* 0x1d */  "ROM+MBC5+RUMBLE+SRAM",
    /* 0x1e */  "ROM+MBC5+RUMBLE+SRAM+BATT",
    };
    public final byte cartridgeType;
    public final int romBanks;
    public final int ramBanks;
    public final boolean isJapanese;
    public final byte[] rom;
    public int checksum;

    public Cartridge(byte[] rom)
    {
        this.rom = rom;

        /**
         * 0143 - CGB Flag
         * In older cartridges this byte has been part of the Title (see above).
         * In CGB cartridges the upper bit is used to enable CGB functions. This is required,
         * otherwise the CGB switches itself into Non-CGB-Mode. Typical values are:
         *
         * 80h - Game supports CGB functions, but works on old gameboys also.
         * C0h - Game works on CGB only (physically the same as 80h).
         *
         * Values with Bit 7 set, and either Bit 2 or 3 set, will switch the gameboy into a special non-CGB-mode
         * with uninitialized palettes. Purpose unknown, eventually this has been supposed to be used to colorize
         * monochrome games that include fixed palette data at a special location in ROM.
         *
         * // TODO: special uninitialized palette mode
         */
        this.isColorGB = rom[0x0143] != 0;

        /**
         * 0134-0143 - Title
         * Title of the game in UPPER CASE ASCII. If it is less than 16 characters then the remaining bytes are
         * filled with 00's.
         *
         * When inventing the CGB, Nintendo has reduced the length of this area to 15 characters, and some months
         * later they had the fantastic idea to reduce it to 11 characters only.
         */
        String title = new String(rom, 0x134, isColorGB ? 11 : 16, StandardCharsets.US_ASCII);
        int stop = title.indexOf('\0');
        this.gameTitle = stop != -1 ? title.substring(0, stop) : title;

        /**
         * Specifies whether the game supports SGB functions, common values are:
         *
         * 00h = No SGB functions (Normal Gameboy or CGB only game)
         * 03h = Game supports SGB functions
         *
         * The SGB disables its SGB functions if this byte is set to another value than 03h.
         */
        this.isSuperGB = rom[0x0146] == 3;

        /**
         * Specifies which Memory Bank Controller (if any) is used in the cartridge, and if further
         * external hardware exists in the cartridge.
         */
        this.cartridgeType = rom[0x0147];

        /**
         * 0148 - ROM Size
         * Specifies the ROM Size of the cartridge. Typically calculated as "32KB shl N".
         *
         * 00h -  32KByte (no ROM banking)
         * 01h -  64KByte (4 banks)
         * 02h - 128KByte (8 banks)
         * 03h - 256KByte (16 banks)
         * 04h - 512KByte (32 banks)
         * 05h -   1MByte (64 banks)  - only 63 banks used by MBC1
         * 06h -   2MByte (128 banks) - only 125 banks used by MBC1
         * 07h -   4MByte (256 banks)
         * 52h - 1.1MByte (72 banks)
         * 53h - 1.2MByte (80 banks)
         * 54h - 1.5MByte (96 banks)
         */
        byte romSize = rom[0x0148];
        switch (romSize)
        {
            case 52:
                this.romBanks = 72;
                break;
            case 53:
                this.romBanks = 80;
                break;
            case 54:
                this.romBanks = 96;
                break;
            default:
                this.romBanks = (int) Math.pow(2, romSize + 1);
        }
        if (rom.length != 16384 * romBanks) throw new AssertionError();

        /**
         * 0149 - RAM Size
         * Specifies the size of the external RAM in the cartridge (if any).
         *
         * 00h - None
         * 01h - 2 KBytes
         * 02h - 8 Kbytes
         * 03h - 32 KBytes (4 banks of 8KBytes each)
         */
        this.ramBanks = new int[]{0, 1, 1, 4, 16}[rom[0x0149]];

        /**
         * 014A - Destination Code
         * Specifies if this version of the game is supposed to be sold in Kapan, or anywhere else.
         * Only two values are defined.
         *
         * 0h - Japanese
         * 1h - Non-Japanese
         */
        this.isJapanese = rom[0x014A] == 0;

        /**
         * 014D - Header Checksum
         * Contains an 8 bit checksum across the cartridge header bytes 0134-014C. The checksum is calculated as follows:
         *
         * x=0:FOR i=0134h TO 014Ch:x=x-MEM[i]-1:NEXT
         *
         * We use this to simulate the CGB boot rom to colorize some monochrome games.
         */

//        this.checksum = 0x14;// rom[0x014d] & 0xff;
    }

    public boolean hasBattery()
    {
        // MMM01 not included here, but we don't support it anyway
        switch (cartridgeType)
        {
            case 0x03: // ROM+MBC1+RAM+BATT
            case 0x09: // ROM+RAM+BATTERY
            case 0x1B: // ROM+MBC5+RAM+BATT
            case 0x1E: // ROM+MBC5+RUMBLE+SRAM+BATT
            case 0x10: // ROM+MBC3+TIMER+RAM+BATT
            case 0x13: // ROM+MBC3+RAM+BAT
            case 0x06: // ROM+MBC2+BATTERY
                // 8 KB ram banks
                return true;
            default:
                return false;
        }
    }

    public Memory createController(Emulator core)
    {
        switch (cartridgeType)
        {
            case 0x00:
                return new Memory(core);
            case 0x01:
            case 0x02:
            case 0x03:
                return new MBC1(core);
            case 0x05:
            case 0x06:
                return new MBC2(core);
            case 0x10:
            case 0x11:
            case 0x12:
            case 0x13:
                return new MBC3(core);
            case 0x1b:
                return new MBC5(core);

        }
        throw new UnsupportedOperationException("unsupported controller " + CARTRIDGE_TYPES[cartridgeType]);
    }

    @Override
    public String toString()
    {
        return "Cartridge{" +
                "gameTitle='" + gameTitle + '\'' +
                ", checksum=" + Integer.toHexString(checksum) +
                ", isColorGB=" + isColorGB +
                ", isSuperGB=" + isSuperGB +
                ", isJapanese=" + isJapanese +
                ", cartridgeType=" + CARTRIDGE_TYPES[cartridgeType] + " (" + Integer.toHexString(cartridgeType) + ")" +
                ", romBanks=" + romBanks +
                ", ramBanks=" + ramBanks + ")" +
                '}';
    }
}
