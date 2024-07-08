/*
 * (C) Copyright 2008 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

package janusengine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

public class NGramUtils {

  static final boolean sDebug = false;

  static public final int sMac = 1;
  static public final int sWindows = 2;
  static public final int sOther = 3;

  static public int sMachineType;
  static {
    Properties p = System.getProperties();
    String os = p.getProperty("os.name");
    os = (os == null ? "" : os.toLowerCase());
    if (os.contains("windows"))
      sMachineType = sWindows;
    else if (os.contains("mac"))
      sMachineType = sMac;
    else
      sMachineType = sOther;
  }

  //----------------
  // static methods
  //----------------

  static public final String extractTag(String data, String start, String end) {
    int s = data.indexOf(start);
    int e = data.indexOf(end, s);
    if (s>=0 && e>=0) return data.substring(s+start.length(),e);
    return null;
  }

  static public final String truncateAt(String data, String end) {
    int c = data.indexOf(end);
    return (c>=0 ? data.substring(0,c) : data);
  }

  static public final String trimFront(String data, String end) {
    int c = data.lastIndexOf(end);
    return (c>=0 ? data.substring(c+end.length(),data.length()) : data);
  }

  static public final boolean deleteRecursive(String f) {
    return deleteRecursive(new File(f));
  }

  static public final boolean deleteRecursive(File f) {
    if (!f.exists())
      return true;
    if (!deleteRecursiveSubFiles(f))
      return false;
    if (!f.delete())
      return false;
    return true;
  }

  static public final boolean deleteRecursiveSubFiles(File f) {
    if (f.isDirectory()) {
      File[] list = f.listFiles();
      for (int i = 0; i < list.length; i++) {
        if (!deleteRecursive(list[i]))
          return false;
      }
    }
    return true;
  }

  static public final int getHash(String s) {
    return getHash(s.toCharArray());
  }

  static public final int getHash(char[] chars) {
    return getHash(chars, 0, chars.length);
  }

  static public final int getHash(char[] chars, int offset, int length) {
    long lhash = 0;
    if (sDebug)
      System.out.print("hash \"");
    for (; length > 0; length--) {
      if (sDebug)
        System.out.print(chars[offset]);
      lhash = 31 * lhash + chars[offset];
      lhash = lhash & 0xFFFFFFFFL ^ lhash >> 32;
      offset = (offset + 1) % chars.length;
    }
    // return positive hash
    int hash = (int) lhash;
    if (hash < 0)
      hash = -hash;
    if (sDebug)
      System.out.println("\"=" + hash);
    return hash;
  }

  static public String cleanAndTrimString(String s) {
    return cleanString(s.trim());
  }

  static public String cleanString(String s) {
    char[] chars = s.toCharArray();
    int k = 0;
    for (int i = 0; i < chars.length; i++) {
      char c = chars[i];
      if (((int) c) == 0)
        continue;
      if (k != i)
        chars[k] = c;
      k++;
    }
    return new String(chars, 0, k);
  }

  static public String readInFile(String filename) throws IOException {
    FileReader fr = new FileReader(filename);
    BufferedReader in = new BufferedReader(fr);
    StringBuffer sb = new StringBuffer();
    for (;;) {
      String line = in.readLine();
      if (line == null)
        break;
      if (sb.length() <= 0)
        sb.append(" ");
      sb.append(line);
      sb.append(" ");
    }
    fr.close();
    return sb.toString();
  }

  static public String readInFileUpto(String filename, String end) throws IOException {
    String s = readInFile(filename);
    int c = s.indexOf(end);
    return (c>=0 ? s.substring(0,c-1) : s);
  }

  static public final String readInFileAndClean(String filename) throws IOException {
    return readInFileAndClean(filename, "\n");
  }

  static public final String readInFileAndClean(String filename, String newline) throws IOException {
    FileReader fr = new FileReader(filename);
    String res = readInFileAndClean(fr, newline);
    fr.close();
    return res;
  }

  static public final String readInFileAndClean(Reader reader, String newline) throws IOException {
    // read in file and clean up data (not sure why we need this, but data has
    // strange chars in it...)
    StringBuffer sb = new StringBuffer(1024);
    BufferedReader in = (reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader));
    for (;;) {
      String line = in.readLine();
      if (line == null)
        break;
      sb.append(cleanAndTrimString(line));
      sb.append(newline);
    }
    return sb.toString();
  }

  //------------------------
  // static utility classes
  //------------------------

  static public class NullStream extends OutputStream {
    public void write(int x) {
    }
  }

  /**
   * A char and the location from the original text.
   */
  static public class LocatedChar {
    public LocatedChar(int c, int originalLocation) {
      this.c = c;
      this.originalLocation = originalLocation;
    }

    int c;
    int originalLocation;
  }

  /**
   * LocationReader interface.
   */
  static public interface LocationReader {
    public LocatedChar readLocated() throws IOException;

    public void close() throws IOException;

    public void reset() throws IOException;
  }

  /**
   * Reads from an original reader and keeps track of location within original.
   */
  static public class ReaderToLocationReader implements LocationReader {
    private Reader fBase;
    private int fLocationInOriginal;

    ReaderToLocationReader(Reader base) {
      fBase = base;
    }

    public LocatedChar readLocated() throws IOException {
      return new LocatedChar(fBase.read(), fLocationInOriginal++);
    }

    public void close() throws IOException {
      fBase.close();
      fBase = null;
    }

    public void reset() throws IOException {
      fBase.reset();
      fLocationInOriginal = 0;
    }
  }

  /**
   * Reads from base LocationReader.
   */
  static public class BaseReader extends Reader implements LocationReader {
    private LocationReader fBase;

    BaseReader(LocationReader base) {
      fBase = base;
    }

    public LocatedChar readLocated() throws IOException {
      return fBase.readLocated();
    }

    public int read() throws IOException {
      return readLocated().c;
    }

    public int read(char[] chars, int offset, int length) throws IOException {
      int i = length;
      for (; i > 0; i--) {
        int c = read();
        if (c == -1) {
          if (length == i)
            return -1;
          return length - i;
        }
        chars[offset++] = (char) c;
      }
      return length;
    }

    public void close() throws IOException {
      fBase.close();
      fBase = null;
    }

    public void reset() throws IOException {
      fBase.reset();
    }
  } // BaseReader

  static public boolean sKeepWhitespace = true;
  
  /**
   * Reads in from a supplied Reader object, then folds cases, prunes out
   * special characters, collapses whitespace, etc.
   */
  static public class NGramReader extends BaseReader {
    boolean fLastWasWhitespace = false;
    LinkedList<LocatedChar> fWaitingLC = new LinkedList<LocatedChar>();

    NGramReader(LocationReader base) {
      super(base);
    }

    public int read() throws IOException {
      return readLocated().c;
    }

    private void readBlock() throws IOException {
      for (int i = 0; i < 30; i++) {
        LocatedChar lc = super.readLocated();
        fWaitingLC.add(lc);
        if (lc.c == -1)
          break;
      }
    }

    public LocatedChar readLocated() throws IOException {
      // TODO: return non a-z characters?
      for (;;) {
        if (fWaitingLC.size() < 30)
          readBlock();
        LocatedChar lc = fWaitingLC.poll();
        if (lc.c == -1)
          return lc;
        // keep this aligned with isWhitespace(char) method below.
        if (lc.c >= 'A' && lc.c <= 'Z')
          lc.c = lc.c - 'A' + 'a';
        if (lc.c >= 'a' && lc.c <= 'z') {
          if (sDebug)
            System.out.println("read " + (char) lc.c);
          fLastWasWhitespace = false;
          return lc;
        }
        if (lc.c == '<') {
          if (fWaitingLC.size() >= 3 && fWaitingLC.get(0).c == 'b' && fWaitingLC.get(1).c == 'r' && fWaitingLC.get(2).c == '>') {
            fWaitingLC.poll();
            fWaitingLC.poll();
            fWaitingLC.poll();
          }
        }
        if (!fLastWasWhitespace && sKeepWhitespace) {
          fLastWasWhitespace = true;
          lc.c = ' ';
          return lc;
        }
      }
    }

    static public boolean isWhitespace(char c) {
      if (c >= 'A' && c <= 'Z')
        return false;
      if (c >= 'a' && c <= 'z')
        return false;
      return true;
    }

    public void reset() throws IOException {
      super.reset();
      fLastWasWhitespace = false;
      fWaitingLC.clear();
    }

  } // NGramReader

  /**
   * Does the word/subword mapping used in the Manipulus Florum.
   */
  // TODO: can we make this mapping faster
  // TODO: move this somewhere else?
  static public class MFReader extends BaseReader {
    String fCurrent = "";
    LinkedList<LocatedChar> fCurrentLC = new LinkedList<LocatedChar>();
    boolean fMoreInput = true;
    int fMaxMapping = 1;
    HashMap<String, String> fMap = new HashMap<String, String>();

    public MFReader(LocationReader base, String subwordMappingFile) {
      super(base);
      if (!new File(subwordMappingFile).exists()) {
        System.err.println("Warning: " + subwordMappingFile + " does not exist.");
        loadMappings(null);
      } else {
        try {
          // TODO: Don't load every time... maybe memoize?
          FileReader fr = new FileReader(subwordMappingFile);
          loadMappings(fr);
          fr.close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    public MFReader(LocationReader base, Reader mappings) {
      super(base);
      loadMappings(mappings);
    }

    private void loadMappings(Reader mappings) {
      if (mappings != null) {
        // read in mappings
        try {
          BufferedReader in = new BufferedReader(mappings);
          for (;;) {
            String line = in.readLine();
            if (line == null)
              break;
            int eq = line.indexOf('=');
            if (line.startsWith("#") || eq < 0)
              continue;
            addMapping(line.substring(0, eq), line.substring(eq + 1));
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      // no default mappings right now...
    }

    public void reset() throws IOException {
      super.reset();
      fMoreInput = true;
      fCurrent = "";
    }

    public void addMapping(String from, String to) {
      fMaxMapping = Math.max(fMaxMapping, from.length());
      fMap.put(from, to);
    }

    public int read() throws IOException {
      return readLocated().c;
    }

    public LocatedChar readLocated() throws IOException {
      // map multiple times as needed
      for (;;) {
        // input
        for (;;) {
          int length = fCurrent.length();
          if (!fMoreInput && length <= 0)
            return new LocatedChar(-1, -1);
          if (!fMoreInput || length >= fMaxMapping)
            break;
          // add one character
          if (fMoreInput && length < fMaxMapping) {
            LocatedChar lc = super.readLocated();
            if (lc.c < 0)
              fMoreInput = false;
            else {
              fCurrent += (char) lc.c;
              fCurrentLC.add(lc);
            }
          }
        }
        // map
        String currentOriginal = fCurrent;
        int length = Math.min(fCurrent.length(), fMaxMapping);
        for (int i = 0; i < length; i++) {
          String x = fMap.get(fCurrent.subSequence(0, i + 1));
          if (x == null)
            continue;
          // modify values
          fCurrent = x + fCurrent.substring(i + 1);
          LocatedChar lc = fCurrentLC.peek();
          for (int k = 0; k <= i; k++) {
            fCurrentLC.poll();
          }
          for (int k = x.length() - 1; k >= 0; k--) {
            fCurrentLC.addFirst(new LocatedChar(x.charAt(k), lc.originalLocation));
          }
          break;
        }
        if (fCurrent == currentOriginal)
          break;
      }
      // return first character
      fCurrent = fCurrent.substring(1);
      LocatedChar lc = fCurrentLC.poll();
      assert (fCurrent.length() == fCurrentLC.size());
      return lc;
    }
  } // MFReader

}
