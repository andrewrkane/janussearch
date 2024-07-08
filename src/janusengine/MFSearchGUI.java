/*
 * (C) Copyright 2008 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

package janusengine;

import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * Check results for different values of NGram size and Window size.
 */
public class MFSearchGUI extends MFSearchServlet {

  public String fQueryFileDefault;
  File fQueryFile;

  public MFSearchGUI() throws IOException {
    super(".");
    fQueryFileDefault = fBaseDir + "query/Moralium dogma philosophorum.txt";
    reset();
    createWindowObjects();
  }

  public void reset() {
    super.reset();
    fQueryFile = new File(fQueryFileDefault);
    if (fKeywordsField != null) fKeywordsField.setText("");
    setSettings();
  }

  public void start() throws Exception {
    reset();
    fFrame.setVisible(true);
  }

  public void runSearch(boolean bUseQuery) {
    try {
      File outputFile = File.createTempFile("MF_QueryResult", ".html");
      FileWriter fout = new FileWriter(outputFile);
      PrintWriter out = new PrintWriter(fout);
      out.println("<html><body>");

      Hashtable<String,String> formData = new Hashtable<String,String>();
      formData.put("queryfrom", fQueryfromField.getText());
      formData.put("dataset", fDatasetField.getText());
      formData.put("keywords", fKeywordsField.getText());
      if (bUseQuery) {
        formData.put("query", NGramUtils.readInFileAndClean(fQueryFile.getAbsolutePath(), "<br>\n"));
      }
      runServletSearch(out, formData);

      out.println("</body></html>");
      out.flush();
      fout.close();

      // open in browser
      openInBrowser(outputFile);

    } catch (Exception e) {
      if (e instanceof RuntimeException)
        throw (RuntimeException) e;
      else
        throw new RuntimeException(e);
    }
  }

  //-------------
  // Window Code
  //-------------

  JFrame fFrame;
  MyEventListener fEL = new MyEventListener();

  // queryfrom
  JTextField fQueryfromField;

  // dataset
  JTextField fDatasetField;

  // keywords
  JTextField fKeywordsField;

  // query file
  JLabel fQueryFileLabel;
  JButton fOpenQueryFile;

  // buttons
  JButton[] fButtons;

  public void createWindowObjects() {
    String title = "Manipulus Florum NGram Search Engine";
    JPanel pane = new JPanel();
    pane.setLayout(new GridLayout(14, 1));
    pane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
    pane.add(new JLabel("<HTML><u><b>" + title + "</b></u></html>", JLabel.CENTER));
    // query file
    fQueryFileLabel = new JLabel();
    pane.add(fQueryFileLabel);
    fOpenQueryFile = new JButton("Open Query File");
    fOpenQueryFile.addActionListener(fEL);
    pane.add(fOpenQueryFile);
    pane.add(new JLabel());
    // keywords
    pane.add(new JLabel("keywords:"));
    pane.add(fKeywordsField = new JTextField(40));
    pane.add(new JLabel());
    // dataset
    pane.add(new JLabel("dataset:"));
    pane.add(fDatasetField = new JTextField(40));
    pane.add(new JLabel());
    // queryfrom
    pane.add(new JLabel("queryfrom:"));
    pane.add(fQueryfromField = new JTextField(40));
    pane.add(new JLabel());
    // buttons
    fButtons = new JButton[] { new JButton("Search"), new JButton("NoFile"), new JButton("Reset"), new JButton("Exit") };
    JPanel paneB = new JPanel(new GridLayout(1,fButtons.length));
    for (int i = 0; i < fButtons.length; i++) {
      paneB.add(fButtons[i]);
      fButtons[i].addActionListener(fEL);
    }
    pane.add(paneB);
    // frame
    fFrame = new JFrame();
    fFrame.setContentPane(pane);
    fFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    fFrame.setSize(400, 300);
    setSettings();
  }

  void setSettings() {
    if (fFrame == null)
      return;
    // query file
    fQueryFileLabel.setText("Query File : " + fQueryFile.getName());
  }

  //---------------
  // EventListener
  //---------------

  private class MyEventListener implements ActionListener {
    int prevx, prevy;
    boolean apple = false;
    boolean shift = false;
    File saveTo = null;

    public void actionPerformed(ActionEvent e) {
      Object s = e.getSource();
      // query file
      if (s == fOpenQueryFile) {
        BasicFileFilter filter = new BasicFileFilter("txt", "Text files");
        final JFileChooser fc = new JFileChooser();
        fc.setFileFilter(filter);
        fc.setCurrentDirectory(fQueryFile.getParentFile());
        int returnVal = fc.showOpenDialog(fFrame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          fQueryFile = fc.getSelectedFile();
        }
        setSettings();
      }
      // buttons
      for (int i = 0; i < fButtons.length; i++) {
        if (s == fButtons[i]) {
          switch (i) {
            case 0: runSearch(true); break;
            case 1: runSearch(false); break;
            case 2: reset(); break;
            case 3: System.exit(0); break;
            default: throw new RuntimeException("Unsupported button " + i);
          }
        }
      }
    }

  } // MyEventListener

}
