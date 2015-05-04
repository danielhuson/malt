package malt.io;


import megan.parsers.blast.BlastMode;

/**
 * some methods to help generate BLAST text output
 * Daniel Huson, 8.2014
 */
public class BlastTextHelper {
    public static final String FILE_HEADER_BLASTN = "BLASTN output produced by MALT\n\n";
    public static final String FILE_HEADER_BLASTX = "BLASTX output produced by MALT\n\n";
    public static final String FILE_HEADER_BLASTP = "BLASTP output produced by MALT\n\n";
    public static final String FILE_FOOTER_BLAST = "\nEOF\n";
    public static final String NO_HITS_STRING = "***** No hits found ******";
    public static final byte[] NO_HITS = NO_HITS_STRING.getBytes();
    public static final String QUERY_EQUALS_STRING = "\nQuery= ";
    private static final byte[] QUERY_EQUALS = QUERY_EQUALS_STRING.getBytes();
    public static final String QUERY_LETTERS_FORMAT_STRING = "\n         (%d letters)\n\n";
    public static final String REFERENCE_LENGTH_FORMAT_STRING = "\n          Length = %d\n\n";

    /**
     * make the query line
     *
     * @return query line
     */
    public static byte[] makeQueryLine(final FastARecord query) {
        final byte[] header = query.getHeader();
        int startOfFirstWord = (header.length > 0 && header[0] == '>' ? 1 : 0);
        while (startOfFirstWord < header.length && Character.isWhitespace(header[startOfFirstWord])) {
            startOfFirstWord++;
        }
        int endOfFirstWord = startOfFirstWord;
        while (endOfFirstWord < header.length) {
            if (Character.isWhitespace(header[endOfFirstWord]) || header[endOfFirstWord] == 0)
                break;
            else
                endOfFirstWord++;
        }
        int lengthOfFirstWord = endOfFirstWord - startOfFirstWord;
        final byte[] result = new byte[QUERY_EQUALS.length + lengthOfFirstWord + 1]; // add one for new-line
        System.arraycopy(QUERY_EQUALS, 0, result, 0, QUERY_EQUALS.length);
        System.arraycopy(query.getHeader(), startOfFirstWord, result, QUERY_EQUALS.length, lengthOfFirstWord);
        result[result.length - 1] = '\n';
        return result;
    }

    /**
     * gets the appropriate header line
     *
     * @param mode
     * @return header line
     */
    public static String getBlastTextHeader(BlastMode mode) {
        switch (mode) {
            case BlastN:
                return BlastTextHelper.FILE_HEADER_BLASTN;
            case BlastX:
                return BlastTextHelper.FILE_HEADER_BLASTX;
            case BlastP:
                return BlastTextHelper.FILE_HEADER_BLASTP;
            default:
                return "unknown";
        }
    }

    /**
     * get query name followed by tab
     *
     * @param query
     * @return query name plus tab
     */
    public static byte[] getQueryNamePlusTab(final FastARecord query) {
        final byte[] header = query.getHeader();
        int startOfFirstWord = (header.length > 0 && header[0] == '>' ? 1 : 0);
        while (startOfFirstWord < header.length && Character.isWhitespace(header[startOfFirstWord])) {
            startOfFirstWord++;
        }
        int endOfFirstWord = startOfFirstWord;
        while (endOfFirstWord < header.length) {
            if (Character.isWhitespace(header[endOfFirstWord]) || header[endOfFirstWord] == 0)
                break;
            else
                endOfFirstWord++;
        }
        int lengthOfFirstWord = endOfFirstWord - startOfFirstWord;
        byte[] result = new byte[lengthOfFirstWord + 1]; // plus one for tab
        System.arraycopy(header, startOfFirstWord, result, 0, lengthOfFirstWord);
        result[lengthOfFirstWord] = '\t';
        return result;
    }
}
