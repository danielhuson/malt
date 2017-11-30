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

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class RandomReadExtractor {
    /**
     * convert feature tables to gene table
     *
     * @param args
     * @throws UsageException
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws Exception {
        try {
            ProgramProperties.setProgramName("RandomReadExtractor");
            ProgramProperties.setProgramVersion(megan.main.Version.SHORT_DESCRIPTION);

            PeakMemoryUsageMonitor.start();
            (new RandomReadExtractor()).run(args);
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
     *
     * @param args
     */
    public void run(String[] args) throws CanceledException, IOException, UsageException {
        final ArgsOptions options = new ArgsOptions(args, this, "Randomly cuts out reads from a single DNA sequence");
        options.setVersion(ProgramProperties.getProgramVersion());
        options.setLicense("Copyright (C) 2017 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");
        options.setAuthors("Daniel H. Huson");

        options.comment("Input Output");
        final String inputFile = options.getOptionMandatory("-i", "input", "FastA file containing a single sequence", "");
        final String outputFile = options.getOptionMandatory("-o", "output", "Output file (.gz ok)", "");
        options.comment("Options");
        final int numberOfReads = options.getOption("-n", "num", "Number of reads to extract", 10000);
        final int readLength = options.getOption("-l", "length", "Length of reads to extract", 100);
        final boolean forwardStrand = options.getOption("-fs", "forwardStrand", "From forward strand", true);
        final boolean backwardStrand = options.getOption("-bs", "backwardtrand", "From backward strand", true);
        final int randomSeed = options.getOption("-rs", "randomSeed", "Random number seed", 666);

        options.done();

        final Random random = new Random(randomSeed);

        int count = 0;

        final FastA fastA = new FastA();
        fastA.read(new FileReader(inputFile));
        final String genome = fastA.getSequence(0);

        System.err.println("Writing to file: " + outputFile);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputFile)); ProgressPercentage progress = new ProgressPercentage(numberOfReads)) {
            final boolean forward = forwardStrand && !backwardStrand || (!backwardStrand || forwardStrand) && random.nextBoolean();

            int start = random.nextInt(genome.length() - readLength);
            final int end;
            if (forward) {
                end = start + readLength;
            } else {
                end = start;
                start = end + readLength;
            }

            for (int r = 0; r < numberOfReads; r++) {
                final String header = String.format(">r%06d %d-%d from %s", (r + 1), (start + 1), (end + 1), fastA.getHeader(0));
                final String sequence;
                if (forward) {
                    sequence = genome.substring(start, start + readLength);
                } else {
                    sequence = SequenceUtils.getReverseComplement(genome.substring(end, start + readLength));
                }
                w.write(header);
                w.write("\n");
                w.write(sequence);
                w.write("\n");
                progress.incrementProgress();
                count++;
            }
        }
        System.err.println(String.format("Lines: %,d", count));
    }
}
