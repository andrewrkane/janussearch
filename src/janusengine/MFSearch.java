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
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;

/**
 * Check results for different values of NGram size and Window size.
 */
public class MFSearch {

  static public int sMaxSeparation = 100;

  String fBaseDir;
  int fNGramSize;
  int fWindowSize;

  public MFSearch() throws IOException {
    this(new File(".").getCanonicalPath() + "/");
  }

  public MFSearch(String baseDir) throws IOException {
    fBaseDir = baseDir + "/";
    fNGramSize = 18;
    fWindowSize = 18;
  }

  public void reset() {
  }

  // ----------------------
  // static query methods
  // ----------------------

  static class HitRangeComparator implements java.util.Comparator<HitRange> {
    public int compare(HitRange o1, HitRange o2) {
      return o1.start - o2.start;
    }
  }

  static class HitRange {
    /* inclusive */
    int start;
    /* inclusive */
    int end;

    public HitRange(int start, int end) {
      this.start = start;
      this.end = end;
    }

    static HitRangeComparator sComparator = new HitRangeComparator();
  }

  static class NGramHitMatch {
    /* inclusive */
    int startInQuery = -1;
    /* inclusive */
    int endInQuery = -1;
    /* inclusive */
    protected int startInHit = -1;
    /* inclusive */
    protected int endInHit = -1;

    public NGramHitMatch() {
    }

    public NGramHitMatch(int startInQuery, int endInQuery, int startInHit, int endInHit) {
      this.startInQuery = startInQuery;
      this.endInQuery = endInQuery;
      this.startInHit = startInHit;
      this.endInHit = endInHit;
    }

    public NGramHitMatch copy() {
      return new NGramHitMatch(startInQuery, endInQuery, startInHit, endInHit);
    }

    private void setStart(int inQuery, int inHit) {
      startInQuery = inQuery;
      startInHit = inHit;
    }

    private void setEnd(int inQuery, int inHit) {
      endInQuery = inQuery;
      endInHit = inHit;
    }

    private void moveStart(int amount) {
      startInQuery += amount;
      startInHit += amount;
    }

    private void moveEnd(int amount) {
      endInQuery += amount;
      endInHit += amount;
    }
  }

  static private void addWords(String queryData, String hitData, NGramHitMatch curr, NGramHitMatch next, Vector<NGramHitMatch> extraMatches) {
    // get words in query
    String[] wordsInQueryData = getWordsBetween(queryData, curr.endInQuery, next.startInQuery);
    if (wordsInQueryData.length <= 0)
      return;
    HashSet<String> wordsInQueryDataSet = new HashSet<String>();
    for (int i = 0; i < wordsInQueryData.length; i++)
      wordsInQueryDataSet.add(wordsInQueryData[i].trim().toLowerCase());
    wordsInQueryDataSet.remove("");
    if (wordsInQueryDataSet.size() <= 0)
      return;
    // find overlapping words in hit
    String[] wordsInHitData = getWordsBetween(hitData, curr.endInHit, next.startInHit);
    if (wordsInHitData.length > 0) {
      for (int i = 0; i < wordsInHitData.length; i++) {
        String word = wordsInHitData[i].trim().toLowerCase();
        if (word.length() <= 0)
          continue;
        if (wordsInQueryDataSet.contains(word)) {
          // add word to extra matches
          int wordLength = word.length();
          int wordStartInHit = indexOfWordIgnoreCase(hitData, word, curr.endInHit);
          assert (wordStartInHit >= 0);
          int wordStartInQuery = indexOfWordIgnoreCase(queryData, word, curr.endInQuery);
          assert (wordStartInQuery >= 0);
          extraMatches.add(new NGramHitMatch(wordStartInQuery, wordStartInQuery + wordLength - 1, wordStartInHit, wordStartInHit + wordLength - 1));
        }
      }
    }
  }

  /**
   * @return vector of match locations within original text for the specified
   *         hit data.
   */
  static public void getMatchLocations(String queryData, NGram.Winnowing queryWinnow, String hitData, Vector<HitRange> queryOverlaps, Vector<HitRange> hitOverlaps) {
    // make hashmap of hitNGrams
    HashMap<String, NGram.Entry> hitNGrams = new HashMap<String, NGram.Entry>();
    NGram.Winnowing hitWinnow = new NGram.Winnowing();
    hitWinnow.winnow(hitData);
    for (Iterator<NGram.Entry> iter = hitWinnow.fOutputEntries.iterator(); iter.hasNext();) {
      NGram.Entry e2 = iter.next();
      // TODO: How do we handle when already exists?
      if (hitNGrams.get(e2.fToken) != null)
        continue; // throw new
                  // RuntimeException("Internal Error: Token value already found in map '"
                  // + e2.fChars + "'");
      hitNGrams.put(e2.fToken, e2);
    }

    // find NGram match locations
    Vector<NGramHitMatch> overlappingHitMatches = new Vector<NGramHitMatch>();
    // loop over query NGrams
    for (Iterator<NGram.Entry> iter = queryWinnow.fOutputEntries.iterator(); iter.hasNext();) {
      NGram.Entry queryEntry = iter.next();
      NGram.Entry hitEntry;
      if ((hitEntry = hitNGrams.get(queryEntry.fToken)) != null) {
        if (!queryEntry.fChars.equals(hitEntry.fChars))
          throw new RuntimeException("Internal error: tokens do not overlap for '" + queryEntry.fChars + "':'" + hitEntry.fChars + "'");
        NGramHitMatch match = new NGramHitMatch();
        match.setStart(queryEntry.fLocationStartInOriginal, hitEntry.fLocationStartInOriginal);
        match.setEnd(queryEntry.fLocationEndInOriginal, hitEntry.fLocationEndInOriginal);
        // add to vector
        overlappingHitMatches.add(match);
      }
    }

    Iterator<NGramHitMatch> iter;
    NGramHitMatch curr;
    NGramHitMatch next;

    // find words around hits up to sMaxSeparation away
    Vector<NGramHitMatch> extraMatches = new Vector<NGramHitMatch>();
    iter = overlappingHitMatches.iterator();
    curr = null;
    next = null;
    for (boolean doneMatches = false; !doneMatches;) {
      curr = next;
      next = null;
      if (iter.hasNext()) {
        next = iter.next();
        // debugging
        // System.out.println("start=" + next.startInHit);
      }
      // two hits
      if (curr != null && next != null) {
        // not lined up
        if (curr.endInHit >= next.startInHit || curr.endInQuery >= next.startInQuery)
          continue;
        // close together = check between
        if (curr.endInHit + sMaxSeparation >= next.startInHit && curr.endInQuery + sMaxSeparation >= next.startInQuery) {
          // debugging
          // System.out.println("addWords " + curr.startInHit + " " +
          // curr.endInHit + " " + next.startInHit + " " + next.endInHit);
          addWords(queryData, hitData, curr, next, extraMatches);
        } else {
          // not close together = check close to each end
          int query, hit;
          NGramHitMatch middle;
          // close to beginning (curr)
          query = Math.min(curr.endInQuery + sMaxSeparation / 2, findEndOfSentence(queryData, curr.endInQuery, 1));
          hit = Math.min(curr.endInHit + sMaxSeparation / 2, findEndOfSentence(hitData, curr.endInHit, 1));
          middle = new NGramHitMatch(query, query, hit, hit);
          addWords(queryData, hitData, curr, middle, extraMatches);
          // close to end (next)
          query = Math.max(next.startInQuery - sMaxSeparation / 2, findEndOfSentence(queryData, next.startInQuery, -1));
          hit = Math.max(next.startInHit - sMaxSeparation / 2, findEndOfSentence(hitData, next.startInHit, -1));
          middle = new NGramHitMatch(query, query, hit, hit);
          addWords(queryData, hitData, middle, next, extraMatches);
        }
      } else {
        if (next == null) {
          if (curr == null)
            break;
          // find at end of hits
          int query = Math.min(curr.startInQuery + sMaxSeparation, findEndOfSentence(queryData, curr.endInQuery, 1));
          int hit = Math.min(curr.startInHit + sMaxSeparation, findEndOfSentence(hitData, curr.endInHit, 1));
          next = new NGramHitMatch(query, query, hit, hit);
          doneMatches = true;
        }
        if (curr == null) {
          // find at beginning of hits
          int query = Math.max(next.startInQuery - sMaxSeparation, findEndOfSentence(queryData, next.startInQuery, -1));
          int hit = Math.max(next.startInHit - sMaxSeparation, findEndOfSentence(hitData, next.startInHit, -1));
          curr = new NGramHitMatch(query, query, hit, hit);
        }
        addWords(queryData, hitData, curr, next, extraMatches);
      }
    }
    if (extraMatches.size() >= 0) {
      overlappingHitMatches.addAll(extraMatches);
      sortMatches(overlappingHitMatches);
    }
    growMatches(overlappingHitMatches, queryData, hitData);

    // add to output vectors
    for (iter = overlappingHitMatches.iterator(); iter.hasNext();) {
      NGramHitMatch match = iter.next();
      if (match.startInQuery <= match.endInQuery)
        queryOverlaps.add(new HitRange(match.startInQuery, match.endInQuery));
      // TODO: this was from a bug, the start was after then end, not sure why
      // it came up, probably because of match.move()
      if (match.startInHit <= match.endInHit)
        hitOverlaps.add(new HitRange(match.startInHit, match.endInHit));
    }
    Collections.sort(queryOverlaps, HitRange.sComparator);
    Collections.sort(hitOverlaps, HitRange.sComparator);

    // collapse overlapping ranges
    combineOverlapping(queryOverlaps);
    combineOverlapping(hitOverlaps);

    // TODO: grow match locations if they match original form to the left or
    // right
  }

  static void sortMatches(List<NGramHitMatch> list) {
    Collections.sort(list, new Comparator<NGramHitMatch>() {
      public int compare(NGramHitMatch a, NGramHitMatch b) {
        if (a.startInQuery < b.startInQuery)
          return -1;
        else if (a.startInQuery > b.startInQuery)
          return 1;
        else
          return 0;
      }
    });
  }

  static void combineOverlapping(Vector<HitRange> overlaps) {
    HitRange prev = null;
    for (Iterator<HitRange> iter = overlaps.iterator(); iter.hasNext();) {
      HitRange curr = iter.next();
      // debugging
      // System.out.println("overlap " + curr.start + " " + curr.end);
      if (prev != null) {
        if (prev.start > curr.start)
          throw new RuntimeException("Not sorted.");
        if (prev.end >= curr.start) {
          prev.end = Math.max(prev.end, curr.end);
          iter.remove();
          curr = prev;
        }
      }
      prev = curr;
    }
  }

  static int indexOfIgnoreCase(String data, String value, int offset) {
    char[] v = value.toLowerCase().toCharArray();
    int length = data.length();
    if (offset < 0)
      offset = 0;
    for (int match = 0; offset < length; offset++) {
      char c = data.charAt(offset);
      if (c >= 'A' && c <= 'Z')
        c = (char) (c - 'A' + 'a');
      if (c == v[match])
        match++;
      else if (match > 0) {
        offset -= match;
        match = 0;
      }
      if (match >= v.length)
        return offset - v.length + 1;
    }
    return -1;
  }

  static int indexOfWordIgnoreCase(String data, String word, int offset) {
    int wordLength = word.length();
    // make sure starts and ends with whitespace or end of data
    for (;; offset++) {
      offset = indexOfIgnoreCase(data, word, offset);
      if (offset < 0)
        return offset;
      if ((offset <= 0 || isWhitespace(data.charAt(offset - 1))) && (offset + wordLength >= data.length() || isWhitespace(data.charAt(offset + wordLength))))
        break;
    }
    return offset;
  }

  /**
   * Whitespace for highlighting does not include '<br>
   * '.
   */
  static boolean isWhitespace(char c) {
    if (c >= 'A' && c <= 'Z')
      return false;
    if (c >= 'a' && c <= 'z')
      return false;
    if (c == '<' || c == '>')
      return false;
    return true;
  }

  static int findEndOfSentence(String data, int offset, int step) {
    if (step > 0) {
      int length = data.length();
      for (; offset < length; offset += step)
        if ("<>.!?\n\r".indexOf(data.charAt(offset)) >= 0)
          return offset;
      return length;
    } else {
      for (; offset >= 0; offset += step)
        if ("<>.!?\n\r".indexOf(data.charAt(offset)) >= 0)
          return offset;
      return -1;
    }
  }

  static String[] getWordsBetween(String data, int start, int end) {
    // first word
    if (start < 0)
      start = 0;
    else
      for (; start < end; start++) {
        if (isWhitespace(data.charAt(start)))
          break;
      }
    // last word
    if (end >= data.length())
      end = data.length();
    else
      for (; start < end; end--) {
        if (isWhitespace(data.charAt(end)))
          break;
      }
    if (start >= end)
      return new String[] {};
    return data.substring(start, end).split("[^A-Za-z]+");
  }

  static boolean sGrow = true;

  static void growMatches(List<NGramHitMatch> list, String queryData, String hitData) {
    // TODO: handle translations, whitespace collapse, etc.
    NGramHitMatch prev = null, match = null;
    int maxHitIndex = hitData.length();
    int maxQueryIndex = queryData.length();
    for (Iterator<NGramHitMatch> iter = list.iterator(); iter.hasNext();) {
      prev = match;
      match = iter.next();
      // TODO: find out why match.startInQuery is sometimes -1 (causing exception without this warning)
      if (match.startInHit<0 || match.endInHit<match.startInHit || match.startInQuery<0 || match.endInQuery<match.startInQuery) { System.err.println("WARNING: invalid match "+match.startInHit+".."+match.endInHit+", "+match.startInQuery+".."+match.endInQuery); continue; } // validate
      // overlap adjustment if query overlaps and hit doesn't
      if (prev != null && prev.endInQuery >= match.startInQuery && !(prev.endInHit > match.startInHit && prev.startInHit < match.endInHit)) {
        prev.moveEnd(match.startInQuery - prev.endInQuery - 2);
      }
      // grow match before start
      if (sGrow)
        for (int i = match.startInHit - 1; i >= 0; i--, match.moveStart(-1)) {
          if (match.startInQuery <= 0)
            break;
          char h = hitData.charAt(i);
          char q = queryData.charAt(match.startInQuery - 1);
          if (isWhitespace(h) && isWhitespace(q))
            continue;
          if (Character.toLowerCase(h) != Character.toLowerCase(q))
            break;
        }
      // remove whitespace at start
      for (; match.startInQuery >= 0 && isWhitespace(queryData.charAt(match.startInQuery)); match.moveStart(1))
        ;
      // grow match after end
      if (sGrow)
        for (int i = match.endInHit + 1; i < maxHitIndex; i++, match.moveEnd(1)) {
          if (match.endInQuery + 1 >= maxQueryIndex)
            break;
          char h = hitData.charAt(i);
          char q = queryData.charAt(match.endInQuery + 1);
          if (isWhitespace(h) && isWhitespace(q))
            continue;
          if (Character.toLowerCase(h) != Character.toLowerCase(q))
            break;
        }
      // remove whitespace at end
      for (; match.endInQuery < maxQueryIndex && isWhitespace(queryData.charAt(match.endInQuery)); match.moveEnd(-1))
        ;
      // overlap adjustment if query overlaps and hit doesn't
      if (prev != null && prev.endInQuery >= match.startInQuery && !(prev.endInHit > match.startInHit && prev.startInHit < match.endInHit)) {
        match.moveStart(prev.endInQuery - match.startInQuery + 2);
      }
    }
  }

  // -------------
  // main method
  // -------------

  static public void openInBrowser(File f) throws IOException {
    if (f == null)
      return;
    String cmd = (NGramUtils.sMachineType == NGramUtils.sMac ? "open" : "rundll32 SHELL32.DLL,ShellExec_RunDLL");
    Runtime.getRuntime().exec(cmd + " " + f.getAbsolutePath());
  }

  static public void mainFromServlet(String dir, PrintWriter out, Hashtable<String,String> formData) throws IOException {
    String header = dir + "/page-header.txt";
    if (new File(header).exists())
      out.println(NGramUtils.readInFile(header));
    else {
      out.println("<html>");
      out.println("<head>");
      out.println("<title>Manipulus Florum Search Result</title>");
      out.println("</head>");
      out.println("<body>");
    }

    MFSearchServlet mfsearch = new MFSearchServlet(dir);
    int matches = mfsearch.runServletSearch(out, formData);

    String footer = dir + "/page-footer.txt";
    if (new File(footer).exists())
      out.println(NGramUtils.readInFile(footer));
    else {
      out.println("</body>");
      out.println("</html>");
    }
  }

  static public final String sUsage = MFSearch.class.getName() + " [file.cfg]";

  static public void main(String[] args) throws Exception {
    MFSearchGUI gui = new MFSearchGUI();

    if (args.length >= 2) {
      System.err.println(sUsage);
      System.exit(1);
    }

    // read in config file
    if (args.length == 1) {
      try {
        FileReader fr = new FileReader(args[0]);
        BufferedReader in = new BufferedReader(fr);
        for (;;) {
          String line = in.readLine();
          if (line == null)
            break;
          int eq = line.indexOf('=');
          if (line.startsWith("#") || eq < 0)
            continue;
          String x = line.substring(0, eq);
          String y = line.substring(eq + 1);
          if (x.equalsIgnoreCase("n")) {
            gui.fNGramSize = Integer.parseInt(y);
          } else if (x.equalsIgnoreCase("w")) {
            gui.fWindowSize = Integer.parseInt(y);
          } else if (x.equalsIgnoreCase("mappings")) {
            NGram.sSubwordMappingsFile = y;
          } else if (x.equalsIgnoreCase("queryFileRelative")) {
            gui.fQueryFileDefault = gui.fBaseDir + y;
          } else if (x.equalsIgnoreCase("reindex") && y.equalsIgnoreCase("true")) {
            String indexDir = gui.fBaseDir + "index";
            if (new File(indexDir).exists()) {
              SplitMF_XML.deleteRecursive(indexDir);
            }
          } else {
            // ignore extra stuff
          }
        }
        fr.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

    }

    gui.start();
  }
}
