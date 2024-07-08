/*
 * (C) Copyright 2014 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

import java.io.IOException;

import janusengine.MFSearchServlet;

class JanusCreateIndex {
    public static void main( String args[] ) {
        try {
          MFSearchServlet mfsearch = new MFSearchServlet(".");
          mfsearch.createIndex();
        } catch (IOException e) {
            System.out.println(e);
        }
    }
}
