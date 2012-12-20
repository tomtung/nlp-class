#! /bin/bash

time env JAVA_OPTS="-Xmx2G" ./ibm2-align.scala shiji.clean.classical shiji.clean.modern --max-iter 100 -r 0.99999 --pseudo-cnt 0.6 -o shiji.wa.classical-modern
time env JAVA_OPTS="-Xmx2G" ./ibm2-align.scala shiji.clean.modern shiji.clean.classical --max-iter 100 -r 0.99999 --pseudo-cnt 0.6 -o shiji.wa.modern-classical
