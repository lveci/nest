package org.esa.nest.dataio;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * A reader for reading binary files 
 *
 */
public final class BinaryFileReader {

    private static final String EM_EXPECTED_X_FOUND_Y_BYTES = "Expected bytes to read %d, but only found %d";
    private static final String EM_READING_X_TYPE = "Reading '%s'-Type";
    private static final String EM_NOT_PARSABLE_X_STRING = "Not able to parse %s string";

    private final ImageInputStream _stream;

    public BinaryFileReader(final ImageInputStream stream) {
        _stream = stream;
    }

    public void close() throws IOException {
        _stream.close();
    }

    public void seek(final long pos) throws IOException {
        _stream.seek(pos);
    }

    public void skipBytes(final long numBytes) throws IOException {
        _stream.skipBytes(numBytes);
    }

    public int readB1() throws IOException, IllegalBinaryFormatException {
        final long streamPosition = _stream.getStreamPosition();
        try {
            return _stream.readByte() & 0xFF;
        } catch (IOException e) {
            final String message = String.format(EM_READING_X_TYPE, new Object[]{"B1"});
            throw new IllegalBinaryFormatException(message, streamPosition, e);
        }
    }

    public short readB2() throws IOException, IllegalBinaryFormatException {
        final long streamPosition = _stream.getStreamPosition();
        try {
            return _stream.readShort();
        } catch (IOException e) {
            final String message = String.format(EM_READING_X_TYPE, new Object[]{"B2"});
            throw new IllegalBinaryFormatException(message, streamPosition, e);
        }
    }

    public int readB4() throws IOException, IllegalBinaryFormatException {
        final long streamPosition = _stream.getStreamPosition();
        try {
            return _stream.readInt();
        } catch (IOException e) {
            final String message = String.format(EM_READING_X_TYPE, new Object[]{"B4"});
            throw new IllegalBinaryFormatException(message, streamPosition, e);
        }
    }

    public long readB8() throws IOException, IllegalBinaryFormatException {
        final long streamPosition = _stream.getStreamPosition();
        try {
            return _stream.readLong();
        } catch (IOException e) {
            final String message = String.format(EM_READING_X_TYPE, new Object[]{"B8"});
            throw new IllegalBinaryFormatException(message, streamPosition, e);
        }
    }

    public void read(final byte[] array) throws IOException {
            _stream.readFully(array, 0, array.length);
    }

    public void read(final char[] array) throws IOException {
            _stream.readFully(array, 0, array.length);
    }

    public void read(final short[] array) throws IOException {
            _stream.readFully(array, 0, array.length);
    }

    public void read(final int[] array) throws IOException {
            _stream.readFully(array, 0, array.length);
    }

    public void read(final long[] array) throws IOException, IllegalBinaryFormatException {
            _stream.readFully(array, 0, array.length);
    }

    public void read(final float[] array) throws IOException {
            _stream.readFully(array, 0, array.length);
    }

    public void read(final double[] array) throws IOException {
            _stream.readFully(array, 0, array.length);
    }

    public long readIn(final int n) throws IOException, IllegalBinaryFormatException {
        final long streamPosition = _stream.getStreamPosition();
        final String longStr = readAn(n).trim();
        if(longStr.isEmpty()) return 0;
        return parseLong(longStr, streamPosition);
    }

    private static long parseLong(String integerStr, long streamPosition) throws IllegalBinaryFormatException {
        long number;
        try {
            number = Long .parseLong(integerStr);
        } catch (NumberFormatException e) {

            final String newStr = createIntegerString(integerStr,
                new char[]{'.'}, ' ').trim();
            try {
                number = Long .parseLong(newStr);
            } catch (NumberFormatException e2) {
                final String message = String.format(EM_NOT_PARSABLE_X_STRING + " \"" + integerStr + '"',
                                                                    new Object[]{"integer"});
                throw new IllegalBinaryFormatException(message, streamPosition, e);
            }
        }
        return number;
    }

    public double readFn(final int n) throws IOException, IllegalBinaryFormatException {
        final long streamPosition = _stream.getStreamPosition();
        final String doubleString = readAn(n).trim();
        if(doubleString.isEmpty()) return 0;
        try {
            return Double.parseDouble(doubleString);
        } catch (NumberFormatException e) {
            final String message = String.format(EM_NOT_PARSABLE_X_STRING, new Object[]{"double"});
            throw new IllegalBinaryFormatException(message, streamPosition, e);
        }
    }

    public void readFn(final int n, final double[] numbers) throws IOException, IllegalBinaryFormatException {
        final long streamPosition = _stream.getStreamPosition();
        for (int i = 0; i < numbers.length; i++) {
            try {
                numbers[i] = Double.parseDouble(readAn(n).trim());
            } catch (IllegalBinaryFormatException e) {
                final String message = String.format(EM_READING_X_TYPE, new Object[]{"Gn[]"});
                throw new IllegalBinaryFormatException(message, streamPosition, e);
            }
        }
    }

    public String readAn(final int n) throws IOException, IllegalBinaryFormatException {
        final long streamPosition = _stream.getStreamPosition();
        final byte[] bytes = new byte[n];
        final int bytesRead;
        try {
            bytesRead = _stream.read(bytes);
        } catch (IOException e) {
            final String message = String.format(EM_READING_X_TYPE, new Object[]{"An"});
            throw new IllegalBinaryFormatException(message, streamPosition, e);
        }
        if (bytesRead != n) {
            final String message = String.format(EM_EXPECTED_X_FOUND_Y_BYTES,
                                                 n, bytesRead);
            throw new IllegalBinaryFormatException(message, streamPosition);
        }
        String str = new String(bytes);
        if(str.contains("\0"))
            return str.replace("\0", " ");

        return str;
    }

    public int[] readInArray(final int arraySize, final int intValLength)
            throws IOException, IllegalBinaryFormatException {
        final long streamPosition = _stream.getStreamPosition();
        final int[] ints = new int[arraySize];
        for (int i = 0; i < ints.length; i++) {
            final String integerString = readAn(intValLength).trim();
            if (integerString.length() > 0) {
                ints[i] = (int) parseLong(integerString, streamPosition + i * intValLength);
            }
        }
        return ints;
    }

    public long getCurrentPos() throws IOException {
        return _stream.getStreamPosition();
    }

    public long getLength() throws IOException {
        return _stream.length();
    }

    private static String createIntegerString(String name, char[] validChars, char replaceChar) {
        char[] sortedValidChars = null;
        if (validChars == null) {
            sortedValidChars = new char[0];
        } else {
            sortedValidChars = validChars.clone();
        }
        Arrays.sort(sortedValidChars);
        StringBuilder validName = new StringBuilder(name.length());
        boolean pad = false;
        for (int i = 0; i < name.length(); i++) {
            final char ch = name.charAt(i);
            if (!pad && Character.isDigit(ch)) {
                validName.append(ch);
            } else if (!pad && Arrays.binarySearch(sortedValidChars, ch) >= 0) {
                validName.append(ch);
            } else {
                pad = true;
                validName.append(replaceChar);
            }
        }
        return validName.toString();
    }
}