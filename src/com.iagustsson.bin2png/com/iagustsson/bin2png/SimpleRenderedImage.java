package com.iagustsson.bin2png;

//import java.awt.*;

import java.awt.*;
import java.awt.image.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Vector;

public class SimpleRenderedImage implements RenderedImage {
    final ColorModel cm;
    final SampleModel sm;
    final int width;
    final int height;
    final int[] pixels;
    final SeekableByteChannel sbc;
    final ByteBuffer bf;
    SimpleRenderedImage(SeekableByteChannel sbc, int width, int height, ColorModel cm, SampleModel sm) {
        this.sbc = sbc;
        this.width = width;
        this.height = height;
        this.cm = cm;
        this.sm = sm;

        pixels = new int[width];
        bf = ByteBuffer.allocateDirect(3*width);

    }
    @Override
    public Raster getData(Rectangle rect) {
        int nRead;
        try {
            bf.rewind();
            nRead = sbc.read(bf);
        } catch (IOException ioe) {
            ioe.printStackTrace(System.err);
            nRead = 0;
        }
        
        for (int i = 0, j = 0; j < pixels.length; i += 3, ++j) {
            int color = 0;
            if (i < nRead) {
                color = (0xFF & bf.get(i));
                color <<= 8;
                if (i + 1 < nRead) {
                    color |= (0xFF & bf.get(i + 1));
                    color <<= 8;
                    if (i + 2 < nRead) {
                        color |= (0xFF & bf.get(i + 2));
                        color |= 0xFF000000;
                    } else {
                        color |= 0xFE000000;
                    }
                } else {
                    color <<= 8;
                    color |= 0xFD000000;
                }
            }
            pixels[j] = color;
        }
        SampleModel sampleModel = cm.createCompatibleSampleModel(width, rect.height);
        return Raster.createRaster(sampleModel, new DataBufferInt(pixels, pixels.length), rect.getLocation());
    }

    @Override
    public ColorModel getColorModel() {
        return cm;
    }

    @Override
    public SampleModel getSampleModel() {
        return sm;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinX() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    // all other methods (see below) throw UnsupportedOperationException

    @Override
    public Vector<RenderedImage> getSources() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public Object getProperty(String name) {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public String[] getPropertyNames() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public int getNumXTiles() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public int getNumYTiles() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public int getMinTileX() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public int getMinTileY() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public int getTileWidth() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public int getTileHeight() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public int getTileGridXOffset() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public int getTileGridYOffset() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public Raster getTile(int tileX, int tileY) {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public Raster getData() {
        throw new UnsupportedOperationException("Method not implemented!");
    }

    @Override
    public WritableRaster copyData(WritableRaster raster) {
        throw new UnsupportedOperationException("Method not implemented!");
    }
}