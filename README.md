# Nitrous Emulator

Nitrous is a high-performance Gameboy Color emulator aimed at squeezing every last drop of performance out of the Java runtime.

<table>
<tr>
<td>
<img src="https://i.imgur.com/1Mw3mXu.gif" width="256px">
</td>
<td>
<img src="https://i.imgur.com/jEUTg6Y.gif" width="256px">
</td>
<td>
<img src="https://i.imgur.com/bXffYY5.gif" width="256px">
</td>
</tr>
</table>

It supports many popular games to a playable extent, including:
* Pokemon Silver/Gold/Crystal
* The Legend of Zelda: Oracle of Ages
* Super Mario Bros. Deluxe

You may download Nitrous from [the release tab](https://github.com/Xyene/Nitrous-Emulator/releases), or build it yourself from the source.

The code is extensively documented, with excerpts and references to the [Pandocs](http://bgb.bircd.org/pandocs.htm) where appropriate,
so it can be used as educational material for those interested in learning about Gameboy emulation.

## Performance and Compatibility
For extra rendering speed, Nitrous bypasses the Java2D API to obtain rendering contexts for the various pipelines Java2D supports simultaneously (e.g., on Windows you can switch whether Nitrous should render using GDI, Direct3D or OpenGL at runtime). Though this allows for higher performance by reducing the abstraction of rendering, it comes at the cost of portability.

Currently, Nitrous has been tested on Windows 8 and 10 using Java 8 and 9, and briefly on Ubuntu 16.04 running Oracle's Java 8 binaries. Your mileage may vary with other setups, though Nitrous does make a reasonable attempt to fall back to well-supported APIs. 
