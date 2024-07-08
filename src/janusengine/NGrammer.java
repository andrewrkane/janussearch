/*
 * (C) Copyright 2008 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

package janusengine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

/**
 * Convert input to ngrams and output them to stdout.
 */
public class NGrammer {

  String fBaseDir;
  File fQueryFile;
  int fNGramSize;
  int fWindowSize;

  public NGrammer() throws IOException {
    fBaseDir = new File(".").getCanonicalPath() + "/";
    reset();
  }

  public void reset() {
    fNGramSize = 20;
    fWindowSize = 10;
  }

  public void outputNGrams(String data, PrintStream out) {

    NGram.sNGramSize = fNGramSize;
    NGram.sWindowSize = fWindowSize;

    NGram.Winnowing w = new NGram.Winnowing();
    w.winnow(data);
    int length = w.fOutputEntries.size();
    for (int i = 0; i < length; i++) {
      NGram.Entry e = w.fOutputEntries.get(i);
      out.println("(" + e.fChars + ") ");
    }
  }

  //-------------
  // main method
  //-------------

  static public void main(String[] args) throws Exception {
    NGrammer n = new NGrammer();
    String query;
    if (args.length > 0) {
      // args[0] is the filename
      query = NGramUtils.readInFileAndClean(args[0], "<br>\n");
    } else {
      // stdin
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      StringBuffer sb = new StringBuffer();
      for (;;) {
        int c = in.read();
        if (c == -1)
          break;
        sb.append((char) c);
      }
      query = sb.toString();
    }
    n.outputNGrams(query, System.out);
  }
}
