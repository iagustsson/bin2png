package com.iagustsson.bin2png;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.Checksum;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;


/**
 * This class is a utility to insert binary data from a file into a PNG image
 * and export the image to a file without keeping the whole binary data (or the
 * whole image) in memory at any given time. This class also implements the
 * reverse process, i.e. it can extract the binary data from a PNG file, and
 * output the binary data to a file, again without ever keeping the whole image
 * (or binary data) in memory at any given time.
 * <p>
 *
 * <p>
 * Binary to PNG: The binary data is inserted as the color information of the
 * PNG image. Each 3-byte block of the binary data is interpreted as an RGB value
 * of a pixel using the ARGB color model, where the alpha channel is always 0xFF.
 * In case the # of bytes in binary data is not a multiple of 3, the last two
 * bytes, or the last one byte, are interpreted as two colors, or one color, with
 * the alpha channel 0xFE, or 0xFD, respectively. The rest of the pixel data
 * is simply for padding purposes with alpha channel 0x00, so that the image is
 * rectangular with a default width of 32 KiB (32768) and calculated height such
 * that 3*height*width ≥ # of bytes. The binary data is read in the same order
 * as it is found in the file.
 * <p>
 * PNG to binary: The use of the alpha channel allows the extraction utility to
 * differentiate between pixels that contain data (alpha=0xFF, 0xFE, 0xFD,
 * depending on if there are 3, 2 or 1 bytes of data embedded in the pixel) and
 * those pixels that are simply there to pad the image to provide it with a
 * rectangular shape (alpha=0x00). The binary data is writtend in the same
 * order as it is found in the image.
 * <p>
 * No preexisting files are every overwritten by this utility.
 *
 * @author Ingolfur Agustsson
 * @version 1.0 02/14/18
 */
public class Bin2PNG {
    // compression window size of not more than 32K
    // PNG compression method 0 is deflate/inflate compression with a sliding
    // compression window size of not more than 32 KiB (32768 bytes)
    // (which is an upper bound on the distances appearing in the deflate stream)
    private static final int width = (1 << 15); //32768

    static void copyBinary2Image(String binFileName, String pngFileName, boolean verboseFlag) throws IOException {
        File inBinFile = new File(binFileName);
        File outImgFile = new File(pngFileName);

        if (!inBinFile.exists()) {
            throw new IOException("ERROR: The provided binary file does not exists, using the given file-path: " + inBinFile.getAbsolutePath());
        }
        if (outImgFile.exists()) {
            throw new IOException("ERROR: Nothing is overwritten and the given PNG file already exists with file-path: " + outImgFile.getAbsolutePath());
        }

        try (SeekableByteChannel sbc = Files.newByteChannel(Paths.get(inBinFile.getAbsolutePath()))) {
            final long fileSize = sbc.size();
            // choose height such that 3*height*width exceeds fileSize
            final int height = (int) (fileSize / (3L * width) + ((fileSize % (3L * width) != 0) ? 1 : 0));//+1;

            // create a PNG writer
            ImageWriter writer = ImageIO.getImageWritersBySuffix("PNG").next();
            ImageOutputStream imageStream = ImageIO.createImageOutputStream(outImgFile);
            writer.setOutput(imageStream);

            final DirectColorModel mainColorModel = (DirectColorModel) ColorModel.getRGBdefault();
            final SampleModel mainSampleModel = mainColorModel.createCompatibleSampleModel(width, 1);

            // create a simple-render-image that simply provides the pixel data from the given
            // byte-channel as needed, without loading the whole binary file into memory
            RenderedImage ri = new SimpleRenderedImage(sbc, width, height, mainColorModel, mainSampleModel);

            IIOImage iioimage = new IIOImage(ri, null, null);
            if (verboseFlag) {
                System.err.println("# of bytes in the given binary file (file-path: " + inBinFile.getAbsolutePath() + ") is " + fileSize);
                System.err.println("The default width=" + width + " and the height=" + height + " is calculated to ensure");
                System.err.println("that 3*height*width ≥ # of bytes, i.e. " + 3L * height * width + " ≥ " + fileSize + ".");
                System.err.println("Now writing out the content of the binary file to the image file, (file-path: " + outImgFile.getAbsolutePath());
            }
            writer.write(iioimage);
        } catch (IOException ioe) {
            throw new IOException("ERROR: Cannot write the PNG file!");
        }
    }

    static void extractBinaryFromImage(String pngFileName, String outFileName, boolean verboseFlag) throws ZipException, IOException {
        File inImgFile = new File(pngFileName);

        File outFile = new File(outFileName);
        if (!inImgFile.exists()) {
            throw new IOException("ERROR: The provided image file does not exists, using the given file-path: " + inImgFile.getAbsolutePath());
        }
        if (outFile.exists()) {
            throw new IOException("ERROR: Nothing is overwritten and the given output file already exists with file-path: " + outFile.getAbsolutePath());
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(inImgFile));
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile))) {

            // the chunk-length never exceeds 32768
            byte[] chunk = new byte[0x8000];
            Checksum crc32 = new CRC32();

            // read all the header information from the PNG file

            // The first eight bytes of a PNG datastream always contain the following (decimal) values:
            // 137 80 78 71 13 10 26 10
            // or in hex
            // 0x89504e470d0a1a0a
            final long defaultSignaturePNG = 0x89504e470d0a1a0aL;
            byte[] signaturePNG = new byte[8];
            dis.readFully(signaturePNG);
            if (ByteBuffer.wrap(signaturePNG).getLong() != defaultSignaturePNG) {
                throw new IOException("Bad PNG signature " + signaturePNG);
            }
            int chunkLength = dis.readInt();
            if (chunkLength != 0xd) {
                System.err.println("Bad length for IHDR chunk!");
                System.exit(1);
            }
            int chunkType = dis.readInt();
            if (chunkType != ByteBuffer.wrap("IHDR".getBytes()).getInt()) {
                throw new IOException("Bad type for IHDR chunk!");
            }

            // read the IHDR
            dis.read(chunk, 0, chunkLength);

            // read the image paramters
            final int imgWidth = ByteBuffer.wrap(chunk, 0, 4).getInt();
            final int imgHeight = ByteBuffer.wrap(chunk, 4, 4).getInt();

            int imgBitDepth = chunk[8];
            int imgColorType = chunk[9];
            int imgCompressionMethod = chunk[10];
            int imgFilterMethod = chunk[11];
            int imgInterlaceMethod = chunk[12];

            // imgColorType=6, has 4 input passes rgb + alpha
            if (imgColorType != 6) {
                throw new IOException("Bad type for ColorType!");
            }
            // ARGB model has 4 bands
            final int imgInputBands = 4;

            int chunkCRC = dis.readInt();
            crc32.update(ByteBuffer.wrap(new byte[Integer.BYTES]).putInt((chunkType)).array());
            crc32.update(chunk, 0, chunkLength);
            int calcCRC = (int) crc32.getValue();
            if (chunkCRC != calcCRC) {
                //String hexCRC=Long.toHexString(crc32.getValue()));
                throw new IOException("CRC=" + Long.toHexString(chunkCRC) + " for the IHDR chunk does not equal to calculated one (calculated is " + Long.toHexString(calcCRC) + ")!");
            }
            // done reading all the header information from the PNG file

            // the # of bytes in a row, plus the filter byte
            int bytesPerRow = (imgInputBands * imgWidth * imgBitDepth + 7) / 8 + 1;

            if (verboseFlag) {
                System.err.println("Done reading the header information from the image file, file-path=" + inImgFile.getAbsolutePath());
                System.err.println("imgWidth=" + imgWidth + " imgHeight=" + imgHeight);
                System.err.println("imgBitDepth=" + imgBitDepth + " imgColorType=" + imgColorType);
                System.err.println("chunkCRC=" + Integer.toHexString(chunkCRC) + " calcCRC=" + Integer.toHexString(calcCRC));
                System.err.println("bytesPerRow=" + bytesPerRow);
            }

            // the chunk-length never exceeds 32768
            byte[] argb = new byte[bytesPerRow];
            Inflater inf = new Inflater();

            int off = 0;
            while (!inf.finished()) {
                if (inf.needsInput()) {
                    // every chunk starts with its length and its type
                    chunkLength = dis.readInt();
                    chunkType = dis.readInt();

                    // fill the input array, one chunk at a time,
                    // since the the maximum length of chunks
                    // equals the maximum length of zlib block
                    int numRead = dis.read(chunk, 0, chunkLength);
                    if (numRead != -1) {
                        inf.setInput(chunk, 0, numRead);
                    }
                    if (numRead != chunkLength) {
                        throw new ZipException("Cannot read the next chunk from the PNG file, needed " + chunkLength + " but only " + numRead + " could be read!");
                    }
                    // every chunk ends with its CRC
                    chunkCRC = dis.readInt();

                    crc32.reset();
                    crc32.update(ByteBuffer.wrap(new byte[Integer.BYTES]).putInt((chunkType)).array());
                    crc32.update(chunk, 0, numRead);
                    calcCRC = (int) crc32.getValue();
                    if (chunkCRC != calcCRC) {
                        throw new ZipException("CRC=" + Long.toHexString(chunkCRC) + " for the IHDR chunk does not equal to calculated one (calculated is " + Long.toHexString(calcCRC) + ")!");
                    }
                }
                // inflate the input
                int numDecompressed = inf.inflate(argb, off, argb.length - off);
                assert (numDecompressed > 0);

                off += numDecompressed;

                if (off != argb.length) continue;
                // when this point is reached, argb is full
                off = 0;

                int filter = argb[0];
                if (filter != 0) {
                    throw new ZipException("When reading the uncompressed data, the filter (filter=" + filter + ") is not 0!");
                }
                for (int bi = 1; bi < bytesPerRow - 1; bi += 4) {
                    if (argb[bi + 3] == (byte) 0xFF) {
                        bos.write(argb[bi]);
                        bos.write(argb[bi + 1]);
                        bos.write(argb[bi + 2]);
                    } else if (argb[bi + 3] == (byte) 0xFE) {
                        bos.write(argb[bi]);
                        bos.write(argb[bi + 1]);
                    } else if (argb[bi + 3] == (byte) 0xFD) {
                        bos.write(argb[bi]);
                    }
                }
            }
        } catch (IOException ioe) {
            throw new IOException("ERROR: Cannot read the binary data from the PNG file!", ioe);
        } catch (DataFormatException dfe) {
            throw new IOException("ERROR: Invalid format of the compressed data in the PNG file!");
        }
    }

    public static void main(String[] args) {
        boolean verboseFlag = false;
        String binFileName = null;
        String pngFileName = null;
        String outFileName = null;

        if (args.length == 0) {
            System.err.print("Copies binary data (from 'binfile') as pixels into a PNG image file (to 'pngfile'), ");
            System.err.println("and extracts the binary data from a PNG image file (from 'pngfile') to a binary (to 'outfile').");
            System.err.println("Usage: bin2png [-verbose] -bin binfile -png pngfile -out outfile");
            System.exit(0);
        }

        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-v") || args[i].equals("-verbose")) {
                verboseFlag = true;
            } else if (args[i].equals("-bin")) {
                if (i + 1 < args.length) {
                    binFileName = args[i + 1];
                } else {
                    System.err.println("ERROR: -bin requires a filename for the binary file");
                    System.exit(1);
                }
            } else if (args[i].equals("-png")) {
                if (i + 1 < args.length) {
                    pngFileName = args[i + 1];
                } else {
                    System.err.println("ERROR: -png requires a filename for the image file");
                    System.exit(1);
                }
            } else if (args[i].equals("-out")) {
                if (i + 1 < args.length) {
                    outFileName = args[i + 1];
                } else {
                    System.err.println("ERROR: -out requires a filename for the output file");
                    System.exit(1);
                }
            }
        }

        if (binFileName != null && pngFileName != null) {
            try {
                copyBinary2Image(binFileName, pngFileName, verboseFlag);
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
                if (ioe.getCause() != null) {
                    ioe.getCause().printStackTrace(System.err);
                }
            }
        }

        if (pngFileName != null && outFileName != null) {
            try {
                extractBinaryFromImage(pngFileName, outFileName, verboseFlag);
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
                if (ioe.getCause() != null) {
                    ioe.getCause().printStackTrace(System.err);
                }
            }
        }

    }
}
