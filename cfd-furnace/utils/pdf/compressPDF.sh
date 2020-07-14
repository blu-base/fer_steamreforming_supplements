#!/bin/bash

FILE=errorReport


#gs -sDEVICE=pdfwrite -dCompatibilityLevel=1.4 -dPDFSETTINGS=/ebook -dNOPAUSE -dQUIET -dBATCH -sOutputFile=${FILE}Small.pdf $FILE.pdf
gs -sDEVICE=pdfwrite -dNumRenderingThreads=4 -dBandBufferSpace=5000000000 -sBandListStorage=memory -dBufferSpace=10000000000 -dCompatibilityLevel=1.4 -dPDFSETTINGS=/ebook -dNOPAUSE -dQUIET -dBATCH -sOutputFile=${FILE}Small.pdf $FILE.pdf
