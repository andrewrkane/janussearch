/*
 * (C) Copyright 2008 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

package janusengine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;
import java.io.IOException;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class IndexUtils {

  /**
   * @return number of ngrams picked
   */
  static public int indexDocs(String docDir, String indexDir) {
    final File docDirFile = new File(docDir);
    if (!docDirFile.exists() || !docDirFile.canRead()) {
      System.err.println("Document directory '" + docDirFile.getAbsolutePath() + "' does not exist or is not readable, please check the path");
      System.exit(1);
    }

    Date start = new Date();
    try {
      Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT);
      IndexWriter writer = new IndexWriter(FSDirectory.open(new File(indexDir)), analyzer, true, new IndexWriter.MaxFieldLength(25000)); // TODO: what max field length?
      System.out.println("Indexing to directory '" + indexDir + "'...");
      int result = indexDocs(writer, docDirFile);
      System.out.println("Optimizing...");
      writer.optimize();
      writer.close();

      Date end = new Date();
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");
      return result;
    } catch (IOException e) {
      System.err.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
      return 0;
    }
  }

  static int pacify = 0;
  
  /**
   * @return number of ngrams picked
   */
  static int indexDocs(IndexWriter writer, File file) throws IOException {
    int result = 0;
    if (file.canRead()) {
      if (file.isDirectory()) {
        String[] files = file.list();
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            result += indexDocs(writer, new File(file, files[i]));
          }
        }
      } else {
        // not needed for now.
        //if (pacify++ % 1000 == 0) System.out.println("adding " + pacify + " " + file);
        try {
          Document doc = new Document();
          String path=file.getParent(); int k=Math.max(path.lastIndexOf("/"),path.lastIndexOf("\\")); if (k>=0) path=path.substring(k+1);
          doc.add(new Field("path", path, Field.Store.YES, Field.Index.ANALYZED));
          String name=file.getName(); int t=name.lastIndexOf("."); if (t>=0) name=name.substring(0,t);
          doc.add(new Field("name", name, Field.Store.YES, Field.Index.NOT_ANALYZED));
          //doc.add(new Field("modified", DateTools.timeToString(file.lastModified(), DateTools.Resolution.MINUTE), Field.Store.YES, Field.Index.NOT_ANALYZED));
          //doc.add(new Field("name", NGram.convertToNormalized(file.getName()), Field.Store.NO, Field.Index.ANALYZED));
          String original = NGramUtils.readInFile(file.getCanonicalPath());
          // link
          String l = NGramUtils.extractTag(original,"<L>","</L>");
          if (l!=null) doc.add(new Field("L", l, Field.Store.YES, Field.Index.NOT_ANALYZED));
          // original
          doc.add(new Field("original", original, Field.Store.YES, Field.Index.NOT_ANALYZED)); // allow access to the origial form
          // searchable data
          String originalQ = NGramUtils.truncateAt(original, "<");
          //String originalR = (original!=originalQ ? original.substring(originalQ.length()) : null);
          String originalR = NGramUtils.extractTag(original,"<cite>","</cite>"); // TODO: query biblio+link, not just cite?
          NGram.CombinedDocument cb = NGram.createDocumentReader(new StringReader(originalQ),originalR);
          doc.add(new Field("contents", cb.r)); // original form and ngrams

          writer.addDocument(doc);
          result += cb.w.fOutputEntries.size();

        } catch (FileNotFoundException fnfe) {
        }
      }
    }
    return result;
  }
}
