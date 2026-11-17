# PDFBox-Android ships its own consumer rules (security handlers loaded by reflection).
# Optional JPEG 2000 decoder, not bundled: PDFBox falls back gracefully when it is missing.
-dontwarn com.gemalto.jp2.**
