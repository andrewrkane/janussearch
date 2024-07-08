/*
 * (C) Copyright 2014 Andrew R J Kane <arkane (at) uwaterloo.ca>, All Rights Reserved.
 *     Released for academic purposes only, All Other Rights Reserved.
 *     This software is provided "as is" with no warranties, and the authors are not liable for any damages from its use.
 * project: https://github.com/andrewrkane/janussearch
 */

import java.io.PrintWriter;
import java.io.IOException;
import java.util.Hashtable;

import janusengine.MFSearch;

class JanusCGI {
    public static void main( String args[] ) {
        System.out.println(cgi_lib.Header());

        Hashtable<String, String> formData = cgi_lib.ReadParse(System.in);
        try {
          MFSearch.mainFromServlet(".", new PrintWriter(System.out,true), formData);
        } catch (IOException e) {
            System.out.println(e);
        }

        System.out.println(cgi_lib.HtmlBot());
    }
}
