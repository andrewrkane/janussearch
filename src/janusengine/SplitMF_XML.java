/*
 * (C) Copyright 2008 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

package janusengine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Basically get quotes from an XMLish file and save them to separate files...
 */
public class SplitMF_XML extends SplitUtils {

  static String sOutputDir;
  static PrintWriter out = null;

  static String sStartElement = "<E>";
  static String sEndElement = "</E>";
  static String sStartQuoteName = "<wd>";
  static String sEndQuoteName = "</wd>";
  static String[] sStartQuote = new String[] { "<p>", "<q>" };
  static String[] sEndQuote = new String[] { "</p>", "</q>" };
  static String sStartCite = "<cite>";
  static String sEndCite = "</cite>";
  static String sStartBiblio = "<biblio>";
  static String[] sEndBiblio = new String[] { "</biblio>", "<p>", "</p>" };
  static String sStartLink = "<L>";
  static String sEndLink = "</L>";
  static String sSplitQuoteByRegex = null;

  static void endFile() {
    if (out != null) {
      out.flush();
      out.close();
      out = null;
    }
  }

  static void newFile(String quotename) throws IOException {
    out = new PrintWriter(new BufferedWriter(new FileWriter(sOutputDir + "/" + quotename + ".txt")));
  }

  static final String sInvalidQuotenameChars = "\"<>'|:;*&^%$#@!";
  static final String sInvalidQuoteChars = "\"<>'|";

  static final String replace(String in, String from, String to) {
    int i;
    while ((i = in.indexOf(from)) >= 0) {
      in = in.substring(0,i) + to + in.substring(i+from.length());
    }
    return in;
  }
  
  static public void outputData(String data) throws IOException {
    String quotename = getFirst(data, sStartQuoteName, sEndQuoteName);
    String quote = getFirst(data, sStartQuote, sEndQuote);
    String[] cite = getAll(data, sStartCite, sEndCite);
    String[] biblio = getAll(data, sStartBiblio, sEndBiblio);
    String[] link = getAll(data, sStartLink, sEndLink);
    if (quotename == null || quote == null)
      return;
    quotename = replace(quotename, "<sup>", "_");
    quotename = replace(quotename, "</sup>", "");
    if (containsAnyChars(sInvalidQuotenameChars, quotename)) {
      System.err.println("Warning: quotename contained invalid chars (" + quotename + ").");
      return;
    }
    if (containsAnyChars(sInvalidQuoteChars, quote)) {
      System.err.println("Warning: quote contained invalid chars (" + quote + ").");
      return;
    }
    if (sSplitQuoteByRegex == null) {
      newFile(quotename);
      out.println(quote);
      if (cite!=null) for (int k=0; k<cite.length; k++) { if (cite[k]!=null) out.println(sStartCite + cite[k] + sEndCite); }
      if (biblio!=null) for (int k=0; k<biblio.length; k++) { if (biblio[k]!=null) out.println(sStartBiblio + biblio[k] + sEndBiblio[0]); }
      if (link!=null) for (int k=0; k<link.length; k++) { if (link[k]!=null) out.println(sStartLink + link[k] + sEndLink); }
      endFile();
    } else {
      String[] split = quote.split(sSplitQuoteByRegex);
      if (split.length == 0) {
        newFile(quotename);
        out.println(quote);
        if (cite!=null) for (int k=0; k<cite.length; k++) { if (cite[k]!=null) out.println(sStartCite + cite[k] + sEndCite); }
        if (biblio!=null) for (int k=0; k<biblio.length; k++) { if (biblio[k]!=null) out.println(sStartBiblio + biblio[k] + sEndBiblio[0]); }
        if (link!=null) for (int k=0; k<link.length; k++) { if (link[k]!=null) out.println(sStartLink + link[k] + sEndLink); }
        endFile();
      } else {
        for (int i = 0; i < split.length; i++) {
          newFile(quotename + "-" + (i + 1));
          out.println(split[i]);
          if (cite!=null) for (int k=0; k<cite.length; k++) { if (cite[k]!=null) out.println(sStartCite + cite[k] + sEndCite); }
          if (biblio!=null) for (int k=0; k<biblio.length; k++) { if (biblio[k]!=null) out.println(sStartBiblio + biblio[k] + sEndBiblio[0]); }
          if (link!=null) for (int k=0; k<link.length; k++) { if (link[k]!=null) out.println(sStartLink + link[k] + sEndLink); }
          endFile();
        }
      }
    }
  }

  static public void run(String inputXMLFile, String outputDir) throws IOException {
    sOutputDir = outputDir;

    // XML file exits
    if (!new File(inputXMLFile).exists()) {
      System.err.println("XML input file not found " + inputXMLFile);
      return;
    }
    BufferedReader in = new BufferedReader(new FileReader(inputXMLFile));

    // clear data directory
    File outputDirFile = new File(outputDir);
    if (outputDirFile.exists()) {
      if (!deleteRecursive(outputDirFile)) {
        System.err.println("Unable to delete output directory " + outputDir);
        System.exit(1);
      }
    }

    // create output directory
    outputDirFile.mkdirs();

    try {
      StringBuffer sb = new StringBuffer();
      int index;
      boolean inside = false;
      for (;;) {
        String line = in.readLine();
        if (line == null)
          break;
        for (;;) {
          // split between start and end strings
          if (!inside) {
            index = line.indexOf(sStartElement);
            if (index >= 0) {
              line = line.substring(index + sStartElement.length(), line.length());
              inside = true;
            }
          }
          if (inside) {
            index = line.indexOf(sEndElement);
            if (index >= 0) {
              sb.append(line.substring(0, index));
              line = line.substring(index + sEndElement.length(), line.length());
              sb.append(" ");
              outputData(sb.toString());
              inside = false;
              sb.setLength(0);
              continue;
            } else {
              sb.append(line);
              sb.append(" ");
            }
          }
          break;
        }
      }
    } finally {
      endFile();
    }
    System.out.println("Done " + SplitMF_XML.class.getSimpleName() + " on " + inputXMLFile + ".");
  }

  static void processConfig(String cfgFile) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(cfgFile));
      for (;;) {
        String line = in.readLine();
        if (line == null)
          break;
        int eq = line.indexOf('=');
        if (line.startsWith("#") || eq < 0)
          continue;
        String x = line.substring(0, eq);
        String y = line.substring(eq + 1);
        if (x.equalsIgnoreCase("startElement")) {
          sStartElement = y;
        } else if (x.equalsIgnoreCase("endElement")) {
          sEndElement = y;
        } else if (x.equalsIgnoreCase("startQuoteName")) {
          sStartQuoteName = y;
        } else if (x.equalsIgnoreCase("endQuoteName")) {
          sEndQuoteName = y;
        } else if (x.equalsIgnoreCase("startQuote")) {
          sStartQuote = new String[] { y };
        } else if (x.equalsIgnoreCase("endQuote")) {
          sEndQuote = new String[] { y };
        } else if (x.equalsIgnoreCase("splitQuoteByRegex")) {
          sSplitQuoteByRegex = y;
        } else {
          // ignore extra stuff
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static void usage() {
    System.err.println("Usage: " + SplitMF_XML.class.getName() + " [-c file.cfg] inputXMLFile outputDirectory");
    System.exit(1);
  }

  static public void main(String args[]) throws Exception {
    if (args.length < 2)
      usage();
    int i = 0;
    String cfgFile = null, xmlFile = null, outputDir = null;
    while (i < args.length) {
      if (args[i].equals("-c")) {
        if (i + 1 > args.length)
          usage();
        cfgFile = args[i + 1];
        i += 2;
      } else if (xmlFile == null) {
        xmlFile = args[i];
        i++;
      } else if (outputDir == null) {
        outputDir = args[i];
        i++;
      } else {
        usage();
      }
    }
    if (cfgFile != null)
      processConfig(cfgFile);
    run(xmlFile, outputDir);
  }

}
