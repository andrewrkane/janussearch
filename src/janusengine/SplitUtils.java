/*
 * (C) Copyright 2014 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

package janusengine;

import java.io.File;

public class SplitUtils {

  // ------------------------
  // static utility methods
  // ------------------------

  /**
   * First occurrence of start to following occurrence of end.
   */
  static protected String getFirst(String data, String start, String end) {
    int s = data.indexOf(start);
    if (s < 0) return null;
    s += start.length();
    int e = data.indexOf(end, s);
    if (e < 0) return null;
    return data.substring(s, e);
  }

  /**
   * First occurrence of any start to following occurrence of any end.
   */
  static protected String getFirst(String data, String[] start, String[] end) {
    int s = Integer.MAX_VALUE;
    for (int i = 0; i < start.length; i++) {
      int t = data.indexOf(start[i]);
      if (t >= 0)
        s = Math.min(s, t + start[i].length());
    }
    if (s == Integer.MAX_VALUE)
      return null;
    int e = Integer.MAX_VALUE;
    for (int i = 0; i < end.length; i++) {
      int t = data.indexOf(end[i], s);
      if (t >= 0)
        e = Math.min(e, t);
    }
    if (e == Integer.MAX_VALUE) return null;
    return data.substring(s, e);
  }

  /**
   * result - some may be null
   */
  static protected String[] getAll(String data, String start, String end) {
    boolean empty = true;
    String[] r = data.split(start);
    r[0]=null; // skip first value
    for (int i = 1; i < r.length; i++) {
      int c = r[i].indexOf(end);
      if (c <= 0) r[i] = null;
      else {
        r[i] = r[i].substring(0,c).trim();
        if (r[i] == "") r[i] = null;
        else empty = false;
      }
    }
    return (empty ? null : r);
  }

  /**
   * Start up to first of the ends
   * result - some may be null
   */
  static protected String[] getAll(String data, String start, String[] end) {
    boolean empty = true;
    String[] r = data.split(start);
    r[0]=null; // skip first value
    for (int i = 1; i < r.length; i++) {
      int c = -1;
      for (int k=0; k<end.length; k++) {
        int cc = r[i].indexOf(end[k]);
        if (c<0) c=cc;
        else if (cc>=0) c=Math.min(c,cc);
      }
      if (c <= 0) r[i] = null;
      else {
        r[i] = r[i].substring(0,c).trim();
        if (r[i] == "") r[i] = null;
        else empty = false;
      }
    }
    return (empty ? null : r);
  }

  static public final boolean deleteRecursive(String path) {
    return deleteRecursive(new File(path));
  }

  static public final boolean deleteRecursive(File f) {
    if (!deleteRecursiveSubFiles(f)) return false;
    if (!f.delete()) return false;
    return true;
  }

  static public final boolean deleteRecursiveSubFiles(File f) {
    if (f.isDirectory()) {
      File[] list = f.listFiles();
      for (int i = 0; i < list.length; i++) {
        if (!deleteRecursive(list[i])) return false;
      }
    }
    return true;
  }

  static public final boolean containsAnyChars(String invalidChars, String data) {
    char[] chars = invalidChars.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      if (data.indexOf(chars[i]) >= 0) return true;
    }
    return false;
  }

}
