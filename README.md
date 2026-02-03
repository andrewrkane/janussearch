:# janussearch

janus (intertextuality) search engine

1. clone this repository inside your cgi-bin/ directory to create cgi-bin/janussearch/ (and ensure NOT publicly visible)

    git clone https://github.com/andrewrkane/janussearch.git

2. copy orignal data into cgi-bin/janussearch/_original/*.xml (and ensure NOT publicly visible)

3. run make command inside cgi-bin/janussearch/ directory

    - create janus.jar from cgi-bin/janussearch/src/*
    - extract individual items from original data files
    - compile lucene index from extracted items

4. copy cgi-bin/janussearch/cgifiles/* to cgi-bin/ and setup permissions for those files

    - set up janus.html page with search html form
    - set up janus.cgi to accept html form input, run search, return result page

5. try running janus in browser via url loading cgi-bin/janus.html
