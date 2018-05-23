/*
 *  Copyright (C) 2015 Daniel H. Huson
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

package malt.tools;

import jloda.util.*;
import malt.genes.GeneItem;
import megan.io.FileRandomAccessReadOnlyAdapter;
import megan.io.InputReader;
import megan.util.interval.Interval;
import megan.util.interval.IntervalTree;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * add functional annotations to DNA alignments
 * Daniel Huson, 5.2018
 */
public class AAddRun {
    /**
     * add functional annotations to DNA alignments
     */
    public static void main(String[] args) {
        try {
            ProgramProperties.setProgramName("AAddRun");
            ProgramProperties.setProgramVersion(megan.main.Version.SHORT_DESCRIPTION);

            PeakMemoryUsageMonitor.start();
            (new AAddRun()).run(args);
            System.err.println("Total time:  " + PeakMemoryUsageMonitor.getSecondsSinceStartString());
            System.err.println("Peak memory: " + PeakMemoryUsageMonitor.getPeakUsageString());
            System.exit(0);
        } catch (Exception ex) {
            Basic.caught(ex);
            System.exit(1);
        }
    }

    /**
     * run the program
     */
    public void run(String[] args) throws CanceledException, IOException, UsageException {
        final ArgsOptions options = new ArgsOptions(args, this, "Adds functional annotations to DNA alignments");
        options.setVersion(ProgramProperties.getProgramVersion());
        options.setLicense("Copyright (C) 2018 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");
        options.setAuthors("Daniel H. Huson");

        options.comment("Input Output");
        final String[] inputFiles = options.getOptionMandatory("-i", "input", "Input SAM file(s) (.gz ok)", new String[0]);
        final String indexDirectory = options.getOptionMandatory("-d", "index", "AAdd index directory", "");
        final String[] outputFiles = options.getOptionMandatory("-o", "output", "Output file(s) (.gz ok) or directory", new String[0]);
        //options.comment(ArgsOptions.OTHER);
        options.done();

        final File outputDir;
        if (outputFiles.length == 1 && ((new File(outputFiles[0])).isDirectory())) {
            outputDir = new File(outputFiles[0]);
        } else {
            outputDir = null;
            if (inputFiles.length != outputFiles.length)
                throw new UsageException("Number of output files doesn't match number of input files");
        }

        final Map<String, Pair<Long, IntervalTree<GeneItem>>> ref2PosAndTree;
        final File indexFile = new File(indexDirectory, "aadd.idx");
        final File dbFile = new File(indexDirectory, "aadd.dbx");
        try (InputReader ins = new InputReader(indexFile); ProgressPercentage progress = new ProgressPercentage("Reading file: " + indexFile)) {
            readAndVerifyMagicNumber(ins, AAddBuild.MAGIC_NUMBER_IDX);
            final int entries = ins.readInt();

            ref2PosAndTree = new HashMap<>((int) (1.2 * entries));

            for (int t = 0; t < entries; t++) {
                final String dnaId = ins.readString();
                final long pos = ins.readLong();
                ref2PosAndTree.put(dnaId, new Pair<Long, IntervalTree<GeneItem>>(pos, null));
            }
        }

        final IntervalTree<GeneItem> emptyTree = new IntervalTree<>();


        try (FileRandomAccessReadOnlyAdapter dbxIns = new FileRandomAccessReadOnlyAdapter(dbFile)) {

            for (int i = 0; i < inputFiles.length; i++) {
                File inputFile = new File(inputFiles[i]);
                final File outputFile;
                if (outputDir != null) {
                    outputFile = new File(outputDir, inputFile.getName() + ".out");
                } else
                    outputFile = new File(outputFiles[i]);
                if (inputFile.equals(outputFile))
                    throw new IOException("Input file equals output file: " + inputFile);
                final boolean gzipOutput = outputFile.getName().toLowerCase().endsWith(".gz");

                long countLines = 0;
                long countAlignments = 0;
                long countAnnotated = 0;
                long countReferencesLoaded = 0;


                try (final FileInputIterator it = new FileInputIterator(inputFile, true);
                     final BufferedWriter w = new BufferedWriter(new OutputStreamWriter(gzipOutput ? new GZIPOutputStream(new FileOutputStream(outputFile)) : new FileOutputStream(outputFile)))) {
                    System.err.println("Writing file: " + outputFile);

                    while (it.hasNext()) {
                        final String aLine = it.next();
                        if (aLine.startsWith("@"))
                            w.write(aLine);
                        else {

                            final String[] tokens = Basic.split(aLine, '\t');

                            if (tokens.length < 2 || tokens[2].equals("*")) {
                                w.write(aLine);
                            } else {
                                final IntervalTree<GeneItem> tree;
                                {
                                    final int pos = tokens[2].indexOf(".");
                                    final String ref = (pos > 0 ? tokens[2].substring(0, pos) : tokens[2]);

                                    final Pair<Long, IntervalTree<GeneItem>> pair = ref2PosAndTree.get(ref);

                                    if (pair != null) {
                                        if (pair.getSecond() == null && pair.getFirst() != 0) {
                                            dbxIns.seek(pair.getFirst());

                                            int intervalsLength = dbxIns.readInt();
                                            if (intervalsLength > 0) {
                                                final IntervalTree<GeneItem> intervals = new IntervalTree<>();
                                                for (int t = 0; t < intervalsLength; t++) {
                                                    int start = dbxIns.readInt();
                                                    int end = dbxIns.readInt();
                                                    GeneItem geneItem = new GeneItem();
                                                    geneItem.read(dbxIns);
                                                    intervals.add(start, end, geneItem);
                                                    //System.err.println(refIndex+"("+start+"-"+end+") -> "+geneItem);
                                                }
                                                pair.setSecond(intervals);
                                                countReferencesLoaded++;
                                            } else
                                                pair.setSecond(emptyTree);
                                        }
                                    }
                                    tree = pair.getSecond();
                                    if (tree == null) {
                                        System.err.println("Ref not found: " + ref);
                                        continue;
                                    }
                                }

                                final int startSubject = Basic.parseInt(tokens[3]);
                                final int endSubject = startSubject + getRefLength(tokens[5]) - 1;

                                final Interval<GeneItem> refInterval = tree.getBestInterval(new Interval<GeneItem>(startSubject, endSubject, null), 0.9);

                                String annotatedRef = tokens[2];
                                if (refInterval != null) {
                                    final GeneItem geneItem = refInterval.getData();
                                    final String remainder;
                                    final int len = annotatedRef.indexOf(' ');
                                    if (len >= 0 && len < annotatedRef.length()) {
                                        remainder = annotatedRef.substring(len); // keep space...
                                        annotatedRef = annotatedRef.substring(0, len);
                                    } else
                                        remainder = "";
                                    annotatedRef += (annotatedRef.endsWith("|") ? "pos|" : "|pos|") + (geneItem.isReverse() ? refInterval.getEnd() + ".." + refInterval.getStart()
                                            : refInterval.getStart() + ".." + refInterval.getEnd()) + "|ref|" + Basic.toString(geneItem.getProteinId()) + remainder;
                                }
                                for (int t = 0; t < tokens.length; t++) {
                                    if (t > 0)
                                        w.write('\t');
                                    if (t == 2 && !annotatedRef.equals(tokens[2])) {
                                        w.write(annotatedRef);
                                        countAnnotated++;
                                    } else
                                        w.write(tokens[t]);
                                }
                            }
                            countAlignments++;
                        }
                        w.write("\n");
                        countLines++;
                    }
                }

                System.err.println(String.format("Lines:     %,11d", countLines));
                System.err.println(String.format("Alignments:%,11d", countAlignments));
                System.err.println(String.format("Annotated: %,11d", countAnnotated));
                System.err.println(String.format("(Loaded refs:%,10d)", countReferencesLoaded));

            }
        }
    }

    private static Pattern pattern = Pattern.compile("[0-9]+[MDN]+");

    public static int getRefLength(String cigar) {
        final Matcher matcher = pattern.matcher(cigar);
        final ArrayList<String> pairs = new ArrayList<>();
        while (matcher.find())
            pairs.add(matcher.group());

        int length = 0;
        for (String p : pairs) {
            int num = Integer.parseInt(p.substring(0, p.length() - 1));
            length += num;
        }
        return length;

    }

    /**
     * read and verify a magic number from a stream
     *
     * @param ins
     * @param expectedMagicNumber
     * @throws java.io.IOException
     */
    public static void readAndVerifyMagicNumber(InputReader ins, byte[] expectedMagicNumber) throws IOException {
        byte[] magicNumber = new byte[expectedMagicNumber.length];
        if (ins.read(magicNumber, 0, magicNumber.length) != expectedMagicNumber.length || !Basic.equal(magicNumber, expectedMagicNumber)) {
            System.err.println("Expected: " + Basic.toString(expectedMagicNumber));
            System.err.println("Got:      " + Basic.toString(magicNumber));
            throw new IOException("Index is too old or incorrect file (wrong magic number). Please recompute index.");
        }
    }

}