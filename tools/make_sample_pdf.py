#!/usr/bin/env python3
"""Writes a small PDF with "floating" layers, to try the app by hand: an ink signature (with its
appearance stream and popup), a free-text note without appearance stream, a filled form field
that leaves its look to the viewer (NeedAppearances), and a square annotation on page 2.

Usage: python3 tools/make_sample_pdf.py [output.pdf]    (no dependency besides Python 3)
"""
import sys
import zlib


def stream(content, extra=b""):
    data = zlib.compress(content)
    return b"<< /Length %d /Filter /FlateDecode %s >>\nstream\n%s\nendstream" % (len(data), extra, data)


signature = (b"0 0 0.6 RG 2 w 1 J 1 j "
             b"5 30 m 20 70 35 70 40 35 c 45 5 55 5 65 40 c 72 62 80 62 88 30 c "
             b"95 10 105 20 120 45 c 135 65 150 40 165 35 c 180 30 190 38 195 42 c S")
page1 = (b"BT /F1 24 Tf 72 770 Td (Lease agreement) Tj ET "
         b"BT /F1 12 Tf 72 740 Td (The undersigned parties have agreed as follows.) Tj ET "
         b"BT /F1 12 Tf 72 720 Td (Tenant name:) Tj ET "
         b"BT /F1 12 Tf 72 690 Td (Signature:) Tj ET 0.5 w 72 598 m 290 598 l S")
page2 = b"BT /F1 18 Tf 72 770 Td (Appendix) Tj ET BT /F1 12 Tf 72 740 Td (Inventory of fixtures.) Tj ET"

objects = {
    1: b"<< /Type /Catalog /Pages 2 0 R /AcroForm 12 0 R >>",
    2: b"<< /Type /Pages /Kids [3 0 R 13 0 R] /Count 2 >>",
    3: b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R "
       b"/Resources << /Font << /F1 5 0 R >> >> /Annots [6 0 R 8 0 R 9 0 R 11 0 R] >>",
    4: stream(page1),
    5: b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
    # Ink signature, as drawn with a finger in a PDF viewer
    6: b"<< /Type /Annot /Subtype /Ink /Rect [72 600 272 680] /F 4 /C [0 0 0.6] /Border [0 0 2] "
       b"/InkList [[77 630 112 635 137 640 192 640 267 642]] /Popup 8 0 R /AP << /N 7 0 R >> >>",
    7: stream(signature, b"/Type /XObject /Subtype /Form /BBox [0 0 200 80]"),
    8: b"<< /Type /Annot /Subtype /Popup /Parent 6 0 R /Rect [300 600 500 700] /F 28 >>",
    # Free text note with no appearance stream: the app has to build one
    9: b"<< /Type /Annot /Subtype /FreeText /Rect [300 610 520 640] /F 4 /Contents (Read and approved) "
       b"/DA (/Helv 14 Tf 0.8 0 0 rg) >>",
    # Filled text field whose appearance is left to the viewer
    11: b"<< /Type /Annot /Subtype /Widget /FT /Tx /T (tenant) /V (Camille Martin) /F 4 "
        b"/Rect [180 715 420 733] /DA (/Helv 12 Tf 0 g) /P 3 0 R >>",
    12: b"<< /Fields [11 0 R] /NeedAppearances true /DA (/Helv 0 Tf 0 g) /DR << /Font << /Helv 5 0 R >> >> >>",
    13: b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 14 0 R "
        b"/Resources << /Font << /F1 5 0 R >> >> /Annots [15 0 R] >>",
    14: stream(page2),
    # Square drawn around a paragraph, again without appearance stream
    15: b"<< /Type /Annot /Subtype /Square /Rect [66 730 220 760] /F 4 /C [0.9 0.1 0.1] /Border [0 0 2] >>",
}

out = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
offsets = {}
for number in sorted(objects):
    offsets[number] = len(out)
    out += b"%d 0 obj\n%s\nendobj\n" % (number, objects[number])
size = max(objects) + 1
xref = len(out)
out += b"xref\n0 %d\n0000000000 65535 f \n" % size
for number in range(1, size):
    out += b"%010d 00000 n \n" % offsets[number] if number in offsets else b"0000000000 65535 f \n"
out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (size, xref)

path = sys.argv[1] if len(sys.argv) > 1 else "contract.pdf"
with open(path, "wb") as f:
    f.write(out)
print(f"{path}: {len(out)} bytes")
