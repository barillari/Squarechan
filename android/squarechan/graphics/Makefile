all: icons

icons: ../src/main/res/drawable-hdpi/ic_app_icon.png ../src/main/res/drawable-mdpi/ic_app_icon.png ../src/main/res/drawable-ldpi/ic_app_icon.png

../src/main/res/drawable-hdpi/ic_app_icon.png: squarechan512x512.png
	convert -resize 72x72 $< $@

../src/main/res/drawable-mdpi/ic_app_icon.png: squarechan512x512.png
	convert -resize 48x48 $< $@

../src/main/res/drawable-ldpi/ic_app_icon.png: squarechan512x512.png
	convert -resize 48x48 $< $@