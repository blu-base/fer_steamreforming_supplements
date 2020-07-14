#!/bin/bash
for f in vel*.jpg; do
	convert $f -crop 1x1-48-48@ +repage +adjoin temp.jpg
 	convert temp.jpg -crop 3104x2054+0+250 c-$f
done
rm temp.jpg

for f in temp_*.jpg; do
	convert $f -crop 1x1-48-48@ +repage +adjoin temp.jpg
 	convert temp.jpg -crop 3104x2054+0+250 c-$f
done
rm temp.jpg
