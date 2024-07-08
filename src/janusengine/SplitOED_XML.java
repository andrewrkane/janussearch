/*
 * (C) Copyright 2014 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
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
import java.util.Scanner;

/**
 * Basically get quotes from an XMLish file and save them to separate files...
 */
public class SplitOED_XML extends SplitUtils {

  static String sOutputDir;
  static String outName = "";
  static FileWriter outFileWriter = null;
  static PrintWriter out = null;

  static String sStartQuoteName = "<hw>";
  static String sEndQuoteName = "</hw>";
  static String sStartQuote = "<q>";
  static String sEndQuote = "</q>";

  static void endFile() {
    if (out != null) { out.flush(); out.close(); out = null; }
    try {
      if (outFileWriter != null) { outFileWriter.close(); outFileWriter = null; }
    } catch (IOException e) {
      e.printStackTrace(System.err);
    }
  }
  
  static String getSafeName(String quotename) {
    // use first three characters
    char[] c = quotename.toLowerCase().toCharArray();
    int j = 0;
    for (int i = 0; i < c.length; i++) {
      if (c[i] >= 'a' && c[i] <= 'z') c[j++] = c[i];
      else c[i] = 0;
    }
    return new String(c, 0, j);
  }
  
  static void newFile(String quotename) throws IOException {
    String name = getSafeName(quotename);
    if (name.equals(outName)) return;
    // create dir - first three characters
    String dir = name.substring(0, Math.min(3,name.length()));
    if (dir.length() <= 0) dir = "other";
    new File(sOutputDir + "/" + dir).mkdir();
    // create file
    endFile();
    outFileWriter = new FileWriter(sOutputDir + "/" + dir + "/" + name + ".txt", true);
    out = new PrintWriter(new BufferedWriter(outFileWriter));
  }

  static int pacify = 0;
  
  static public void outputData(String data) throws IOException {
    String quotename = getFirst(data, sStartQuoteName, sEndQuoteName);
    String[] quotes = getAll(data, sStartQuote, sEndQuote);
    if (quotename == null || quotes == null) return;

    // write
    if (pacify++ % 1000 == 0) System.err.println("Processing " + pacify + " " + quotename);
    newFile(quotename);
    out.println("| <b><u>" + quotename + "</b></u>");
    for (int i = 0; i < quotes.length; i++) {
      if (quotes[i] == null) continue;
      String qd = getFirst(quotes[i], "<qd>","</qd>");
      String a = getFirst(quotes[i], "<a>","</a>");
      String w = getFirst(quotes[i], "<w>","</w>");
      String lc = getFirst(quotes[i], "<lc>","</lc>");
      String qt = getFirst(quotes[i], "<qt>","</qt>");
      if (qt == null) continue;
      out.print(" : " + qt + " -");
      if (qd != null) out.print(" " + qd);
      if (a != null) out.print(", " + a);
      if (w != null) out.print(", " + w);
      if (lc != null) out.print(", " + lc);
      out.println();
    }
    out.println();
    out.println();
  }

  static public void run(String inputXMLFile, String outputDir) throws IOException {
    sOutputDir = outputDir;
    
    // XML file exits
    if (!new File(inputXMLFile).exists()) {
      System.err.println("XML input file not found " + inputXMLFile);
      return;
    }
    Scanner s = new Scanner(new BufferedReader(new FileReader(inputXMLFile))).useDelimiter("</e>");
    
    // clear data directory
    File outputDirFile = new File(outputDir);
    if (outputDirFile.exists()) {
      System.err.println("Delete output directory " + outputDirFile);
      if (!deleteRecursive(outputDirFile)) {
        System.err.println("Unable to delete output directory " + outputDir);
        System.exit(1);
      }
      System.err.println("Done delete.");
    }
    
    // create output directory
    outputDirFile.mkdir();
    
    try {
      StringBuffer sb = new StringBuffer();
      int index;
      boolean inside = false;
      for (;;) {
        if (!s.hasNext()) break;
        String element = s.next();
        if (element == null) break;
        outputData(element);
      }
    } finally {
      endFile();
    }
    System.out.println("Done.");
  }

  static void processConfig(String cfgFile) {
    // TODO: implement cfg if needed
  }

  static void usage() {
    System.err.println("Usage: " + SplitOED_XML.class.getName() + " [-c file.cfg] inputXMLFile outputDirectory");
    System.exit(1);
  }

  static public void main(String args[]) throws Exception {
    if (args.length < 2) usage();
    int i = 0;
    String cfgFile = null, xmlFile = null, outputDir = null;
    while (i < args.length) {
      if (args[i].equals("-c")) {
        if (i + 1 > args.length) usage();
        cfgFile = args[i + 1]; i += 2;
      } else if (xmlFile == null) { xmlFile = args[i]; i++; }
      else if (outputDir == null) { outputDir = args[i]; i++; }
      else { usage(); }
    }
    if (cfgFile != null) processConfig(cfgFile);
    run(xmlFile, outputDir);
  }

}
