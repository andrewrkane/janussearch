/*
 * (C) Copyright 2015 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

import janusengine.MFSearchServlet;
import janusengine.NGramUtils;

/**
 * Reads in file names from stdin and runs them through Janus, outputting the results to stdout.
 */
class JanusRunFiles {
  public static void main( String args[] ) {
    boolean bOutputQuotation = false;
    for (int i = 0; i < args.length; i++) { if (args[i].equals("quote")) bOutputQuotation = true; }

    int fn = 0;
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    for (;;) {
      // get file name
      String filename = "";
      try {
        filename = in.readLine();
        if (filename == null) break;
      } catch (IOException e) {
        System.out.println(e);
        break;
      }

      fn++;
      
      try {
        // read in file content
        // TODO: pass content without reading in.  also extract file size.
        String content = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
        // process
        //PrintWriter out = new PrintWriter(System.out,true);
        StringWriter sr = new StringWriter();
        PrintWriter out = new PrintWriter(sr,true);
        Hashtable<String,String> formData = new Hashtable<String,String>();
        formData.put("query", NGramUtils.readInFileAndClean(new StringReader(content), "<br>\n"));
        formData.put("displayformat", "excerpt");
        MFSearchServlet mfsearch = new MFSearchServlet(".");
        int matches = mfsearch.runServletSearch(out, formData, false);
        if (matches > 0) {
          System.out.println();
          System.out.println("filenumber\t" + fn + "\t<br>");
          System.out.println("file\t" + filename + "\t<br>");
          System.out.println("size\t" + content.length() + "\t<br>");
          System.out.println("matches\t" + matches + "\t" + filename + "\t<br>");
          if (bOutputQuotation) System.out.print(sr);
        } else {
          System.out.println("nomatch\t" + filename + "\t<br>");
        }
        System.err.println("" + fn + "\t" + filename);

        //MFSearch.mainFromServlet(".", new PrintWriter(System.out,true), "", content, "excerpt");
      } catch (IOException e) {
        System.out.println(e);
      }
    }
  }
}
