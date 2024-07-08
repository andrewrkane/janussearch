/*
 * (C) Copyright 2008 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

package janusengine;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.Collections;
import java.util.Iterator;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * Check results for different values of NGram size and Window size.
 */
public class MFSearchServlet extends MFSearch {

  static public String sStartMarking = "<font style='BACKGROUND-COLOR: #ff6'>";
  static public String sEndMarking = "</font>";
  
  public MFSearchServlet(String baseDir) throws IOException { super(baseDir); }

  /** creates the index if it does not already exist */
  public void createIndex() throws IOException {
    String indexDir = fBaseDir + "index";
    String docDir = fBaseDir + "data";
    NGram.sBaseDir = fBaseDir;
    NGram.sNGramSize = fNGramSize;
    NGram.sWindowSize = fWindowSize;

    // lock on file to make this threadsafe
    File lockFile = new File(fBaseDir, "indexbuild.lock");
    if (!lockFile.exists()) {
      try { lockFile.createNewFile(); } catch (Exception e) {}
    }
    RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
    FileLock lock = raf.getChannel().lock();
    try {
      // index files if not done already
      if (!new File(indexDir).exists()) {
        try {
          IndexUtils.indexDocs(docDir, indexDir);
        } catch (RuntimeException e) { e.printStackTrace(System.err); SplitMF_XML.deleteRecursive(indexDir); throw e; }
      }
    } finally {
      lock.release(); lock = null;
      raf.close(); raf = null;
      lockFile.delete();
    }
  }

  static String doEscape(String s) {
    StringWriter sb = new StringWriter();
    int size=s.length();
    for (int i=0;i<size;i++) {
      char c=s.charAt(i);
      switch (c) {
        case '&': sb.write("&amp;"); break;
        case '<': sb.write("&lt;"); break;
        case '>': sb.write("&gt;"); break;
        case '"': sb.write("&quot;"); break;
        case '\'': sb.write("&#x27;"); break;
        case '/': sb.write("&#x2F;"); break;
        default: sb.write(c);
      }
    }
    return sb.toString();
  }

  static String[] ds = {"mf","lp","vc"};
  static byte[] dsEnum = {0x1, 0x2, 0x4};
  static byte ds2Enum(String n) { for (int i=0;i<ds.length;i++) if (ds[i].equals(n)) return dsEnum[i]; return 0; }

  /** returns number of quotations with a match */
  public int runServletSearch(PrintWriter out, Hashtable<String,String> formData) {
    return runServletSearch(out, formData, true);
  }
  public int runServletSearch(PrintWriter out, Hashtable<String,String> uncleanFormData, boolean bOutputEmpty) {

    // escape all form data to prevent XSS attacks
    Hashtable<String,String> formData = new Hashtable<String,String>();
    for (Iterator<String> it=uncleanFormData.keySet().iterator(); it.hasNext();) {
      String key = it.next();
      formData.put(key, doEscape(uncleanFormData.get(key)).trim());
    }

    // ensure index exists
    try {
      createIndex();
    } catch (Exception e) { out.println("<p>"); e.printStackTrace(out); return 0; }

    // query formats
    String queryfrom = formData.getOrDefault("queryfrom","");
    if (queryfrom.equals("stats")) {
      for (int i=0;i<ds.length;i++) {
        try {
          DocQ[] h = runBaseQuery("+path:"+ds[i]); out.println("<p> Found "+h.length+" "+ds[i]+" quotations.");
          for (int m=0; m<h.length; m++) {
            if (h[m].name==null || h[m].name.length()<=0) out.println("<br>no name: "+h[m].originalQ);
            if (i!=0 && (h[m].link==null || h[m].link.length()<=0)) out.println("<br>no link: "+h[m].name);
            if (h[m].cite==null || h[m].cite.length()<=0) out.println("<br>no cite: "+h[m].name);
            if (h[m].originalQ==null || h[m].originalQ.length()<=0) out.println("<br>no originalQ: "+h[m].name);
          }
        } catch (Exception e) { out.println("<p>"); e.printStackTrace(out); }
      }
      return 0;

    } else if (queryfrom.equals("names")) {
      for (int i=0;i<ds.length;i++) {
        try {
          String lastpre="", prenames=""; int precount=0;
          DocQ[] h = runBaseQuery("+path:"+ds[i]); out.println("<p> Found "+h.length+" "+ds[i]+" quotations.");
          java.util.Arrays.sort(h, DocQ.sComparator);
          for (int m=0; m<h.length; m++) {
            String[] pre=h[m].splitName();
            if (lastpre.equals(pre[0])) { prenames+=","+pre[1]; precount++; }
            else { if (precount>0) out.println("<br>"+lastpre+" "+precount+" - ("+prenames+")"); lastpre=pre[0]; prenames=""; precount=1; prenames=pre[1]; }
          } if (precount>0) out.println("<br>"+lastpre+" "+precount+" - ("+prenames+")");
        } catch (Exception e) { out.println("<p>"); e.printStackTrace(out); }
      }
      return 0;

    } else if (queryfrom.startsWith(ds[0]+"+") || queryfrom.startsWith(ds[1]+"+") || queryfrom.startsWith(ds[2]+"+")) {
      // restrict to other datasets
      String queryfromN = queryfrom.substring(0,2);
      String dataset = "-path:"+queryfromN;
      // restrict target
      byte target=0; String tg=queryfrom.substring(3);
      for (;;) {
        if (tg.length()<2) { out.println("<p> Invalid queryfrom "+queryfrom); return 0; }
        byte b = ds2Enum(tg.substring(0,2));
        if (b!=0) target|=b; else { out.println("<p> Invalid queryfrom "+queryfrom); return 0; }
        if (tg.length()==2) break;
        if (tg.length()>2 && tg.charAt(2)!='+') { out.println("<p> Invalid queryfrom "+queryfrom); return 0; }
        tg=tg.substring(3);
      }
      if ((ds2Enum(queryfromN)&target) != 0) { out.println("<p> Invalid queryfrom "+queryfrom); return 0; }
      // restrict to high overlaps
      int minimumOverlapSize = 0; // TODO: increase this and include in normal ngram query?
      int maxQuotationsToDisplay = Integer.MAX_VALUE; // modify later if want to restrict

      int quotationsThatMatch = 0;
      try {
        // find all quotations in queryfrom
        DocQ[] h = runBaseQuery("+path:"+queryfromN);
        Object[] fh = new Object[h.length];
        byte[] dss = new byte[h.length];
        for (int m=0; m<h.length; m++) {
          Vector<FullHit> fullhits = new Vector<FullHit>(); fh[m]=fullhits;
          dss[m]=0;
          runQuery(dataset, "", h[m].originalQ, maxQuotationsToDisplay, minimumOverlapSize, fullhits);
          for (int x=0; x<fullhits.size(); x++) {
            byte b = ds2Enum(fullhits.get(x).docq.path);
            if (b!=0) dss[m]|=b; else throw new Exception("Invalid path="+fullhits.get(x).docq.path);
          }
        }
        // counts
        out.println("<p> Found quotations: ");
        out.println("<br> "+h.length+" "+queryfromN);
        byte f = ds2Enum(queryfromN); if (f==0) throw new Exception("Internal Error queryfromN="+queryfromN);
        for (int i=0;i<ds.length;i++) {
          if (dsEnum[i]==f) continue;
          int count=0; for (int m=0; m<h.length; m++) if ((dss[m]&dsEnum[i]) == dsEnum[i]) count++;
          out.println("<br> "+count+" "+queryfromN+"+"+ds[i]);
        }
        String tname=queryfromN; byte tb=0;
        for (int i=0;i<ds.length;i++) {
          if (dsEnum[i]==f) continue;
          tname+="+"+ds[i]; tb|=dsEnum[i];
        }
        int count=0; for (int m=0; m<h.length; m++) if ((dss[m]&tb) == tb) count++;
        out.println("<br> "+count+" "+tname);
        out.println("<br>");
        // run each quotation separately
        out.println("<br> Overlaps for "+queryfrom);
        out.println("<br>");
        out.println("<table>");
        out.println(" <tr>");
        out.println("  <th width='15%'>Quote Name</th>");
        out.println("  <th>Quote & Overlaps</th>");
        out.println(" </tr>");
        int outputNumber=1;
        for (int m=0; m<h.length; m++) {
          if ((dss[m]&target) != target) continue;
          Vector<FullHit> fullhits = (Vector<FullHit>)fh[m];
          if (fullhits.size()<=0) continue;
          out.println(" <tr><td colspan=3 bgcolor='#C0C0C0'>&nbsp;"+outputNumber+". "+getQuoteLink(h[m])+"</td>");
          outputNumber++;
          //doDRData(out, data, allFullHits);
          out.println(" </tr>");
          for (int x=0; x<fullhits.size(); x++) {
            byte b = ds2Enum(fullhits.get(x).docq.path);
            if ((b&target) != b) continue;
            doERHit(out, h[m].originalQ, false, -1, fullhits.get(x));
          }
          out.println(" <tr><td colspan=3 style='line-height:10px;'>&nbsp;</td></tr>");
          quotationsThatMatch += fullhits.size();
        }
        out.println("</table><br>");
      } catch (Exception e) { out.println("<p>"); e.printStackTrace(out); }
      return quotationsThatMatch;

    } else if (queryfrom.equals("") || queryfrom.equals("textfield")) {
      // input from form
      String dataset = formData.getOrDefault("dataset","");
      String keywords = formData.getOrDefault("keywords","");
      String data = formData.getOrDefault("query","");
      String displayformat = formData.getOrDefault("displayformat","");

      // this can also prune out keyword matches, so doesn't work well for general interface.
      int minimumOverlapSize = 0; // modify later in code to restrict results
      int maxQuotationsToDisplay = Integer.MAX_VALUE; // modify later if want to restrict
      // displayformat
      boolean bDF_Excerpt=true, bDF_Document=true;
      if ("excerpt".compareToIgnoreCase(displayformat) == 0) { bDF_Excerpt=true; bDF_Document=false; }
      else if ("document".compareToIgnoreCase(displayformat) == 0) { bDF_Excerpt=false; bDF_Document=true; }

      // normal runs
      Vector<FullHit> allFullHits = new Vector<FullHit>();
      try {
        int err = runQuery(dataset, keywords, data, maxQuotationsToDisplay, minimumOverlapSize, allFullHits);
        if (err==-1) { if (bOutputEmpty) out.println("<p> Empty query."); return 0; }
        if (bDF_Excerpt) { doExcerptReport(out, data, bDF_Document, allFullHits); }
        if (bDF_Document) { doDocumentReport(out, keywords, data, allFullHits); }
      } catch (Exception e) { out.println("<p>"); e.printStackTrace(out); }
      return allFullHits.size(); // quotationsThatMatch

    } else {
      out.println("<p> Invalid 'query from dataset' specified (" + queryfrom + ").");
      return 0;
    }
  }

  static class DocQ {
    String path, name, link, originalQ, cite;
    DocQ(Document doc) {
      path=doc.get("path");
      name=doc.get("name").toLowerCase().trim();
      link=doc.get("L");
      originalQ=NGramUtils.truncateAt(doc.get("original"), "<");
      cite=NGramUtils.extractTag(doc.get("original"),"<cite>","</cite>"); }
    String[] splitName() {
      int t=name.lastIndexOf(" "); if (name.charAt(t+1)=='(') { t=name.lastIndexOf(" ",t-1); }
      return new String[] {name.substring(0,t),name.substring(t+1)};
    }
    static DocQComparator sComparator = new DocQComparator();
  }
  static class DocQComparator implements java.util.Comparator<DocQ> {
    public int compare(DocQ a, DocQ b) { return a.name==null ? -1 : a.name.compareTo(b.name); }
  }

  DocQ[] runBaseQuery(String fullQuery) throws Exception {
    // query
    String field = "contents";
    Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT);
    QueryParser parser = new QueryParser(Version.LUCENE_CURRENT, field, analyzer);
    Query query = parser.parse(fullQuery);
    // index
    String indexDir = fBaseDir + "index";
    IndexReader reader = IndexReader.open(FSDirectory.open(new File(indexDir)));
    Searcher searcher = new IndexSearcher(reader);
    TopScoreDocCollector collector = TopScoreDocCollector.create(100000, true); // TODO: store all hits
    // search
    searcher.search(query, collector);
    ScoreDoc[] hits = collector.topDocs().scoreDocs;
    DocQ[] r = new DocQ[hits.length];
    for (int m=0; m<hits.length; m++) { r[m] = new DocQ(searcher.doc(hits[m].doc)); }
    return r;
  }

  int runQuery(String dataset, String keywords, String data, int maxQuotationsToDisplay, int minimumOverlapSize, Vector<FullHit> allFullHits) throws Exception {
      // get query parts
      NGram.Winnowing wData = new NGram.Winnowing();
      wData.winnow(data);
      String ngramQuery = NGram.convertToQuery(wData);
      String keywordsQuery = NGram.convertToNormalizedKeywordQuery(keywords);
      // empty query
      if (keywordsQuery.equals("") && ngramQuery.equals("")) return -1;
      // truncate results when no ngram query
      //if (ngramQuery.equals("")) maxQuotationsToDisplay = 10; // TODO: truncate value? none for now
      // combine query parts
      String fullQuery = dataset;
      if (!keywordsQuery.equals("")) fullQuery += (fullQuery.equals("") ? "" : " AND ") + "("+keywordsQuery+")";
      if (!ngramQuery.equals("")) fullQuery += (fullQuery.equals("") ? "" : " AND ") + "("+ngramQuery+")";

      // execute the search
      DocQ[] hits = runBaseQuery(fullQuery);
      for (int m = 0; m < hits.length; m++) {
        // matches
        FullHit fullHit = new FullHit();
        getMatchLocations(data, wData, hits[m].originalQ, fullHit.queryOverlaps, fullHit.hitOverlaps);

        // prune small cumulative overlaps
        int overlapTotalSize = 0; for (Iterator<HitRange> iter = fullHit.hitOverlaps.iterator(); iter.hasNext();) { HitRange overlap = iter.next(); overlapTotalSize += overlap.end - overlap.start; }
        if (overlapTotalSize < minimumOverlapSize) { continue; }

        // truncate
        if (allFullHits.size() >= maxQuotationsToDisplay) { allFullHits.add(null); break; }

        fullHit.docq = hits[m];
        allFullHits.add(fullHit);
      }
      return 0;
  }

  static class FullHit {
    Vector<HitRange> queryOverlaps = new Vector<HitRange>();
    Vector<HitRange> hitOverlaps = new Vector<HitRange>();
    DocQ docq;
  }

  static String getQuoteLink(DocQ docq) {
    if (docq.link!=null) {
      return docq.path + " - <a href=\"" + docq.link + "\">" + docq.name + "</a>";
    } else {
      String baseLinkFonsPrimus = "https://manipulus-project.wlu.ca/MFfontes/";
      //String baseLinkFonsPrimus = "http://web.wlu.ca/%7ewwwhist/faculty/cnighman/MFfontes/";
      // String baseLinkVaria = "http://web.wlu.ca/%7ewwwhist/faculty/cnighman/MFuaria/";
      String baseLink = baseLinkFonsPrimus;
      String[] splitQuote = docq.name.split(" ");
      int quoteAlphaCode = splitQuote.length - 1;
      if (splitQuote[quoteAlphaCode].toLowerCase().equals("ubi"))
        return docq.name;
      else {
        if (splitQuote[quoteAlphaCode].startsWith("(") && splitQuote[quoteAlphaCode].endsWith(")")) {
          splitQuote[quoteAlphaCode - 1] += splitQuote[quoteAlphaCode].substring(1, splitQuote[quoteAlphaCode].length() - 1);
          quoteAlphaCode--;
        }
        String nameLink = Character.toUpperCase(splitQuote[0].charAt(0)) + splitQuote[0].substring(1);
        if (nameLink.equals("Gloria"))
          nameLink += Character.toUpperCase(splitQuote[1].charAt(0)) + splitQuote[1].substring(1);
        nameLink += splitQuote[quoteAlphaCode].toUpperCase().replace("U", "V") + ".pdf";
        return docq.path + " - <a href=\"" + baseLink + nameLink + "\">"  + docq.name + "</a>";
        //quoteLink = quoteName; // no quote link for OED right now
      }
    }
  }
  static void doERHit(PrintWriter out, String data, boolean bDF_Document, int resultCount, FullHit fullHit) {
    out.println(" <tr>");
    // output link to quote
    String rc = (resultCount>0 ? ""+resultCount+": " : "");
    out.println("  <td width='15%' valign=top><table border=1 width='100%' height='100%'><tr><td>" + rc + getQuoteLink(fullHit.docq) + "</td></tr></table></td>");
    out.println("  <td>");

    // output quote
    out.println("   <table border=1 width='100%'>");
    out.println("    <tr><td>");
    // original
    out.println(getMarkedData(fullHit.docq.originalQ, fullHit.hitOverlaps, sStartMarking, sEndMarking));
    // cite
    if (fullHit.docq.cite!=null) out.println("<small><small> &mdash; " + fullHit.docq.cite + "</small></small>");
    // biblio
    // TODO: add biblio back when data is cleaned up?
    //String biblio = NGramUtils.extractTag(original,"<biblio>","</biblio>");
    //if (biblio!=null) out.println("<small><small> &mdash; " + biblio + "</small></small>");
    out.println("    </td></tr>");
    out.println("   </table>");

    // hits from input data
    out.println("   <table border=1 width='100%'>");
    Vector<HitSection> hitSections = getHitSections(data, fullHit.queryOverlaps);
    for (Iterator<HitSection> iter = hitSections.iterator(); iter.hasNext();) {
      HitSection hs = iter.next();
      out.println(" <tr><td width=50>");
      // only create link if document report is also being generated
      out.println(!bDF_Document ? hs.fHitLocation : "<a href='#A"+hs.fHitLocation+"'>"+hs.fHitLocation+"</a>");
      out.println(" </td><td>");
      out.println(hs.fHitValue);
      out.println(" </td></tr>");
    }
    out.println("   </table>");
    out.println("  </td>");
    out.println(" </tr>");
  }
  static void doExcerptReport(PrintWriter out, String data, boolean bDF_Document, Vector<FullHit> allFullHits) {
    // excerpt report format
    out.println("<u>Excerpt Report:</u><br>");
    out.println("<table>");
    out.println(" <tr>");
    out.println("  <th width='15%'>Quote Name</th>");
    out.println("  <th>Quote & Overlaps</th>");
    out.println(" </tr>");

    int resultCount = 1;
    for (int m = 0; m < allFullHits.size(); m++) {
      FullHit fullHit = allFullHits.get(m);
      if (fullHit == null) { out.println("<tr><td><i> results truncated </i></td></tr>"); break; }
      doERHit(out, data, bDF_Document, resultCount, fullHit);
      resultCount++;
    }
    out.println("</table><br>");
  }

  static void doDocumentReport(PrintWriter out, String keywords, String data, Vector<FullHit> allFullHits) {
    if (!keywords.equals("")) {
      out.println("<u>Keywords:</u><br>");
      out.println(keywords);
      out.println("<br><br>");
    }
    // output original data in full and connect above to points within full data
    out.println("<u>Document Report:</u><br>");
    doDRData(out, data, allFullHits);
    out.println("<br>");
  }
  static void doDRData(PrintWriter out, String data, Vector<FullHit> allFullHits) {
    // document report format
    Vector<HitRange> allQueryOverlaps = new Vector<HitRange>();
    for (int m = 0; m < allFullHits.size(); m++) {
      FullHit fullHit = allFullHits.get(m);
      if (fullHit != null) allQueryOverlaps.addAll(fullHit.queryOverlaps);
    }
    Collections.sort(allQueryOverlaps, HitRange.sComparator); // sort hit overlaps

    int dataLength = data.length();
    int lastOutputLocation = 0;
    for (Iterator<HitRange> iter = allQueryOverlaps.iterator(); iter.hasNext();) {
      HitRange overlap = iter.next();
      if (lastOutputLocation > overlap.start) continue;
      out.print(data.substring(lastOutputLocation, overlap.start));
      lastOutputLocation = overlap.start;
      // prequote
      out.print("<a name='A" + overlap.start + "'/>" + sStartMarking);
      // quote
      out.print(data.substring(lastOutputLocation, overlap.end + 1));
      lastOutputLocation = overlap.end + 1;
      // postquote
      out.print(sEndMarking);
    }
    out.print(data.substring(lastOutputLocation, dataLength));
  }

  static public class HitSection {
    int fHitLocation;
    String fHitValue;
    public HitSection(int hitLocation, String hitValue) { fHitLocation = hitLocation; fHitValue = hitValue; }
  }

  static public String getMarkedData(String data, Vector<HitRange> overlaps, String startMarking, String endMarking) {
    StringBuffer sb = new StringBuffer();
    int dataLength = data.length();
    int lastOutputLocation = 0;
    for (Iterator<HitRange> iter = overlaps.iterator(); iter.hasNext();) {
      HitRange overlap = iter.next();
      if (overlap.start > dataLength || overlap.end > dataLength || overlap.start<0 || overlap.end<0) continue; // display of quote could be truncated so dropping some overlaps
      if (lastOutputLocation > overlap.start)
        throw new RuntimeException("Invalid " + lastOutputLocation + " " + overlap.start + " " + overlap.end);
      sb.append(data.substring(lastOutputLocation, overlap.start));
      lastOutputLocation = overlap.start;
      // prequote
      sb.append(startMarking);
      // quote
      sb.append(data.substring(lastOutputLocation, overlap.end + 1));
      lastOutputLocation = overlap.end + 1;
      // postquote
      sb.append(endMarking);
    }
    sb.append(data.substring(lastOutputLocation, dataLength));
    return sb.toString();
  }

  /**
   * Combine together hit overlaps found close together into hit sections.
   */
  static public Vector<HitSection> getHitSections(String data, Vector<HitRange> queryOverlaps) {
    // hits from input data
    Vector<HitSection> results = new Vector<HitSection>();
    StringBuffer sb = new StringBuffer();
    int dataLength = data.length();
    int lastOutputLocation = 0;
    int startHit = -1;
    for (Iterator<HitRange> iter = queryOverlaps.iterator(); iter.hasNext();) {
      HitRange overlap = iter.next();
      // debugging
      // System.out.println("overlap = " + overlap.startInQuery + " " +
      // overlap.endInQuery);
      if (lastOutputLocation > overlap.start)
        continue;
      if (overlap.start - lastOutputLocation > sMaxSeparation) {
        // new quote section
        if (lastOutputLocation > 0) {
          String s = data.substring(lastOutputLocation, getCutLocation(1, data, lastOutputLocation, NGram.sWindowSize) + 1);
          sb.append(NGramUtils.truncateAt(s,"----")); // this is the break between alldata-*.txt quotes
          sb.append("\n");
          results.add(new HitSection(startHit, sb.toString()));
          startHit = -1;
          sb = new StringBuffer();
        }
        lastOutputLocation = getCutLocation(-1, data, overlap.start, NGram.sWindowSize);
      }
      if (startHit < 0) {
        startHit = overlap.start;
        String s = data.substring(lastOutputLocation, overlap.start);
        sb.append(NGramUtils.trimFront(s,"----")); // this is the break between alldata-*.txt quotes
      } else {
        // TODO: split in middle on ----?
        sb.append(data.substring(lastOutputLocation, overlap.start));
      }
      lastOutputLocation = overlap.start;
      // prequote
      sb.append(sStartMarking);
      // quote
      sb.append(data.substring(lastOutputLocation, overlap.end + 1));
      lastOutputLocation = overlap.end + 1;
      // postquote
      sb.append(sEndMarking);
    }
    if (dataLength > lastOutputLocation + sMaxSeparation / 2) {
      String enddata = data.substring(lastOutputLocation, getCutLocation(1, data, lastOutputLocation, NGram.sWindowSize) + 1);
      sb.append(NGramUtils.truncateAt(enddata,"----")); // this is the break between alldata-*.txt quotes
      sb.append("\n");
    } else {
      sb.append(data.substring(lastOutputLocation, dataLength));
    }
    if (startHit >= 0) {
      results.add(new HitSection(startHit, sb.toString()));
    }
    return results;
  }

  // do not cut in middle of <br> or a word... Also, jump to end of sentence or
  // newline.
  static public int getCutLocation(int jump, String data, int location, int minSize) {
    int dataLength = data.length();
    // jump by NGrams.sWindowSize and then up to whitespace, but also skip
    // entire "<br>"
    for (int i = minSize - 1;; i--) {
      if (location < 0) { location += Math.max(0, Math.abs(jump)); break; }
      if (location >= dataLength) {
        if (dataLength > 0) { location -= Math.min(dataLength - 1, Math.abs(jump)); }
        break;
      }
      char c = data.charAt(location);
      // <br> going right
      if (jump > 0 && c == '<' && isBR(data, location)) {
        i -= 3;
        if (i < 0) { location -= jump; break; }
        location += 4 * jump;
        // <br> going left
      } else if (jump < 0 && c == '>' && isBR(data, location - 3)) {
        i -= 3;
        if (i < 0) { location -= jump; break; }
        location += 4 * jump;
      } else {
        if (i < 0) {
          // after minSize go until newline or end of sentence
          if (jump > 0) {
            if (".!?".indexOf(c) >= 0) break;
            if ("\n\r".indexOf(c) >= 0) { location -= jump; break; }
          } else {
            if (".!?\n\r".indexOf(c) >= 0) {
              while (isWhitespace(data.charAt(location))) { location -= jump; }
              break;
            }
          }
        }
        location += jump;
      }
    }
    return location;
  }

  static public boolean isBR(String data, int location) {
    if (location < 0) return false;
    int dataLength = data.length();
    if (location + 3 >= dataLength) return false;
    if (data.charAt(location) != '<' || data.charAt(location + 3) != '>') return false;
    if (!(data.charAt(location + 1) == 'b' || data.charAt(location + 1) == 'B')) return false;
    if (!(data.charAt(location + 2) == 'r' || data.charAt(location + 2) == 'R')) return false;
    return true;
  }
}
