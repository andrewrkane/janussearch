:# janussearch

janus (intertextuality) search engine

1. clone this repository inside your cgi-bin/ directory to create cgi-bin/janussearch/ (and ensure NOT publicly visible)

    git clone https://github.com/andrewrkane/janussearch.git

2. copy orignal data into cgi-bin/janussearch/_original/*.xml (and ensure NOT publicly visible)

3. run cgi-bin/janussearch/make

4. copy cgi-bin/janussearch/cgifiles/* to cgi-bin/ and setup permissions for those files

5. try running janus in browser via url loading cgi-bin/janus.html
