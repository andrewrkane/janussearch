/*
 * (C) Copyright 2008 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

package janusengine;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.CharArrayWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Vector;

import org.apache.lucene.search.BooleanQuery;

/**
 * NGram signature for query and documents to allow for overlap matching.
 */
public class NGram {

  static final boolean sDebug = false;

  static public int sWindowSize = 10;
  static public int sNGramSize = 20;

  static public String sBaseDir = "./";
  static public String sSubwordMappingsFile = "mappings.cfg";

  static private Object sLock = new Object();

  // -----------------------
  // static public methods
  // -----------------------

  static boolean startsWith(char[] c, int offset, char[] start) {
    if (offset + start.length >= c.length)
      return false;
    for (int i = 0; i < start.length; i++) {
      if (c[offset + i] != start[i])
        return false;
    }
    return true;
  }

  static Vector<String> splitBoolean(String query) {
    char[] q = query.toCharArray();
    for (int i = 0; i < q.length; i++) {
      char c = q[i];
      if (c >= 'A' && c <= 'Z')
        continue;
      if (c >= 'a' && c <= 'z')
        continue;
      q[i] = ' ';
    }
    Vector<String> result = new Vector<String>();
    int lastOutput = 0;
    char[][] booleans = new char[][] { "AND".toCharArray(), "OR".toCharArray(), "NOT".toCharArray() };
    for (int i = 0; i < q.length; i++) {
      if (q[i] == ' ') {
        for (int b = 0; b < booleans.length; b++) {
          char[] bool = booleans[b];
          int boolLength = bool.length;
          if (i + boolLength + 1 < q.length && q[i + boolLength + 1] == ' ' && startsWith(q, i + 1, bool)) {
            result.add(query.substring(lastOutput, i + 1));
            result.add(query.substring(i + 1, i + boolLength + 1));
            lastOutput = i + boolLength + 1;
            i += boolLength;
            break;
          }
        }
      }
    }
    result.add(query.substring(lastOutput, query.length()));
    return result;
  }

  /**
   * Handles case folding and mappings.
   */
  static String convertToNormalized(String data) {
    StringBuffer sb = new StringBuffer();
    char[] buffer = new char[1024];
    String q = data.toLowerCase();
    NGramUtils.BaseReader r = new NGramUtils.MFReader(new NGramUtils.ReaderToLocationReader(new StringReader(q)), sBaseDir + sSubwordMappingsFile);
    try {
      for (;;) {
        int i = r.read(buffer);
        if (i <= 0) break;
        sb.append(buffer, 0, i);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return sb.toString();
  }

  /**
   * Handles case folding and mappings, preserves AND, OR, NOT if in capitals.
   */
  static String convertToNormalizedKeywordQuery(String keywordQuery) {
    StringBuffer sb = new StringBuffer();
    char[] buffer = new char[1024];
    Vector<String> splitQuery = splitBoolean(keywordQuery);
    for (Iterator<String> iter = splitQuery.iterator(); iter.hasNext();) {
      // non boolean value
      String q = iter.next().toLowerCase();
      NGramUtils.BaseReader r = new NGramUtils.MFReader(new NGramUtils.ReaderToLocationReader(new StringReader(q)), sBaseDir + sSubwordMappingsFile);
      try {
        for (;;) {
          int i = r.read(buffer);
          if (i <= 0) break;
          sb.append(buffer, 0, i);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      // boolean value
      if (iter.hasNext())
        sb.append(iter.next());
    }
    return sb.toString();
  }

  static public class CombinedDocument {
    public Reader r;
    public Winnowing w;
  }

  /**
   * Return a reader which combines the data in r followed by extra data and all the ngram
   * values separated by spaces.
   */
  static public CombinedDocument createDocumentReader(Reader r, String extraData) {
    try {
      if (!(r instanceof BufferedReader))
        r = new BufferedReader(r);

      CombinedDocument result = new CombinedDocument();

      // get ngrams and normalized form
      result.w = new NGram.Winnowing(true);
      result.w.winnow(r);

      // combine normalized form, extra data, and ngrams
      CharArrayWriter caw = new CharArrayWriter();
      caw.write(result.w.fNormalizedOutput);
      caw.write("\n");
      if (extraData != null) {
        caw.write(NGram.convertToNormalized(extraData)); // convert extra data for consistent search
        caw.write("\n");
      }
      for (Iterator<NGram.Entry> iter = result.w.fOutputEntries.iterator(); iter.hasNext();) {
        caw.write(" ");
        caw.write(iter.next().fToken);
      }

      result.r = new CharArrayReader(caw.toCharArray());
      return result;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Return a query for all the ngram hash values of the originalQuery.
   */
  static public String convertQuery(String originalQuery) {
    // get ngrams
    Winnowing w = new NGram.Winnowing();
    w.winnow(originalQuery);
    return convertToQuery(w);
  }

  /**
   * Return a query for all the ngram hash values from file.
   */
  static public String convertFileToQuery(String filename) throws IOException {
    // get ngrams
    Winnowing w = new NGram.Winnowing();
    FileReader fr = new FileReader(filename);
    w.winnow(fr);
    fr.close();
    return convertToQuery(w);
  }

  static public String convertToQuery(NGram.Winnowing w) {
    // TODO: add ngram counts to prune results?
    // convert to query for ngrams
    StringBuffer sb = new StringBuffer();
    int count = 0;
    for (Iterator<NGram.Entry> iter = w.fOutputEntries.iterator(); iter.hasNext(); count++) {
      NGram.Entry e = iter.next();
      sb.append(" ");
      sb.append(e.fToken);
      // debugging
      //System.out.println(e.fHash + " = \"" + e.fChars + "\"");
    }
    // make sure engine can handle this query
    count += 1000;
    synchronized (sLock) {
      if (BooleanQuery.getMaxClauseCount() < count)
        BooleanQuery.setMaxClauseCount(count);
    }
    return sb.toString();
  }

  // ---------
  //  classes
  // ---------

  static public class Entry {
    static public char sWhitespaceConversionChar = '0';

    public String fChars;
    public String fToken;
    protected int fHash;
    public int fLocationStartInOriginal;
    public int fLocationEndInOriginal;
    int fLocationInStream;

    public boolean equals(Object o) {
      if (!(o instanceof NGram.Entry))
        return false;
      NGram.Entry e = (NGram.Entry) o;
      return (fChars == null ? fChars == e.fChars : fChars.equals(e.fChars)) && fHash == e.fHash;
    }

    public void setChars(String chars) {
      fChars = chars;
      fToken = chars.replace(' ', sWhitespaceConversionChar);
    }
  }

  /**
   * Winnowing class creates NGrams from input text
   */
  static public class Winnowing {
    int fWindowSize; // window size
    int fNGramSize; // the ngram size
    int[] fWindowOfHashes; // circular buffer implementing window of size w
    int fWindowRightPoint; // points into fWindowOfHashes
    int fWindowLocationInStream; // location of the end of the window in the input stream
    char[] fWindowOfChars; // rolling chars in the window
    int[] fWindowOfLocations; // aligned with fWindowOfChars, stores locations in original text
    int fNGramEndPoint; // start of rolling chars in ngram, points into

    NGramUtils.BaseReader fInput;

    public Vector<NGram.Entry> fOutputEntries;
    StringBuffer fNormalizedOutputBuffer = null;
    public String fNormalizedOutput;

    public Winnowing() {
      this(sWindowSize, sNGramSize);
    }

    public Winnowing(boolean getNormalizedOutput) {
      this(sWindowSize, sNGramSize);
      if (getNormalizedOutput)
        fNormalizedOutputBuffer = new StringBuffer();
    }

    protected Winnowing(int windowSize, int ngramSize) {
      fWindowSize = windowSize;
      fNGramSize = ngramSize;
      fWindowOfHashes = new int[fWindowSize];
      fWindowRightPoint = -1;
      // keep window size of ngramsize strings all overlapping...
      fWindowOfChars = new char[fWindowSize + fNGramSize];
      fWindowOfLocations = new int[fWindowOfChars.length];
      fNGramEndPoint = 0;
    }

    public void winnow(String data) {
      winnow(new StringReader(data));
    }

    public void winnow(Reader data) {
      try {
        fWindowLocationInStream = -1;
        fWindowRightPoint = -1;
        fNGramEndPoint = 0;
        // debugging
        //fInput = new NGram.NGramReader(data);
        fInput = new NGramUtils.MFReader(new NGramUtils.NGramReader(new NGramUtils.ReaderToLocationReader(data)), sBaseDir + sSubwordMappingsFile);
        fOutputEntries = new Vector<NGram.Entry>();
        // read in first set of chars for ngram
        for (int i = 0; i < fNGramSize - 1; i++) {
          if (!getChar())
            return;
        }
        // first hash
        if (!getCharAndHash())
          return;
        // process up to first window size
        int min = 0; // index of minimum hash
        for (int i = 0; i < fWindowSize - 1; i++) {
          if (!getCharAndHash()) {
            record(min);
            return;
          }
          if (fWindowOfHashes[fWindowRightPoint] < fWindowOfHashes[min])
            min = fWindowRightPoint;
        }
        // record min in first window
        record(min);
        // At the end of each iteration, min holds the
        // position of the rightmost minimal hash in the
        // current window. record(x) is called only the
        // first time an instance of x is selected as the
        // rightmost minimal hash of a window.
        while (true) {
          if (!getCharAndHash())
            return;
          if (min == fWindowRightPoint) {
            // The previous minimum is no longer in this
            // window. Scan h leftward starting from r
            // for the rightmost minimal hash. Note min
            // starts with the index of the rightmost
            // hash.
            for (int i = (fWindowRightPoint - 1 + fWindowSize) % fWindowSize; i != fWindowRightPoint; i = (i - 1 + fWindowSize) % fWindowSize)
              if (fWindowOfHashes[i] < fWindowOfHashes[min])
                min = i;
            record(min);
          } else {
            // Otherwise, the previous minimum is still in
            // this window. Compare against the new value
            // and update min if necessary.
            if (fWindowOfHashes[fWindowRightPoint] <= fWindowOfHashes[min]) { // (*)
              min = fWindowRightPoint;
              record(min);
            }
          }
        }
      } finally {
        if (fNormalizedOutputBuffer != null) {
          fNormalizedOutput = fNormalizedOutputBuffer.toString();
        }
      }
    }

    // -----------------
    // private methods
    // -----------------

    boolean getCharAndHash() {
      if (!getChar())
        return false;
      fWindowRightPoint = (fWindowRightPoint + 1) % fWindowSize;
      fWindowOfHashes[fWindowRightPoint] = getHash((fNGramEndPoint - fNGramSize + fWindowOfChars.length) % fWindowOfChars.length);
      // TODO: fix assertions
      //assert ((fWindowLocationInStream - fWindowSize) % fWindowOfHashes.length == fWindowRightPoint);
      return true;
    }

    boolean getChar() {
      try {
        NGramUtils.LocatedChar lc = fInput.readLocated();
        if (lc.c == -1)
          return false;
        fWindowOfChars[fNGramEndPoint] = (char) lc.c;
        fWindowOfLocations[fNGramEndPoint] = lc.originalLocation;
        fNGramEndPoint = (fNGramEndPoint + 1) % fWindowOfChars.length;
        fWindowLocationInStream++;
        // TODO: fix assertions
        //assert (fWindowLocationInStream % fWindowOfChars.length == fNGramEndPoint);
        if (fNormalizedOutputBuffer != null) {
          fNormalizedOutputBuffer.append((char) lc.c);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return true;
    }

    int getHash(int positionOfNGram) {
      // debugging
      // System.out.println("getHash " + NGram.getHash(fWindowOfChars,
      // positionOfNGram, fNGramSize) + " = \"" +
      // NGram.getNGramString(fWindowOfChars, positionOfNGram, fNGramSize) +
      // "\"");
      return NGramUtils.getHash(fWindowOfChars, positionOfNGram, fNGramSize);
    }

    void record(int positionOfMin) {
      NGram.Entry e = new NGram.Entry();
      e.fHash = fWindowOfHashes[positionOfMin];
      int deltaLocation = (fWindowRightPoint - positionOfMin + fWindowSize) % fWindowSize;
      int ngramStart = (fNGramEndPoint - fNGramSize - deltaLocation + 2 * fWindowOfChars.length) % fWindowOfChars.length;
      e.setChars(NGram.getNGramString(fWindowOfChars, ngramStart, fNGramSize));
      e.fLocationInStream = fWindowLocationInStream - deltaLocation;
      e.fLocationStartInOriginal = fWindowOfLocations[ngramStart];
      e.fLocationEndInOriginal = fWindowOfLocations[(ngramStart + fNGramSize - 1) % fWindowOfChars.length];
      assert (e.fLocationStartInOriginal < e.fLocationEndInOriginal);
      fOutputEntries.add(e);
      assert (e.fChars.length() == fNGramSize);
      /*
       * / * debugging if (e.fHash != NGram.getHash(e.fChars.toCharArray(), 0,
       * sNGramSize)) System.out.println("error ngram=\"" + e.fChars + "\""); //
       */
      assert (e.fHash == NGramUtils.getHash(e.fChars.toCharArray(), 0, fNGramSize));
    }

  } // Winnowing

  // --------------------------
  // static utility functions
  // --------------------------

  static final String getNGramString(char[] chars, int offset, int length) {
    StringBuffer sb = new StringBuffer(length);
    sb.append(chars, offset, Math.min(length, chars.length - offset));
    if (chars.length - offset < length)
      sb.append(chars, 0, length - chars.length + offset);
    return sb.toString();
  }

}
