#!/bin/bash
sbt $@
# make a noise to indicate if a build worked or failed
if [[ $? == 0 ]]; then play /home/dl/angband/trunk/lib/xtra/sound/hit.wav; else play /home/dl/angband/trunk/lib/xtra/sound/opendoor.wav;   fi

