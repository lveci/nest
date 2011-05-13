package org.jdoris.core.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteOrder;

public final class FlatBinaryInt extends FlatBinary {

    int [][] data;
    private int lines;
    private int pixels;

    public FlatBinaryInt() {
        this.byteOrder = ByteOrder.BIG_ENDIAN;
    }

    public void setData(int[][] data) {
        this.data = data;
    }


    @Override
    public void readFromStream() throws FileNotFoundException {

        setLinesPixels();

        data = new int[lines][pixels];
        for (int i = 0; i < lines; i++) {
            for (int j = 0; j < pixels; j++) {
                try {
                    if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                        data[i][j] = ByteSwapper.swap(inStream.readInt());
                    } else {
                        data[i][j] = inStream.readInt();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    @Override
    public void writeToStream() throws FileNotFoundException {

        setLinesPixels();

        for (int i = 0; i < lines; i++) {
            for (int j = 0; j < pixels; j++) {
                try {
                    if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                        outStream.writeInt(ByteSwapper.swap(data[i][j]));
                    } else {
                        outStream.writeInt(data[i][j]);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            this.outStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setLinesPixels() {
        lines = (int) dataWindow.lines();
        pixels = (int) dataWindow.pixels();
    }

}
