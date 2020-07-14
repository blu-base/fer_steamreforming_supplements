#!/bin/bash

pdflatex -interaction=nonstopmode errorReport.tex
pdflatex -interaction=nonstopmode errorReport.tex
pdflatex -interaction=nonstopmode errorReport.tex


./compressPDF.sh
