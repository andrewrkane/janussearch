/*
 * (C) Copyright 2008 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

package janusengine;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;

import javax.swing.filechooser.FileFilter;

public class BasicFileFilter extends FileFilter {

  private HashSet<String> fFilters = new HashSet<String>();
  private String fDescription = null;

  public BasicFileFilter(String extension, String desc) {
    fFilters.add(extension.toLowerCase());
    fDescription = desc;
    if (fDescription == null)
      fDescription = "(";
    else
      fDescription += " (";
    for (Iterator<String> iter = fFilters.iterator(); iter.hasNext();) {
      fDescription += iter.next();
      if (!iter.hasNext())
        break;
      fDescription += ", ";
    }
    fDescription += ")";
  }

  public boolean accept(File f) {
    if (f != null) {
      if (f.isDirectory())
        return true;
      String extension = getExtension(f);
      if (extension != null && fFilters.contains(getExtension(f)))
        return true;
    }
    return false;
  }

  private String getExtension(File f) {
    if (f != null) {
      String filename = f.getName();
      int i = filename.lastIndexOf('.');
      if (i > 0 && i < filename.length() - 1)
        return filename.substring(i + 1).toLowerCase();
    }
    return null;
  }

  public String getDescription() {
    return fDescription;
  }
}
