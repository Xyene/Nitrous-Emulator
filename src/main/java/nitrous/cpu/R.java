package nitrous.cpu;

/**
 * A catch-all class for constants (generally register values) frequently used in emulation.
 *
 * @author Tudor
 */
public interface R
{
    /**
     * The DMG has 4 flag registers, zero, subtract, half-carry and carry.
     * Half-carry is only ever used for the DAA instruction. Half-carry is usually carry over lower nibble, and carry
     * is over bit 7.
     */
    short F_Z = 0x80;
    short F_N = 0x40;
    short F_H = 0x20;
    short F_C = 0x10;

    /**
     * Registers in FF00-FFFF
     */
    int R_DIV = 0x04;
    int R_JOYPAD = 0x00;
    int R_SERIAL = 0x02;
    int R_TAC = 0x07;
    int R_TIMA = 0x05;
    int R_TMA = 0x06;
    int R_NR10 = 0x10;
    int R_NR11 = 0x11;
    int R_NR12 = 0x12;
    int R_NR13 = 0x13;
    int R_NR14 = 0x14;
    int R_NR21 = 0x16;
    int R_NR22 = 0x17;
    int R_NR23 = 0x18;
    int R_NR24 = 0x19;
    int R_NR30 = 0x1A;
    int R_NR31 = 0x1B;
    int R_NR32 = 0x1C;
    int R_NR33 = 0x1D;
    int R_NR34 = 0x1E;
    int R_NR41 = 0x20;
    int R_NR42 = 0x21;
    int R_NR43 = 0x22;
    int R_NR44 = 0x23;
    int R_NR51 = 0x25;
    int R_NR52 = 0x26;
    int R_WRAM_BANK = 0x70;
    int R_VRAM_BANK = 0x4f;
    int R_LCDC = 0x40;
    int R_LCD_STAT = 0x41;
    int R_SCY = 0x42;
    int R_SCX = 0x43;
    int R_LY = 0x44;
    int R_LYC = 0x45;
    int R_BGP = 0x47;
    int R_OBP0 = 0x48;
    int R_OBP1 = 0x49;
    int R_TRIGGERED_INTERRUPTS = 0x0F; // IF
    int R_ENABLED_INTERRUPTS = 0xFF;
    int R_DMA = 0x46;
    int R_WY = 0x4a;
    int R_WX = 0x4b;

    /**
     * Masks for R_TRIGGERED_INTERRUPTS and R_ENABLED_INTERRUPTS.
     */
    int VBLANK_BIT = 0x1;
    int LCDC_BIT = 0x2;
    int TIMER_OVERFLOW_BIT = 0x4;
    int SERIAL_TRANSFER_BIT = 0x8;
    int HILO_BIT = 0x10;

    /**
     * The addresses to jump to when an interrupt is triggered.
     */
    int VBLANK_HANDLER_ADDRESS = 0x40;
    int LCDC_HANDLER_ADDRESS = 0x48;
    int TIMER_OVERFLOW_HANDLER_ADDRESS = 0x50;
    int SERIAL_TRANSFER_HANDLER_ADDRESS = 0x58;
    int HILO_HANDLER_ADDRESS = 0x60;
    int W = 160;
    int H = 144;

    interface LCDC
    {
        int BGWINDOW_DISPLAY_BIT = 0x01;
        int SPRITE_DISPLAY_BIT = 0x02;
        int SPRITE_SIZE_BIT = 0x04;
        int BG_TILE_MAP_DISPLAY_SELECT_BIT = 0x08;
        int BGWINDOW_TILE_DATA_SELECT_BIT = 0x10;
        int WINDOW_DISPLAY_BIT = 0x20;
        int WINDOW_TILE_MAP_DISPLAY_SELECT_BIT = 0x40;
        int CONTROL_OPERATION_BIT = 0x80;
    }

    interface LCD_STAT
    {
        int OAM_MODE_BIT = 0x20;
        int VBLANK_MODE_BIT = 0x10;
        int HBLANK_MODE_BIT = 0x8;
        int COINCIDENCE_BIT = 0x4;
        int COINCIDENCE_INTERRUPT_ENABLED_BIT = 0x40;
        int MODE_MASK = 0b11;

        /**
         * Values for bit 1-0
         */
        interface Mode
        {
            int HBLANK_BIT = 0b00;
            int VBLANK_BIT = 0b01;
            int OAM_RAM_SEARCH_BIT = 0b10;
            int DATA_TRANSFER_BIT = 0b11;
        }
    }
}
