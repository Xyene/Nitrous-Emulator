package nitrous.lcd;

import nitrous.cpu.Emulator;
import nitrous.PaletteColors;
import nitrous.R.*;
import nitrous.Settings;
import nitrous.mbc.Memory;
import nitrous.renderer.IRenderManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.peer.ComponentPeer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static nitrous.R.*;

public class LCD
{
    protected final Emulator core;

    /**
     * A buffer to hold the current rendered frame.
     */
    public final BufferedImage screenBuffer = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);

    /**
     * Background palettes. On CGB, 0-7 are used. On GB, only 0 is used.
     */
    private final IPalette[] bgPalettes = new IPalette[8];

    /**
     * Sprite palettes. 0-7 used on CGB, 0-1 used on GB.
     */
    private final IPalette[] spritePalettes = new IPalette[8];

    /**
     * Background palette memory on the CGB, indexed through $FF69.
     */
    private final byte[] gbcBackgroundPaletteMemory = new byte[0x40];

    /**
     * Sprite palette memory on the CGB, indexed through $FF6B.
     */
    private final byte[] gbcSpritePaletteMemory = new byte[0x40];

    private static final int W = 160, H = 144;

    private void loadPalettesFromMemory(byte[] from, IPalette[] to)
    {
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 4; ++j)
                updatePalette(from, to, i, j);

        // System.err.println(Thread.currentThread().getStackTrace()[7]);
    }


    private void updatePalette(byte[] from, IPalette[] to, int i, int j)
    {
        int[] color = ((GBCPalette) to[i]).colors;

        /**
         * This register allows to read/write data to the CGBs Background Palette Memory, addressed through Register FF68.
         * Each color is defined by two bytes (Bit 0-7 in first byte).
         *
         * Bit 0-4   Red Intensity   (00-1F)
         * Bit 5-9   Green Intensity (00-1F)
         * Bit 10-14 Blue Intensity  (00-1F)
         */
        int data = ((from[i * 8 + j * 2 + 1] & 0xff) << 8) | (from[i * 8 + j * 2] & 0xff);
        int red = (data & 0x1f);
        int green = (data >> 5) & 0x1f;
        int blue = (data >> 10) & 0x1f;
        color[j] = (((int) (red / 31f * 255 + 0.5) & 0xFF) << 16) |
                (((int) (green / 31f * 255 + 0.5) & 0xFF) << 8) |
                ((int) (blue / 31f * 255 + 0.5) & 0xFF);
        ///System.out.println("updated a palette");
    }

    public void setBackgroundPalette(int reg, int data)
    {
        gbcBackgroundPaletteMemory[reg] = (byte) data;
        updatePalette(gbcBackgroundPaletteMemory, bgPalettes, reg >> 3, (reg >> 1) & 0x3);
        //loadPalettesFromMemory(gbcBackgroundPaletteMemory, bgPalettes);
    }

    public void setSpritePalette(int reg, int data)
    {
        gbcSpritePaletteMemory[reg] = (byte) data;
        updatePalette(gbcSpritePaletteMemory, spritePalettes, reg >> 3, (reg >> 1) & 0x3);
        //loadPalettesFromMemory(gbcSpritePaletteMemory, spritePalettes);
    }

    public LCD(Emulator core)
    {
        this.core = core;
        if (core.cartridge.isColorGB)
        {
            Arrays.fill(gbcBackgroundPaletteMemory, (byte) 0x1f);
            for (int i = 0; i < spritePalettes.length; i++) spritePalettes[i] = new GBCPalette(new int[4]);
            for (int i = 0; i < bgPalettes.length; i++) bgPalettes[i] = new GBCPalette(new int[4]);
            loadPalettesFromMemory(gbcSpritePaletteMemory, spritePalettes);
            loadPalettesFromMemory(gbcBackgroundPaletteMemory, bgPalettes);
        } else
        {
            /**
             * FF69 - BCPD/BGPD - CGB Mode Only - Background Palette Data
             * This register allows to read/write data to the CGBs Background Palette Memory, addressed through Register FF68.
             * Each color is defined by two bytes (Bit 0-7 in first byte).
             *
             * Bit 0-4   Red Intensity   (00-1F)
             * Bit 5-9   Green Intensity (00-1F)
             * Bit 10-14 Blue Intensity  (00-1F)
             *
             * Much like VRAM, Data in Palette Memory cannot be read/written during the time when the LCD Controller is
             * reading from it. (That is when the STAT register indicates Mode 3).
             * Note: Initially all background colors are initialized as white.
             */
            PaletteColors colors = PaletteColors.byHash[core.cartridge.checksum];
            bgPalettes[0] = new DMGPalette(this, colors.bg, R_BGP);
            spritePalettes[0] = new DMGPalette(this, colors.obj0, R_OBP0);
            spritePalettes[1] = new DMGPalette(this, colors.obj1, R_OBP1);
        }
    }

    private final int[] spritesDrawn = new int[144];
    private long lcdCycles = 0;

    public void drawTile(IPalette palette, int[] data, int x, int y, int tile, int scanline,
                         boolean flipX, boolean flipY, int bank, int basePriority, boolean sprite)
    {
        // Store a local copy to save a lot of load opcodes.
        byte[] vram = core.mmu.vram;
        int line = scanline - y;
        int addressBase = Memory.VRAM_PAGESIZE * bank + tile * 16;

        for (int px = 0; px < 8; px++)
        {
            // Destination pixels
            int dx = x + px;

            if (dx < 0 || dx >= W || scanline >= H)
                continue;

            int index = dx + scanline * W;
            if (basePriority != 0 && basePriority < (data[index] & 0xFF000000))
                continue;

            /**
             * For each line, the first byte defines the least significant bits of the
             * color numbers for each pixel, and the second byte defines the upper bits of the color numbers.
             * In either case, Bit 7 is the leftmost pixel, and Bit 0 the rightmost.
             */
            // here we handle the x and y flipping by tweaking the indexes we are accessing
            int logicalLine = (flipY ? 7 - line : line);
            int logicalX = (flipX ? 7 - px : px);

            int address = addressBase + logicalLine * 2;

            int paletteIndex =
                    (
                            (
                                    (
                                            // each tile takes up 16 bytes, and each line takes 2 bytes
                                            vram[address + 1] & (0x80 >> logicalX)
                                    ) >> (7 - logicalX)
                            ) << 1 // this is the upper bit of the color number
                    ) |
                            (
                                    (
                                            vram[address] & (0x80 >> logicalX)
                                    ) >> (7 - logicalX)
                            ); // << 0, this is the lower bit of the color number

            boolean index0 = paletteIndex == 0;
            int priority = basePriority == 0 ? (index0 ? 1 << 24 : 3 << 24) : basePriority;
            if (sprite && index0)
                continue;

            if (priority >= (data[index] & 0xFF000000))
                data[index] = priority | palette.getColor(paletteIndex);
        }
    }

    private void drawSprites(int[] data, int scanline)
    {
        byte[] oam = core.mmu.oam;
        boolean tall = isUsingTallSprites();
        boolean isColorGB = core.cartridge.isColorGB;

        // Actual GameBoy hardware can only handle drawing 10 sprites per line
        // our code doesn't actually have this limitation, but we artificially introduce it by keeping
        // track of how many sprites are drawn per line
        for (int i = 0; i < oam.length && spritesDrawn[scanline] < 10; i += 4)
        {
            byte attribs = oam[i + 3];
            int vrambank = (attribs & 0b1000) != 0 && isColorGB ? 1 : 0;
            int priority = (attribs & 0x80) != 0 ? 2 << 24 : 5 << 24;

            int y = oam[i] & 0xff;

            if (!tall && !(y - 16 <= scanline && scanline < y - 8))
                continue;

            int x = oam[i + 1] & 0xff;
            int tile = oam[i + 2] & 0xff;
            boolean flipX = (attribs & 0x20) != 0;
            boolean flipY = (attribs & 0x40) != 0;
            int obp = isColorGB ? (attribs & 0x7) : (attribs >> 4) & 0x1;

            IPalette pal = spritePalettes[obp];
            if (tall)
            {
                // If we're using tall sprites we actually have to flip the order that we draw the top/bottom tiles
                int hi = flipY ? (tile | 0x01) : (tile & 0xFE);
                int lo = flipY ? (tile & 0xFE) : (tile | 0x01);
                if (y - 16 <= scanline && scanline < y - 8)
                {
                    drawTile(pal, data, x - 8, y - 16, hi, scanline, flipX, flipY, vrambank, priority, true);
                    spritesDrawn[scanline]++;
                }
                if (y - 8 <= scanline && scanline < y)
                {
                    drawTile(pal, data, x - 8, y - 8, lo, scanline, flipX, flipY, vrambank, priority, true);
                    spritesDrawn[scanline]++;
                }

            } else
            {
                drawTile(pal, data, x - 8, y - 16, tile, scanline, flipX, flipY, vrambank, priority, true);
                spritesDrawn[scanline]++;
            }
        }
    }

    public IRenderManager currentRenderer;
    public List<IRenderManager> renderers;

    @SuppressWarnings("deprecation")
    public void initializeRenderers()
    {
        renderers = Collections.unmodifiableList(new ArrayList<IRenderManager>()
        {{
                for (Class<? extends IRenderManager> rendererClass : IRenderManager.RENDERERS)
                {
                    IRenderManager renderer;
                    try
                    {
                        System.err.println(rendererClass);
                        renderer = rendererClass.getDeclaredConstructor(ComponentPeer.class).newInstance(core.display.getPeer());
                    } catch (ReflectiveOperationException e1)
                    {
//                        e1.printStackTrace();
                        continue;
                    }
                    if (renderer.getGraphics() != null)
                    {
                        add(renderer);
                        if (currentRenderer == null)
                        {
                            System.out.println("Using " + renderer);
                            currentRenderer = renderer;
                        }
                    } else
                    {
                        System.err.println(renderer + " failed to produce a Graphics2D");
                    }
                }
            }});
    }

    int vblankcount = 0;
    long lastsecond = -1;
    long lastclock;

    public void tick(long cycles)
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
            int LY = core.mmu.registers[R_LY] & 0xFF;
            // draw the scanline
            boolean displayEnabled = displayEnabled();
            if (displayEnabled)
                if (core.display != null) draw(screenBuffer, LY);
            core.mmu.registers[R_LY] = (byte) (((LY + 1) % 154) & 0xff);

            if (LY == 0)
            {
                if (lastsecond == -1)
                {
                    lastsecond = System.nanoTime();
                    lastclock = core.cycle;
                }
                vblankcount++;
                if (vblankcount == 60)
                {
                    System.out.println("Took " + ((System.nanoTime() - lastsecond) / 1_000_000_000.0) +
                            " seconds for 60 frames - " + (core.cycle - lastclock) / 60 + " clks/frames");
                    lastclock = core.cycle;
                    vblankcount = 0;
                    lastsecond = System.nanoTime();
                }
            }

            boolean isVBlank = 144 <= LY;

            if (!isVBlank && core.mmu.hdma != null)
            {
                System.err.println(LY);
                core.mmu.hdma.tick();
            }

            core.mmu.registers[R_LCD_STAT] &= ~0x03;

            int mode = 0;
            if (isVBlank) mode = 0x01;

            core.mmu.registers[R_LCD_STAT] |= mode;

            int lcdStat = core.mmu.registers[R_LCD_STAT];
            if (displayEnabled && !isVBlank)
            {
                /**
                 * INT 48 - LCDC Status Interrupt
                 *
                 * There are various reasons for this interrupt to occur as described by the STAT register ($FF40).
                 * One very popular reason is to indicate to the user when the video hardware is about to redraw
                 * a given LCD line.
                 *
                 * This is determined with an LY == LYC comparison.
                 */
                if ((lcdStat & LCD_STAT.COINCIDENCE_INTERRUPT_ENABLED_BIT) != 0)
                {
                    int lyc = (core.mmu.registers[R_LYC] & 0xff);
                    // Fire when LYC == LY
                    if (lyc == LY)
                    {
                        core.setInterruptTriggered(LCDC_BIT);
                        core.mmu.registers[R_LCD_STAT] |= LCD_STAT.COINCIDENCE_BIT;
                    } else
                    {
                        core.mmu.registers[R_LCD_STAT] &= ~LCD_STAT.COINCIDENCE_BIT;
                    }
                }

                if ((lcdStat & LCD_STAT.HBLANK_MODE_BIT) != 0)
                {
                    core.setInterruptTriggered(LCDC_BIT);
                }
            }

            /**
             * INT 40 - V-Blank Interrupt
             *
             * The V-Blank interrupt occurs ca. 59.7 times a second on a regular GB and ca. 61.1 times a second
             * on a Super GB (SGB). This interrupt occurs at the beginning of the V-Blank period (LY=144).
             * During this period video hardware is not using video ram so it may be freely accessed.
             * This period lasts approximately 1.1 milliseconds.
             */
            // use 143 here as we've just finished processing line 143 and will start 144
            if (LY == 143)
            {
                Graphics2D graphics = currentRenderer != null ? currentRenderer.getGraphics() : null;

                if (graphics != null)
                {
                    switch (Settings.getInterpolator())
                    {
                        case NEAREST:
                            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                            break;
                        case BILINEAR:
                            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            break;
                        case BICUBIC:
                            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                            break;
                    }

                    graphics.drawImage(screenBuffer, 0, 0, core.display.getWidth(), core.display.getHeight(), null);
                }

                if (displayEnabled)
                {
                    // Trigger VBlank
                    core.setInterruptTriggered(VBLANK_BIT);

                    // Trigger LCDC if enabled
                    if ((lcdStat & LCD_STAT.VBLANK_MODE_BIT) != 0)
                    {
                        core.setInterruptTriggered(LCDC_BIT);
                    }
                }
            }
        }
    }

    static final int[] BLANK = new int[W * H];

    public void draw(BufferedImage buffer, int scanline)
    {
        // don't even bother if the display is not enabled
        if (!displayEnabled()) return;

        // we still receive these calls for scanlines in vblank, but we can just ignore them
        if (scanline >= 144 || scanline < 0) return;

        spritesDrawn[scanline] = 0;

        DataBufferInt dbb = (DataBufferInt) buffer.getRaster().getDataBuffer();
        int[] data = dbb.getData(0);

        if (scanline == 0)
            System.arraycopy(BLANK, 0, data, 0, data.length);

        byte[] vram = core.mmu.vram;

        int tileDataOffset = getTileDataOffset();

        // All sprites show up above BG 0, so just fill the scanline with BG 0 to start with
        if (backgroundEnabled())
        {

            int y = (scanline + getScrollY() % 8) / 8;
            int scrollY = getScrollY();
            int scrollX = getScrollX();

//                System.out.printf("SCX=%d, SCY=%d\n", scrollX, scrollY);

            int offset = getBackgroundTileMapOffset();

            // 20 8x8 tiles fit in a 160px-wide screen
            for (int x = 0; x < 21; x++)
            {
                int addressBase = offset + ((y + scrollY / 8) % 32 * 32) + ((x + scrollX / 8) % 32);
                // add 256 to jump into second tile pattern table
                int tile = tileDataOffset == 0 ? vram[addressBase] & 0xff : vram[addressBase] + 256;

                int gbcVramBank = 0;
                int gbcPalette = 0;
                boolean flipX = false;
                boolean flipY = false;
                if (core.cartridge.isColorGB)
                {
                    int attribs = vram[Memory.VRAM_PAGESIZE + addressBase];

                    if ((attribs & 0x8) != 0) gbcVramBank = 1;
                    flipX = (attribs & 0x20) != 0;
                    flipY = (attribs & 0x40) != 0;
                    gbcPalette = (attribs & 0x7);
                }
                drawTile(bgPalettes[gbcPalette], data, -(scrollX % 8) + x * 8, -(scrollY % 8) + y * 8, tile, scanline, flipX, flipY, gbcVramBank, 0, false);
            }
        }

        if (spritesEnabled())
            drawSprites(data, scanline);

        if (windowEnabled() &&
                scanline >= getWindowPosY() &&
                getWindowPosX() < W && getWindowPosY() >= 0)
        {
            int posX = getWindowPosX();

//            int bg = bgPalettes[0].getColor(0);
//            for (int x = Math.max(posX, 0); x < W; x++)
//                data[x + scanline * W] = bg;

            int posY = getWindowPosY();
            int tileMapOffset = getWindowTileMapOffset();

            int y = (scanline - posY) / 8;
            for (int x = getWindowPosX() / 8; x < 21; x++)
//            for (int x = 0; x < 21 - getWindowPosX() / 8; x++)
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
                    flipX = (attribs & 0x20) != 0;
                    flipY = (attribs & 0x40) != 0;
                    gbcPalette = (attribs & 0x07);
                }

                drawTile(bgPalettes[gbcPalette], data, posX + x * 8, posY + y * 8, tile, scanline, flipX, flipY, gbcVramBank, 6 << 24, false);

//                int tile = tileDataOffset == 0 ? vram[addressBase] & 0xff : vram[addressBase] + 256;
//                drawTile(bgPalettes[0], buffer, data, posX + x * 8, posY + y * 8, tile, scanline, false, false, 0, false);
            }
        }

        /*if (spritesEnabled())
            drawSprites(buffer, data, scanline, false);*/
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
        return (core.mmu.registers[R_LCDC] & LCDC.CONTROL_OPERATION_BIT) != 0;
    }

    public boolean backgroundEnabled()
    {
        return (core.mmu.registers[R_LCDC] & LCDC.BGWINDOW_DISPLAY_BIT) != 0;
    }

    public int getWindowTileMapOffset()
    {
        if ((core.mmu.registers[R_LCDC] & LCDC.WINDOW_TILE_MAP_DISPLAY_SELECT_BIT) != 0)
            return 0x1c00;
        return 0x1800;
    }

    public int getBackgroundTileMapOffset()
    {
        if ((core.mmu.registers[R_LCDC] & LCDC.BG_TILE_MAP_DISPLAY_SELECT_BIT) != 0)
            return 0x1c00;
        return 0x1800;
    }

    public boolean isUsingTallSprites()
    {
        return (core.mmu.registers[R_LCDC] & LCDC.SPRITE_SIZE_BIT) != 0;
    }

    public boolean spritesEnabled()
    {
        return (core.mmu.registers[R_LCDC] & LCDC.SPRITE_DISPLAY_BIT) != 0;
    }

    public boolean windowEnabled()
    {
        return (core.mmu.registers[R_LCDC] & LCDC.WINDOW_DISPLAY_BIT) != 0;
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
        if ((core.mmu.registers[R_LCDC] & LCDC.BGWINDOW_TILE_DATA_SELECT_BIT) != 0)
            return 0;
        return 0x0800;
    }

    //    public int scrollx;
    public int getScrollX()
    {
        return (core.mmu.registers[R_SCX] & 0xFF);
    }

    public int getScrollY()
    {
        return (core.mmu.registers[R_SCY] & 0xff);
    }

    public int getWindowPosX()
    {
        return (core.mmu.registers[R_WX] & 0xFF) - 7;
    }

    public int getWindowPosY()
    {
        return (core.mmu.registers[R_WY] & 0xFF);
    }
}
