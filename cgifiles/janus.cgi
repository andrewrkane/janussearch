#!/bin/sh
cd janussearch
java -classpath janus.jar:lucene.jar -Dcgi.request_method=$REQUEST_METHOD -Dcgi.query_string=$QUERY_STRING JanusCGI
