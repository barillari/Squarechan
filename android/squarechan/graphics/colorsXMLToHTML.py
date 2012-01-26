import xml.dom.minidom, sys

HEADER = "<html><body>"
FOOTER = "</body></html>"

def main():
    doc = xml.dom.minidom.parse(open(sys.argv[1]))
    print HEADER
    for colornode in doc.getElementsByTagName("color"):
        origcolor = color = colornode.firstChild.toxml()
        assert len(color) == 9
        alpha = round(int(color[1:3], 16) / 255., 2)
        color = '#'+color[3:]
        print '<p style="text-decoration:underline;">%s/%s:<p><div style="border:1px solid #000; opacity:%s;width:100%%; height:50px;padding-bottom:10px;background-color:%s">%s</div>' % (colornode.getAttribute("name"), origcolor, alpha, color, origcolor)

    print FOOTER

main()
