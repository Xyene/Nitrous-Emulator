package nitrous.lcd;

import nitrous.Emulator;
import nitrous.PaletteColors;
import nitrous.R;
import nitrous.mbc.Memory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

import static nitrous.Emulator.*;

public class LCD
{
    protected final Emulator core;
    /**
     * Background palettes. On CGB, 0-7 are used. On GB, only 0 is used.
     */
    private Palette[] bgPalettes = new Palette[8];
    /**
     * Sprite palettes. 0-7 used on CGB, 0-1 used on GB.
     */
    private Palette[] spritePalettes = new Palette[8];
    /**
     * Background palette memory on the CGB, indexed through $FF69.
     */
    private byte[] gbcBackgroundPaletteMemory = new byte[0x40];
    /**
     * Sprite palette memory on the CGB, indexed through $FF6b.
     */
    private byte[] gbcSpritePaletteMemory = new byte[0x40];

    private void loadPalettesFromMemory(byte[] from, Palette[] to)
    {
        for (int i = 0; i < 8; i++)
        {
            int[] color = new int[4];
            for (int j = 0; j < 4; j++)
            {
                /**
                 * This register allows to read/write data to the CGBs Background Palette Memory, addressed through Register FF68.
                 * Each color is defined by two bytes (Bit 0-7 in first byte).
                 *
                 * Bit 0-4   Red Intensity   (00-1F)
                 * Bit 5-9   Green Intensity (00-1F)
                 * Bit 10-14 Blue Intensity  (00-1F)
                 */
                int data = (((from[i * 8 + j * 2 + 1]) & 0xff) << 8) | ((from[i * 8 + j * 2]) & 0xff);
                int red = (data & 0x1f);
                int green = (data >> 5) & 0x1f;
                int blue = (data >> 10) & 0x1f;
                color[j] = 0xff000000 |
                        (((int) (red / 31f * 255 + 0.5) & 0xFF) << 16) |
                        (((int) (green / 31f * 255 + 0.5) & 0xFF) << 8) |
                        ((int) (blue / 31f * 255 + 0.5) & 0xFF);
            }
            to[i] = new GBCPalette(color);

            // System.err.println(Thread.currentThread().getStackTrace()[7]);
        }
    }

    public void setBackgroundPalette(int reg, int data)
    {
        gbcBackgroundPaletteMemory[reg] = (byte) (data);
        loadPalettesFromMemory(gbcBackgroundPaletteMemory, bgPalettes);
    }

    public void setSpritePalette(int reg, int data)
    {
        gbcSpritePaletteMemory[reg] = (byte) (data);
        loadPalettesFromMemory(gbcSpritePaletteMemory, spritePalettes);
    }

    public LCD(Emulator core)
    {
        this.core = core;
        if (core.cartridge.isColorGB)
        {
            Arrays.fill(gbcBackgroundPaletteMemory, (byte) 0x1f);
            loadPalettesFromMemory(gbcSpritePaletteMemory, spritePalettes);
            loadPalettesFromMemory(gbcBackgroundPaletteMemory, bgPalettes);
        } else
        {
            /**
             * FF69 - BCPD/BGPD - CGB Mode Only - Background Palette Data
             This register allows to read/write data to the CGBs Background Palette Memory, addressed through Register FF68.
             Each color is defined by two bytes (Bit 0-7 in first byte).

             Bit 0-4   Red Intensity   (00-1F)
             Bit 5-9   Green Intensity (00-1F)
             Bit 10-14 Blue Intensity  (00-1F)

             Much like VRAM, Data in Palette Memory cannot be read/written during the time when the LCD Controller is reading from it. (That is when the STAT register indicates Mode 3).
             Note: Initially all background colors are initialized as white.
             */
            PaletteColors colors = PaletteColors.byHash[core.cartridge.checksum];
            bgPalettes[0] = new DMGPalette(this, colors.bg, R.R_BGP);
            spritePalettes[0] = new DMGPalette(this, colors.obj0, R.R_OBP0);
            spritePalettes[1] = new DMGPalette(this, colors.obj1, R.R_OBP1);
        }
    }

    private int[] spritesDrawn = new int[144];
    private long lcdCycles = 0;

    public boolean drawTile(Palette palette, BufferedImage where, int[] data, int x, int y, int tile, int scanline, boolean flipX, boolean flipY, int bank, boolean bgPass)
    {
        byte[] vram = core.mmu.vram;
        int w = where.getWidth();
        int h = where.getHeight();
        if (y <= scanline && scanline < y + 8)
        {
            int line = scanline - y;

            for (int px = 0; px < 8; px++)
            {
                /**
                 * For each line, the first byte defines the least significant bits of the
                 * color numbers for each pixel, and the second byte defines the upper bits of the color numbers.
                 * In either case, Bit 7 is the leftmost pixel, and Bit 0 the rightmost.
                 */
                // here we handle the x and y flipping by tweaking the indexes we are accessing
                int logicalLine = (flipY ? 7 - line : line);
                int logicalX = (flipX ? 7 - px : px);
                short paletteIndex = (short) (
                        (
                                (
                                        (
                                                // each tile takes up 16 bytes, and each line takes 2 bytes
                                                vram[Memory.VRAM_PAGESIZE * bank + tile * 16 + logicalLine * 2 + 1] & (0x80 >> logicalX)
                                        ) >> (7 - logicalX)
                                ) << 1 // this is the upper bit of the color number
                        ) |
                                (
                                        (
                                                vram[Memory.VRAM_PAGESIZE * bank + tile * 16 + logicalLine * 2] & (0x80 >> logicalX)
                                        ) >> (7 - logicalX)
                                ) // << 0, this is the lower bit of the color number
                );
                // Drawing of the background layer is handled separately
                if (bgPass && paletteIndex != 0) continue;
                if (!bgPass && paletteIndex == 0) continue;
                // Destination pixels
                int dx = x + px;
                int dy = y + line;

                if (dx >= 0 && dy >= 0 && dx < w && dy < h)
                    data[dx + dy * w] = palette.getColor(paletteIndex);
            }
            return true;
        }
        return false;
    }

    private void drawSprites(BufferedImage buffer, int[] data, int scanline, boolean underBG)
    {
        byte[] oam = core.mmu.oam;
        // Actual GameBoy hardware can only handle drawing 10 sprites per line
        // our code doesn't actually have this limitation, but we artificially introduce it by keeping
        // track of how many sprites are drawn per line
        for (int i = 0; i < oam.length && spritesDrawn[scanline] < 10; i += 4)
        {
            byte attribs = oam[i + 3];
            int vrambank = (attribs & 0b1000) != 0 && core.cartridge.isColorGB ? 1 : 0;
            // obj above BG
            if ((attribs & 0x80) != 0 == underBG)
            {
                short y = (short) (oam[i] & 0xff);
                short x = (short) (oam[i + 1] & 0xff);
                short tile = (short) (oam[i + 2] & 0xff);
                boolean flipX = (attribs & 0x20) != 0;
                boolean flipY = (attribs & 0x40) != 0;

                int obp = core.cartridge.isColorGB ? (attribs & 0x7) : (attribs >> 4) & 0x1;

                Palette pal = spritePalettes[obp];
                if (isUsingTallSprites())
                {
                    // If we're using tall sprites we actually have to flip the order that we draw the top/bottom tiles
                    short hi = (short) (flipY ? (tile | 0x01) : (tile & 0xFE));
                    short lo = (short) (flipY ? (tile & 0xFE) : (tile | 0x01));
                    if (drawTile(pal, buffer, data, x - 8, y - 16, hi, scanline, flipX, flipY, vrambank, false))
                        spritesDrawn[scanline]++;
                    if (drawTile(pal, buffer, data, x - 8, y - 8, lo, scanline, flipX, flipY, vrambank, false))
                        spritesDrawn[scanline]++;

                } else
                {
                    if (drawTile(pal, buffer, data, x - 8, y - 16, tile, scanline, flipX, flipY, vrambank, false))
                        spritesDrawn[scanline]++;
                }
            }
        }
    }

    public long tick(long cycles)
    {
        lcdCycles += cycles;
        // 4.194304MHz clock, 154 scanlines per frame, 59.7 frames/second
        // = ~456 cycles / line
        if (lcdCycles >= 456)
        {
            lcdCycles -= 456;
            /**
             * The LY indicates the vertical line to which the present data is transferred to the LCD Driver.
             * The LY can take on any value between 0 through 153. The values between 144 and 153 indicate the
             * V-Blank period.
             */
            int LY = core.mmu.registers[R.R_LY] & 0xFF;
            // draw the scanline
            if (display != null) draw(screenBuffer, LY);
            core.mmu.registers[R.R_LY] = (byte) (((LY + 1) % 153) & 0xff);

            boolean isVBlank = 144 <= LY;

            if (!isVBlank && core.mmu.hdma != null) {
                System.err.println(LY);
                core.mmu.hdma.tick();
            }

            /**
             * INT 48 - LCDC Status Interrupt
             *
             * There are various reasons for this interrupt to occur as described by the STAT register ($FF40).
             * One very popular reason is to indicate to the user when the video hardware is about to redraw
             * a given LCD line.
             *
             * This is determined with an LY == LYC comparison.
             */
            if (core.isInterruptEnabled(R.LCDC_BIT) &&
                    // LYC=LY Coincidence
                    (core.mmu.registers[R.R_LCD_STAT] & 0x40) != 0 &&
                    // Fire when LYC == LY
                    (core.mmu.registers[R.R_LYC] & 0xff) == LY &&
                    displayEnabled() &&
                    // the range [144, 153] is Vblank, don't run there
                    !isVBlank)
            {
                core.setInterruptTriggered(R.LCDC_BIT);
            }

            /**
             * INT 40 - V-Blank Interrupt
             *
             * The V-Blank interrupt occurs ca. 59.7 times a second on a regular GB and ca. 61.1 times a second
             * on a Super GB (SGB). This interrupt occurs at the beginning of the V-Blank period (LY=144).
             * During this period video hardware is not using video ram so it may be freely accessed.
             * This period lasts approximately 1.1 milliseconds.
             */
            if (LY == 144)
            {
                if (display != null)
                {
                    display.paintImmediately(display.getBounds());
                }
                if (core.isInterruptEnabled(R.VBLANK_BIT) && displayEnabled())
                {
                    // Trigger VBlank
                    core.setInterruptTriggered(R.VBLANK_BIT);

                    // Trigger LCDC if enabled
                    if (core.isInterruptEnabled(R.LCDC_BIT) && (core.mmu.registers[R.R_LCD_STAT] & R.LCD_STAT.VBLANK_MODE_BIT) != 0)
                    {
                        core.setInterruptTriggered(R.LCDC_BIT);
                    }
                }
            }
            return 0;
        }
        return 0;
    }


    public void draw(BufferedImage buffer, int scanline)
    {
        // don't even bother if the display is not enabled
        if (!displayEnabled()) return;

        // we still receive these calls for scanlines in vblank, but we can just ignore them
        if (scanline >= 144) return;

        spritesDrawn[scanline] = 0;

        int W = buffer.getWidth();
        DataBufferInt dbb = (DataBufferInt) buffer.getRaster().getDataBuffer();
        int[] data = dbb.getData(0);

        byte[] vram = core.mmu.vram;

        int tileDataOffset = getTileDataOffset();

        // All sprites show up above BG 0, so just fill the scanline with BG 0 to start with
        if (backgroundEnabled())
        {
            if (core.cartridge.isColorGB)
            {
                int y = (scanline + getScrollY() % 8) / 8;
                int scrollY = getScrollY();
                int scrollX = getScrollX();
                int offset = getBackgroundTileMapOffset();

                // 20 8x8 tiles fit in a 160px-wide screen
                for (int x = 0; x < 21; x++)
                {
                    int addressBase = offset + ((y + scrollY / 8) % 32 * 32) + ((x + scrollX / 8) % 32);
                    // add 256 to jump into second tile pattern table
                    int tile = tileDataOffset == 0 ? vram[addressBase] & 0xff : vram[addressBase] + 256;
                    int gbcVramBank = 0;
                    boolean flipX = false;
                    boolean flipY = false;
                    int gbcPalette = 0;

                    int attribs = vram[Memory.VRAM_PAGESIZE + addressBase];
                    if ((attribs & 0x8) != 0) gbcVramBank = 1;
                    flipX = (attribs & 0x20) != 0;
                    flipY = (attribs & 0x10) != 0;
                    gbcPalette = (attribs & 0x7);

                    drawTile(bgPalettes[gbcPalette],
                            buffer, data, -(scrollX % 8) + x * 8, -(scrollY % 8) + y * 8, tile, scanline, flipX, flipY, gbcVramBank, true);
                }
            } else
            {
                int bg = bgPalettes[0].getColor(0);
                for (int x = 0; x < W; x++)
                    data[x + scanline * W] = bg;
            }
        }

        if (spritesEnabled())
            drawSprites(buffer, data, scanline, true);

        if (backgroundEnabled())
        {
            int y = (scanline + getScrollY() % 8) / 8;
            int scrollY = getScrollY();
            int scrollX = getScrollX();
            int offset = getBackgroundTileMapOffset();

            // 20 8x8 tiles fit in a 160px-wide screen
            for (int x = 0; x < 21; x++)
            {
                int addressBase = offset + ((y + scrollY / 8) % 32 * 32) + ((x + scrollX / 8) % 32);
                // add 256 to jump into second tile pattern table
                int tile = tileDataOffset == 0 ? vram[addressBase] & 0xff : vram[addressBase] + 256;
                int gbcVramBank = 0;
                boolean flipX = false;
                boolean flipY = false;
                int gbcPalette = 0;
                if (core.cartridge.isColorGB)
                {
                    int attribs = vram[Memory.VRAM_PAGESIZE + addressBase];
                    if ((attribs & 0x8) != 0) gbcVramBank = 1;
                    flipX = (attribs & 0x10) != 0;
                    flipY = (attribs & 0x20) != 0;
                    gbcPalette = (attribs & 0x07);
                }
                drawTile(bgPalettes[gbcPalette],
                        buffer, data, -(scrollX % 8) + x * 8, -(scrollY % 8) + y * 8, tile, scanline, flipX, flipY, gbcVramBank, false);
            }
        }
        if (windowEnabled() &&
                scanline >= getWindowPosY() &&
                getWindowPosX() < buffer.getWidth() && getWindowPosY() >= 0)
        {
            int posX = getWindowPosX();

            int bg = bgPalettes[0].getColor(0);
            for (int x = Math.max(posX, 0); x < buffer.getWidth(); x++)
                data[x + scanline * W] = bg;

            int posY = getWindowPosY();
            int tileMapOffset = getWindowTileMapOffset();

            int y = (scanline - posY) / 8;
            for (int x = getWindowPosX() / 8; x < 21; x++)
            {
                // 32 tiles a row
                int addressBase = tileMapOffset + (x + y * 32);
                // add 256 to jump into second tile pattern table

                int tile = tileDataOffset == 0 ? vram[addressBase] & 0xff : vram[addressBase] + 256;
                int gbcVramBank = 0;
                boolean flipX = false;
                boolean flipY = false;
                int gbcPalette = 0;
                if (core.cartridge.isColorGB)
                {
                    int attribs = vram[Memory.VRAM_PAGESIZE + addressBase];
                    if ((attribs & 0x8) != 0) gbcVramBank = 1;
                    flipX = (attribs & 0x10) != 0;
                    flipY = (attribs & 0x20) != 0;
                    gbcPalette = (attribs & 0x07);
                }
                drawTile(bgPalettes[gbcPalette],
                        buffer, data, posX + x * 8, posY + y * 8, tile, scanline, flipX, flipY, gbcVramBank, false);


//                int tile = tileDataOffset == 0 ? vram[addressBase] & 0xff : vram[addressBase] + 256;
//                drawTile(bgPalettes[0], buffer, data, posX + x * 8, posY + y * 8, tile, scanline, false, false, 0, false);
            }
        }

        if (spritesEnabled())
            drawSprites(buffer, data, scanline, false);
    }

    /**
     * Bit 7 - LCD Display Enable             (0=Off, 1=On)
     * Bit 6 - Window Tile Map Display Select (0=9800-9BFF, 1=9C00-9FFF)
     * Bit 5 - Window Display Enable          (0=Off, 1=On)
     * Bit 4 - BG & Window Tile Data Select   (0=8800-97FF, 1=8000-8FFF)
     * Bit 3 - BG Tile Map Display Select     (0=9800-9BFF, 1=9C00-9FFF)
     * Bit 2 - OBJ (Sprite) Size              (0=8x8, 1=8x16)
     * Bit 1 - OBJ (Sprite) Display Enable    (0=Off, 1=On)
     * Bit 0 - BG Display (for CGB see below) (0=Off, 1=On)
     */
    public boolean displayEnabled()
    {
        return (core.mmu.registers[R.R_LCDC] & R.LCDC.CONTROL_OPERATION_BIT) != 0;
    }

    public boolean backgroundEnabled()
    {
        return (core.mmu.registers[R.R_LCDC] & 0x01) == 1;
    }

    public int getWindowTileMapOffset()
    {
        if ((core.mmu.registers[R.R_LCDC] & R.LCDC.WINDOW_TILE_MAP_DISPLAY_SELECT_BIT) != 0)
            return 0x1c00;
        return 0x1800;
    }

    public int getBackgroundTileMapOffset()
    {
        if ((core.mmu.registers[R.R_LCDC] & R.LCDC.BG_TILE_MAP_DISPLAY_SELECT_BIT) != 0)
            return 0x1c00;
        return 0x1800;
    }

    public boolean isUsingTallSprites()
    {
        return (core.mmu.registers[R.R_LCDC] & R.LCDC.SPRITE_SIZE_BIT) != 0;
    }

    public boolean spritesEnabled()
    {
        return (core.mmu.registers[R.R_LCDC] & R.LCDC.SPRITE_DISPLAY_BIT) != 0;
    }

    public boolean windowEnabled()
    {
        return (core.mmu.registers[R.R_LCDC] & R.LCDC.WINDOW_DISPLAY_BIT) != 0;
    }

    /**
     * Tile patterns are taken from the Tile Data Table located either at $8000-8FFF or $8800-97FF.
     * In the first case, patterns are numbered with unsigned numbers from 0 to 255 (i.e. pattern #0 lies at address $8000).
     * In the second case, patterns have signed numbers from -128 to 127 (i.e. pattern #0 lies at address $9000).
     * <p>
     * The Tile Data Table address for the background can be selected via LCDC register.
     */
    public int getTileDataOffset()
    {
        if ((core.mmu.registers[R.R_LCDC] & R.LCDC.BGWINDOW_TILE_DATA_SELECT_BIT) != 0)
            return 0;
        return 0x0800;
    }

    public int getScrollX()
    {
        return (core.mmu.registers[R.R_SCX] & 0xFF);
    }

    public int getScrollY()
    {
        return (core.mmu.registers[R.R_SCY] & 0xff);
    }

    public int getWindowPosX()
    {
        return (core.mmu.registers[R.R_WX] & 0xFF) - 7;
    }

    public int getWindowPosY()
    {
        return (core.mmu.registers[R.R_WY] & 0xFF);
    }
}
