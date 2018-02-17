# bin2png
This class is a utility to insert binary data from a file into a PNG image and export the image to a file without keeping the whole binary data (or the whole image) in memory at any given time. This class also implements the reverse process, i.e. it can extract the binary data from a PNG file, and output the binary data to a file, again without ever keeping the whole image (or the binary data) in memory at any given time. 

When embedding the binary data into the PNG, the 

**Binary to PNG:** The binary data is inserted as the color information of the PNG image. Each 3-byte block of the binary data is interpreted as an RGB value of a pixel using the ARGB color model, where the alpha channel is always 0xFF. In case the # of bytes in binary data is not a multiple of 3, the last two bytes, or the last one byte, are interpreted as two colors, or one color, with the alpha channel 0xFE, or 0xFD, respectively. The rest of the pixel data is simply for padding purposes with alpha channel 0x00, so that the image is rectangular with a default width of 32 KiB (32768) and calculated height such that 3*height*width ≥ # of bytes. The binary data is read in the same order as it is found in the file.

**PNG to binary:** The use of the alpha channel allows the extraction utility to differentiate between pixels that contain data (alpha=0xFF, 0xFE, 0xFD, depending on if there are 3, 2 or 1 bytes of data embedded in the pixel) and those pixels that are simply there to pad the image to provide it with a rectangular shape (alpha=0x00). The binary data is writtend in the same order as it is found in the image.

The class can be downloaded with

_git clone https://github.com/iagustsson/bin2png_,

compiled with

_javac -d mods/com.iagustsson.bin2png src/com.iagustsson.bin2png/module-info.java src/com.iagustsson.bin2png/com/iagustsson/bin2png/Bin2PNG.java src/com.iagustsson.bin2png/com/iagustsson/bin2png/SimpleRenderedImage.java_,

and run by

_java --module-path mods -m com.iagustsson.bin2png/com.iagustsson.bin2png.Bin2PNG_
