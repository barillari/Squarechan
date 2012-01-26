all: compiled.js geocompiled.js privacy.html

privacy.html: privacy.html.template 
	env PYTHONPATH=../.. python -c 'import settings; print >> open("$@","w"), open("$<").read().replace("SITENAME", settings.SITENAME)'


compiled.js: mygeo.js jquery.cookie.js jquery-1.4.3.js
	java -jar /home/jdb/squarechan/3rdparty/closure/compiler.jar  --js=jquery-1.4.3.js --js=jquery.cookie.js --js=mygeo.js --js_output_file=compiled.js

geocompiled.js: gears_init.js geo.js
	java -jar /home/jdb/squarechan/3rdparty/closure/compiler.jar --js=gears_init.js --js=geo.js --js_output_file=geocompiled.js

