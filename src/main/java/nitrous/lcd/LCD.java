package nitrous.lcd;

import nitrous.cpu.Emulator;
import nitrous.cpu.R.*;
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

import static nitrous.cpu.R.*;

/**
 * Emulates the LCD component of a Gameboy.
 */
public class LCD
{
    /**
     * An array of blank values matching in size with the dimensions of screenBuffer, used to very quickly
     * clear the contents of the previous frame via System.arraycopy.
     */
    private static final int[] BLANK = new int[W * H];

    /**
     * The Emulator on which to operate.
     */
    protected final Emulator core;

    /**
     * A buffer to hold the current rendered frame.
     * <p/>
     * The data is stored in RGB format, which is packed as 0x00RRGGBB. This leaves us with 2 bytes of unused data in
     * at the front, which we use to hold current pixel priorities. That is, our format for this buffer is
     * 0xPPRRGGBB. This does not impact any drawing of this image, but if provides a quick way to determine tile
     * priorities in internal code.
     */
    public final BufferedImage screenBuffer = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);

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

    /**
     * Stores number of sprites drawn per each of the 144 scanlines this frame. Actual Gameboy hardware can
     * only draw 10 sprites/line, so we artificially introduce this limitation using this array.
     */
    private final int[] spritesDrawnPerLine = new int[144];

    /**
     * A counter for the number of cycles elapsed since the last LCD event.
     */
    private long lcdCycles = 0;

    /**
     * Accumulator for how many VBlanks have been performed since the last reset.
     */
    private int currentVBlankCount = 0;

    /**
     * The timestamp of the last second, in nanoseconds.
     */
    private long lastSecondTime = -1;

    /**
     * The last measured Emulator.cycle.
     */
    private long lastCoreCycle;

    /**
     * The current renderer to use when updating the LCD display.
     */
    public IRenderManager currentRenderer;

    /**
     * A List of supported renderers for this platform.
     */
    public List<IRenderManager> renderers;

    /**
     * Creates a new LCD instance.
     *
     * @param core The Emulator to operate on.
     */
    public LCD(Emulator core)
    {
        this.core = core;
        initializePalettes();
    }//end LCD(core)

    /**
     * Initializes all palette RAM to the default on Gameboy boot.
     */
    private void initializePalettes()
    {
        if (core.cartridge.isColorGB)
        {
            // On CGB all background RAM is initialized with 1Fh
            Arrays.fill(gbcBackgroundPaletteMemory, (byte) 0x1f);

            // Create palette structures
            for (int i = 0; i < spritePalettes.length; i++) spritePalettes[i] = new GBCPalette(new int[4]);
            for (int i = 0; i < bgPalettes.length; i++) bgPalettes[i] = new GBCPalette(new int[4]);

            // And "load" them from RAM
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
        }//end if
    }//end initializePalettes

    /**
     * Reloads all Gameboy Color palettes.
     *
     * @param from Palette RAM to load from.
     * @param to   Reference to an array of IPalettes to populate.
     */
    private void loadPalettesFromMemory(byte[] from, IPalette[] to)
    {
        // 8 palettes
        for (int i = 0; i < 8; i++)
        {
            // 4 bytes per palette
            for (int j = 0; j < 4; ++j)
                updatePaletteByte(from, to[i], i, j);
        }//end for
    }//end loadPalettesFromMemory

    /**
     * Performs an update to a byte of palette RAM.
     *
     * @param from The palette RAM to read from.
     * @param to   Reference to an array of IPalettes to update.
     * @param i    The palette index being updated.
     * @param j    The byte index of the palette being updated.
     */
    private void updatePaletteByte(byte[] from, IPalette to, int i, int j)
    {
        /**
         * This register allows to read/write data to the CGBs Background Palette Memory, addressed through Register FF68.
         * Each color is defined by two bytes (Bit 0-7 in first byte).
         *
         * Bit 0-4   Red Intensity   (00-1F)
         * Bit 5-9   Green Intensity (00-1F)
         * Bit 10-14 Blue Intensity  (00-1F)
         */

        // Read an RGB value from RAM
        int data = ((from[i * 8 + j * 2 + 1] & 0xff) << 8) | (from[i * 8 + j * 2] & 0xff);

        // Extract components
        int red = (data & 0x1f);
        int green = (data >> 5) & 0x1f;
        int blue = (data >> 10) & 0x1f;

        // Convert from [0, 1Fh] to [0, FFh], and recombine
        ((GBCPalette) to).colors[j] = (((int) (red / 31f * 255 + 0.5) & 0xFF) << 16) |
                (((int) (green / 31f * 255 + 0.5) & 0xFF) << 8) |
                ((int) (blue / 31f * 255 + 0.5) & 0xFF);
    }//end updatePaletteByte

    /**
     * Updates an entry of background palette RAM. Internal function for use in a Memory controller.
     *
     * @param reg  The register written to.
     * @param data The data written.
     */
    public void setBackgroundPalette(int reg, int data)
    {
        gbcBackgroundPaletteMemory[reg] = (byte) data;
        int palette = reg >> 3;
        updatePaletteByte(gbcBackgroundPaletteMemory, bgPalettes[palette], palette, (reg >> 1) & 0x3);
    }//end setBackgroundPalette

    /**
     * Updates an entry of sprite palette RAM. Internal function for use in a Memory controller.
     *
     * @param reg  The register written to.
     * @param data The data written.
     */
    public void setSpritePalette(int reg, int data)
    {
        gbcSpritePaletteMemory[reg] = (byte) data;
        int palette = reg >> 3;
        updatePaletteByte(gbcSpritePaletteMemory, spritePalettes[palette], palette, (reg >> 1) & 0x3);
    }//end setSpritePalette

    /**
     * Tick the LCD.
     *
     * @param cycles The number of CPU cycles elapsed since the last call to tick.
     */
    public void tick(long cycles)
    {
        // Accumulate to an internal counter
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

            // We may be running headlessly, so we must check before drawing
            if (displayEnabled && core.display != null) draw(LY);

            // Increment LY, and wrap at 154 lines
            core.mmu.registers[R_LY] = (byte) (((LY + 1) % 154) & 0xff);

            if (LY == 0)
            {
                if (lastSecondTime == -1)
                {
                    lastSecondTime = System.nanoTime();
                    lastCoreCycle = core.cycle;
                }//end if
                currentVBlankCount++;
                if (currentVBlankCount == 60)
                {
                    System.out.println("Took " + ((System.nanoTime() - lastSecondTime) / 1_000_000_000.0) +
                            " seconds for 60 frames - " + (core.cycle - lastCoreCycle) / 60 + " clks/frames");
                    lastCoreCycle = core.cycle;
                    currentVBlankCount = 0;
                    lastSecondTime = System.nanoTime();
                }//end if
            }//end if

            boolean isVBlank = 144 <= LY;

            if (!isVBlank && core.mmu.hdma != null)
            {
                System.err.println(LY);
                core.mmu.hdma.tick();
            }//end if

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
                 *
                 * @{see http://bgb.bircd.org/pandocs.htm#lcdstatusregister}
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
                    }//end if
                }//end if

                if ((lcdStat & LCD_STAT.HBLANK_MODE_BIT) != 0)
                {
                    core.setInterruptTriggered(LCDC_BIT);
                }//end if
            }//end if

            /**
             * INT 40 - V-Blank Interrupt
             *
             * The V-Blank interrupt occurs ca. 59.7 times a second on a regular GB and ca. 61.1 times a second
             * on a Super GB (SGB). This interrupt occurs at the beginning of the V-Blank period (LY=144).
             * During this period video hardware is not using video ram so it may be freely accessed.
             * This period lasts approximately 1.1 milliseconds.
             *
             * @{see http://bgb.bircd.org/pandocs.htm#lcdinterrupts}
             */
            // use 143 here as we've just finished processing line 143 and will start 144
            if (LY == 143)
            {
                // Our renderer may have been invalidated, or we may be running headlessly
                Graphics2D graphics = currentRenderer != null ? currentRenderer.getGraphics() : null;

                // If we actually have a display, we should draw
                if (graphics != null)
                {

                    // Set the user's preferred interpolation method
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
                    }//end switch

                    // Blit the our buffer onto the display. The top bytes we use for tile priority won't show up.
                    graphics.drawImage(screenBuffer, 0, 0, core.display.getWidth(), core.display.getHeight(), null);
                }//end if

                // Trigger interrupts if the display is enabled
                if (displayEnabled)
                {
                    // Trigger VBlank
                    core.setInterruptTriggered(VBLANK_BIT);

                    // Trigger LCDC if enabled
                    if ((lcdStat & LCD_STAT.VBLANK_MODE_BIT) != 0)
                    {
                        core.setInterruptTriggered(LCDC_BIT);
                    }//end if
                }//end if
            }//end if
        }//end if
    }//end tick

    /**
     * Initializes renderers to draw on the current Emulator display.
     */
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
                } catch (ReflectiveOperationException ignored)
                {
                    continue;
                }//end try
                if (renderer.getGraphics() != null)
                {
                    add(renderer);
                    if (currentRenderer == null)
                    {
                        System.out.println("Using " + renderer);
                        currentRenderer = renderer;
                    }//end if
                } else
                {
                    System.err.println(renderer + " failed to produce a Graphics2D");
                }//end if
            }//end for
        }});
    }//end initializeRenderers

    /**
     * Draws a scanline.
     *
     * @param scanline The scanline to draw.
     */
    public void draw(int scanline)
    {
        // Don't even bother if the display is not enabled
        if (!displayEnabled()) return;

        // We still receive these calls for scanlines in vblank, but we can just ignore them
        if (scanline >= 144 || scanline < 0) return;

        // Reset our sprite counter
        spritesDrawnPerLine[scanline] = 0;

        /**
         * Obtain the backing array for the BufferedImage screenBuffer. In theory, we could use BufferedImage.setRGB
         * to draw, but this would be significantly more expensive than an array lookup.
         */
        DataBufferInt dbb = (DataBufferInt) screenBuffer.getRaster().getDataBuffer();
        int[] data = dbb.getData(0);

        // If we've reached the start of a frame, clear the current buffer
        if (scanline == 0)
            System.arraycopy(BLANK, 0, data, 0, data.length);

        // Draw the background if it's enabled
        if (backgroundEnabled())
            drawBackgroundTiles(data, scanline);

        // If sprites are enabled, draw them.
        if (spritesEnabled())
            drawSprites(data, scanline);

        // If the window appears in this scanline, draw it
        if (windowEnabled() && scanline >= getWindowPosY() && getWindowPosX() < W && getWindowPosY() >= 0)
            drawWindow(data, scanline);
    }//end draw

    /**
     * Attempt to draw background tiles.
     *
     * @param data     The raster to write to.
     * @param scanline The current scanline.
     */
    private void drawBackgroundTiles(int[] data, int scanline)
    {
        // Local reference to save time
        byte[] vram = core.mmu.vram;

        int tileDataOffset = getTileDataOffset();

        // The background is scrollable
        int scrollY = getScrollY();
        int scrollX = getScrollX();

        int y = (scanline + getScrollY() % 8) / 8;

        // Determine the offset into the VRAM tile bank
        int offset = getBackgroundTileMapOffset();

        /**
         * BG Map Tile Numbers
         * <pre>
         *      An area of VRAM known as Background Tile Map contains the numbers of tiles to
         *      be displayed. It is organized as 32 rows of 32 bytes each. Each byte contains a number
         *      of a tile to be displayed. Tile patterns are taken from the Tile Data Table located either
         *      at $8000-8FFF or $8800-97FF. In the first case, patterns are numbered with
         *      unsigned numbers from 0 to 255 (i.e. pattern #0 lies at address $8000). In the second case,
         *      patterns have signed numbers from -128 to 127 (i.e. pattern #0 lies at address $9000).
         *      The Tile Data Table address for the background can be selected via LCDC register.
         * </pre>
         *
         * @{see http://bgb.bircd.org/pandocs.htm#vrambackgroundmaps}
         */

        // 20 8x8 tiles fit in a 160px-wide screen
        for (int x = 0; x < 21; x++)
        {
            int addressBase = offset + ((y + scrollY / 8) % 32 * 32) + ((x + scrollX / 8) % 32);
            // add 256 to jump into second tile pattern table
            int tile = tileDataOffset == 0 ? vram[addressBase] & 0xff : vram[addressBase] + 256;

            // Tile attributes
            int gbcVramBank = 0;
            int gbcPalette = 0;
            boolean flipX = false;
            boolean flipY = false;

            /**
             * BG Map Attributes (CGB Mode only)
             * <pre>
             *      In CGB Mode, an additional map of 32x32 bytes is stored in VRAM Bank 1 (each byte defines attributes for the corresponding tile-number map entry in VRAM Bank 0):
             *
             *      Bit 0-2  Background Palette number  (BGP0-7)
             *      Bit 3    Tile VRAM Bank number      (0=Bank 0, 1=Bank 1)
             *      Bit 4    Not used
             *      Bit 5    Horizontal Flip            (0=Normal, 1=Mirror horizontally)
             *      Bit 6    Vertical Flip              (0=Normal, 1=Mirror vertically)
             *      Bit 7    BG-to-OAM Priority         (0=Use OAM priority bit, 1=BG Priority)
             * </pre>
             *
             * @{see http://bgb.bircd.org/pandocs.htm#vrambackgroundmaps}
             */
            if (core.cartridge.isColorGB)
            {
                int attribs = vram[Memory.VRAM_PAGESIZE + addressBase];

                if ((attribs & 0x8) != 0) gbcVramBank = 1;
                flipX = (attribs & 0x20) != 0;
                flipY = (attribs & 0x40) != 0;
                gbcPalette = (attribs & 0x7);
            }//end if

            // Delegate tile drawing
            drawTile(bgPalettes[gbcPalette],
                    data,
                    -(scrollX % 8) + x * 8, -(scrollY % 8) + y * 8,
                    tile,
                    scanline,
                    flipX, flipY,
                    gbcVramBank,
                    0,
                    false);
        }//end for
    }//end drawBackgroundTiles

    /**
     * Attempt to draw window tiles.
     *
     * @param data     The raster to write to.
     * @param scanline The current scanline.
     */
    private void drawWindow(int[] data, int scanline)
    {
        // Local reference to save time
        byte[] vram = core.mmu.vram;

        int tileDataOffset = getTileDataOffset();

        // The window layer is offset-able from 0,0
        int posX = getWindowPosX();
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

            /**
             * Same rules apply here as for background tiles.
             *
             * @{see http://bgb.bircd.org/pandocs.htm#vrambackgroundmaps}
             */
            if (core.cartridge.isColorGB)
            {
                int attribs = vram[Memory.VRAM_PAGESIZE + addressBase];
                if ((attribs & 0x8) != 0) gbcVramBank = 1;
                flipX = (attribs & 0x20) != 0;
                flipY = (attribs & 0x40) != 0;
                gbcPalette = (attribs & 0x07);
            }//end if

            drawTile(bgPalettes[gbcPalette],
                    data,
                    posX + x * 8, posY + y * 8,
                    tile,
                    scanline,
                    flipX, flipY,
                    gbcVramBank,
                    6 << 24,
                    false);
        }//end for
    }//end drawWindow

    /**
     * Attempt to draw a single line of a tile.
     *
     * @param palette      The palette currently in use.
     * @param data         An array of W * H elements, representing the LCD raster.
     * @param x            The x-coordinate of the tile.
     * @param y            The y-coordinate of the tile.
     * @param tile         The tile id to draw.
     * @param scanline     The current LCD scanline.
     * @param flipX        Whether the tile should be flipped vertically.
     * @param flipY        Whether the tile should be flipped horizontally.
     * @param bank         The tile bank to use.
     * @param basePriority The current priority for the given tile.
     * @param sprite       Whether the tile belongs to a sprite or not.
     */
    private void drawTile(IPalette palette, int[] data, int x, int y, int tile, int scanline,
                          boolean flipX, boolean flipY, int bank, int basePriority, boolean sprite)
    {
        // Store a local copy to save a lot of load opcodes.
        byte[] vram = core.mmu.vram;
        int line = scanline - y;
        int addressBase = Memory.VRAM_PAGESIZE * bank + tile * 16;

        // 8 pixel width
        for (int px = 0; px < 8; px++)
        {
            // Destination pixels
            int dx = x + px;

            // If we're out of bounds, continue iteration
            if (dx < 0 || dx >= W || scanline >= H)
                continue;

            // Check if our current priority should overwrite the current priority
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
        }//end for
    }//end drawTile

    /**
     * Attempts to draw all sprites.
     *
     * @param data     The raster to write to.
     * @param scanline The current scanline.
     */
    private void drawSprites(int[] data, int scanline)
    {
        // Hold local references to save a lot of load opcodes
        byte[] oam = core.mmu.oam;
        boolean tall = isUsingTallSprites();
        boolean isColorGB = core.cartridge.isColorGB;

        // Actual GameBoy hardware can only handle drawing 10 sprites per line
        // our code doesn't actually have this limitation, but we artificially introduce it by keeping
        // track of how many sprites are drawn per line
        for (int i = 0; i < oam.length && spritesDrawnPerLine[scanline] < 10; i += 4)
        {
            /**
             * Sprite attributes reside in the Sprite Attribute Table (OAM - Object Attribute Memory) at $FE00-FE9F.
             * Each of the 40 entries consists of four bytes with the following meanings:
             *
             * Byte0 - Y Position
             * <pre>
             *      Specifies the sprites vertical position on the screen (minus 16).
             *      An offscreen value (for example, Y=0 or Y>=160) hides the sprite.
             * </pre>
             *
             * Byte1 - X Position
             * <pre>
             *      Specifies the sprites horizontal position on the screen (minus 8).
             *      An offscreen value (X=0 or X>=168) hides the sprite, but the sprite
             *      still affects the priority ordering - a better way to hide a sprite is to set its Y-coordinate offscreen.
             * </pre>
             *
             * Byte2 - Tile/Pattern Number
             * <pre>
             *      Specifies the sprites Tile Number (00-FF). This (unsigned) value selects a tile from memory at 8000h-8FFFh.
             *      In CGB Mode this could be either in VRAM Bank 0 or 1, depending on Bit 3 of the following byte.
             *      In 8x16 mode, the lower bit of the tile number is ignored. Ie. the upper 8x8 tile is "NN AND FEh", and
             *      the lower 8x8 tile is "NN OR 01h".
             * </pre>
             *
             * Byte3 - Attributes/Flags:
             * <pre>
             *      Bit7   OBJ-to-BG Priority (0=OBJ Above BG, 1=OBJ Behind BG color 1-3)
             *      (Used for both BG and Window. BG color 0 is always behind OBJ)
             *      Bit6   Y flip          (0=Normal, 1=Vertically mirrored)
             *      Bit5   X flip          (0=Normal, 1=Horizontally mirrored)
             *      Bit4   Palette number  **Non CGB Mode Only** (0=OBP0, 1=OBP1)
             *      Bit3   Tile VRAM-Bank  **CGB Mode Only**     (0=Bank 0, 1=Bank 1)
             *      Bit2-0 Palette number  **CGB Mode Only**     (OBP0-7)
             * </pre>
             *
             * {@see http://bgb.bircd.org/pandocs.htm#vramspriteattributetableoam}
             */

            int y = oam[i] & 0xff;

            // Have we exited our bounds?
            if (!tall && !(y - 16 <= scanline && scanline < y - 8))
                continue;

            // TODO: things like 2 << 24 or 5 << 24 should have fields like P_2 and P_5
            byte attribs = oam[i + 3];
            int vrambank = (attribs & 0b1000) != 0 && isColorGB ? 1 : 0;
            int priority = (attribs & 0x80) != 0 ? 2 << 24 : 5 << 24;

            int x = oam[i + 1] & 0xff;
            int tile = oam[i + 2] & 0xff;
            boolean flipX = (attribs & 0x20) != 0;
            boolean flipY = (attribs & 0x40) != 0;
            int obp = isColorGB ? (attribs & 0x7) : (attribs >> 4) & 0x1;

            IPalette pal = spritePalettes[obp];

            // Handle drawing double sprites
            if (tall)
            {
                // If we're using tall sprites we actually have to flip the order that we draw the top/bottom tiles
                int hi = flipY ? (tile | 0x01) : (tile & 0xFE);
                int lo = flipY ? (tile & 0xFE) : (tile | 0x01);
                if (y - 16 <= scanline && scanline < y - 8)
                {
                    drawTile(pal, data, x - 8, y - 16, hi, scanline, flipX, flipY, vrambank, priority, true);
                    spritesDrawnPerLine[scanline]++;
                }//end if
                if (y - 8 <= scanline && scanline < y)
                {
                    drawTile(pal, data, x - 8, y - 8, lo, scanline, flipX, flipY, vrambank, priority, true);
                    spritesDrawnPerLine[scanline]++;
                }//end if
            } else
            {
                drawTile(pal, data, x - 8, y - 16, tile, scanline, flipX, flipY, vrambank, priority, true);
                spritesDrawnPerLine[scanline]++;
            }//end if
        }//end for
    }//end drawSprites

    /**
     * Determines whether the display is enabled from the LCDC register.
     *
     * @return The enabled state.
     */
    public boolean displayEnabled()
    {
        return (core.mmu.registers[R_LCDC] & LCDC.CONTROL_OPERATION_BIT) != 0;
    }//end displayEnabled

    /**
     * Determines whether the background layer is enabled from the LCDC register.
     *
     * @return The enabled state.
     */
    public boolean backgroundEnabled()
    {
        return (core.mmu.registers[R_LCDC] & LCDC.BGWINDOW_DISPLAY_BIT) != 0;
    }//end backgroundEnabled

    /**
     * Determines the window tile map offset from the LCDC register.
     *
     * @return The offset.
     */
    public int getWindowTileMapOffset()
    {
        if ((core.mmu.registers[R_LCDC] & LCDC.WINDOW_TILE_MAP_DISPLAY_SELECT_BIT) != 0)
            return 0x1c00;
        return 0x1800;
    }//end getWindowTileMapOffset

    /**
     * Determines the background tile map offset from the LCDC register.
     *
     * @return The offset.
     */
    public int getBackgroundTileMapOffset()
    {
        if ((core.mmu.registers[R_LCDC] & LCDC.BG_TILE_MAP_DISPLAY_SELECT_BIT) != 0)
            return 0x1c00;
        return 0x1800;
    }//end getBackgroundTileMapOffset

    /**
     * Determines whether tall sprites are enabled from the LCDC register.
     *
     * @return The enabled state.
     */
    public boolean isUsingTallSprites()
    {
        return (core.mmu.registers[R_LCDC] & LCDC.SPRITE_SIZE_BIT) != 0;
    }//end isUsingtallSprites

    /**
     * Determines whether sprites are enabled from the LCDC register.
     *
     * @return The enabled state.
     */
    public boolean spritesEnabled()
    {
        return (core.mmu.registers[R_LCDC] & LCDC.SPRITE_DISPLAY_BIT) != 0;
    }//end spritesEnabled

    /**
     * Determines whether the window is enabled from the LCDC register.
     *
     * @return The enabled state.
     */
    public boolean windowEnabled()
    {
        return (core.mmu.registers[R_LCDC] & LCDC.WINDOW_DISPLAY_BIT) != 0;
    }//end windowEnabled

    /**
     * Tile patterns are taken from the Tile Data Table located either at $8000-8FFF or $8800-97FF.
     * In the first case, patterns are numbered with unsigned numbers from 0 to 255 (i.e. pattern #0 lies at address $8000).
     * In the second case, patterns have signed numbers from -128 to 127 (i.e. pattern #0 lies at address $9000).
     * <p/>
     * The Tile Data Table address for the background can be selected via LCDC register.
     */
    public int getTileDataOffset()
    {
        if ((core.mmu.registers[R_LCDC] & LCDC.BGWINDOW_TILE_DATA_SELECT_BIT) != 0)
            return 0;
        return 0x0800;
    }//end getTileDataOffset

    /**
     * Fetches the current background X-coordinate from the WX register.
     *
     * @return The signed offset.
     */
    public int getScrollX()
    {
        return (core.mmu.registers[R_SCX] & 0xFF);
    }//end getScrollX

    /**
     * Fetches the current background Y-coordinate from the SCY register.
     *
     * @return The signed offset.
     */
    public int getScrollY()
    {
        return (core.mmu.registers[R_SCY] & 0xff);
    }//end getScrollY

    /**
     * Fetches the current window X-coordinate from the WX register.
     *
     * @return The unsigned offset.
     */
    public int getWindowPosX()
    {
        return (core.mmu.registers[R_WX] & 0xFF) - 7;
    }//end getWindowPosX

    /**
     * Fetches the current window Y-coordinate from the WY register.
     *
     * @return The unsigned offset.
     */
    public int getWindowPosY()
    {
        return (core.mmu.registers[R_WY] & 0xFF);
    }//end getWindowPosY
}//end class LCD
