/*
 *  Copyright (C) 2016 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package malt.io.experimental;

import jloda.map.ByteFileGetterMappedMemory;
import jloda.map.IByteGetter;
import jloda.util.Basic;
import jloda.util.CanceledException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

/**
 * byte file getter using paged memory
 * Daniel Huson, 5.2015
 */
public class ByteFileGetterPagedMemory implements IByteGetter {
    private final File file;
    private final RandomAccessFile raf;

    private final byte[][] data;
    private final long limit;
    private final int length0;

    private final int PAGE_BITS = 20; // 2^20=1048576
    private int pages = 0;

    /**
     * constructor
     *
     * @param file
     * @throws IOException
     * @throws CanceledException
     */
    public ByteFileGetterPagedMemory(File file) throws IOException {
        this.file = file;
        limit = file.length();

        System.err.println("Opening file: " + file);
        raf = new RandomAccessFile(file, "r");

        data = new byte[(int) ((limit >> PAGE_BITS)) + 1][];
        length0 = (int) (Math.min(limit, 1 << PAGE_BITS));
    }

    /**
     * bulk get
     *
     * @param index
     * @param bytes
     * @param offset
     * @param len
     * @return
     */
    @Override
    public int get(long index, byte[] bytes, int offset, int len) throws IOException {
        synchronized (raf) {
            raf.seek(index);
            len = raf.read(bytes, offset, len);
        }
        return len;
    }

    /**
     * gets value for given index
     *
     * @param index
     * @return value or 0
     */
    @Override
    public int get(long index) throws IOException {
        int dIndex = dataIndex(index);
        byte[] array = data[dIndex];
        if (array == null) {
            synchronized (raf) {
                if (data[dIndex] == null) {
                    pages++;
                    final int length;
                    if (dIndex == data.length - 1)
                        length = (int) (limit - (data.length - 1) * length0); // is the last chunk
                    else
                        length = length0;

                    array = new byte[length];
                    long toSkip = (long) dIndex * (long) length0;
                    raf.seek(toSkip);
                    raf.read(array, 0, array.length);
                    data[dIndex] = array;
                } else
                    array = data[dIndex];
            }
        }
        return array[dataPos(index)];
    }

    /**
     * gets next four bytes as a single integer
     *
     * @param index
     * @return integer
     */
    @Override
    public int getInt(long index) throws IOException {
        return ((get(index++) & 0xFF) << 24) + ((get(index++) & 0xFF) << 16) + ((get(index++) & 0xFF) << 8) + ((get(index) & 0xFF));
    }

    /**
     * length of array
     *
     * @return array length
     * @throws IOException
     */
    @Override
    public long limit() {
        return limit;
    }

    /**
     * close the array
     */
    @Override
    public void close() {
        try {
            raf.close();
            System.err.println("XXX Closing file: " + file.getName() + " (" + pages + "/" + data.length + " pages)");
        } catch (IOException e) {
            Basic.caught(e);
        }
    }

    private int dataIndex(long index) {
        return (int) ((index >> PAGE_BITS));
    }

    private int dataPos(long index) {
        return (int) (index - (index >> PAGE_BITS) * length0);
    }

    public static void main(String[] args) throws IOException {
        File file = new File("/Users/huson/data/ma/protein/index-new/table0.idx");

        final IByteGetter oldGetter = new ByteFileGetterMappedMemory(file);
        final IByteGetter newGetter = new ByteFileGetterPagedMemory(file);

        final Random random = new Random();
        System.err.println("Limit: " + oldGetter.limit());
        for (int i = 0; i < 1000; i++) {
            int r = random.nextInt((int) oldGetter.limit());

            int oldValue = oldGetter.get(r);
            int newValue = newGetter.get(r);

            System.err.println(r + ": " + oldValue + (oldValue == newValue ? " == " : " != ") + newValue);
        }
        oldGetter.close();
        newGetter.close();
    }
}
