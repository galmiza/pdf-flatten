#!/usr/bin/env python3
"""Renders the Google Play graphics into fastlane/metadata/android/<locale>/images:

- icon.png (512 × 512, en-US only: Play uses one icon for every language)
- featureGraphic.jpg (1024 × 500)
- phoneScreenshots/1.jpg … 5.jpg (1080 × 1920: Play rejects a long side over twice the short one,
  so the 1080 × 2400 captures are framed with a caption instead of uploaded as is)

Input: the captures of capture_screens.py. Rendering uses headless Google Chrome, and macOS's
`sips` for the JPEG conversion (photos of UI compress 3× better than PNG, with no visible loss).

    python3 tools/store/render_store.py build/store/raw
"""
import html
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAW = os.path.abspath(sys.argv[1])
METADATA = os.path.join(ROOT, "fastlane", "metadata", "android")
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")

# Store order: the result first (the value), then the problem, then the rest
SCREENS = ["2_result", "3_original", "1_start", "4_settings", "5_dark"]

CAPTIONS = {
    "en-US": ["Signatures and form fields become part of the page", "Without it, many viewers hide them",
              "One tap. 100% offline.", "Choose what to merge", "No account, no ads, no tracking"],
    "fr-FR": ["Signatures et champs intégrés à la page", "Sans cela, beaucoup de lecteurs les masquent",
              "Un geste. 100 % hors ligne.", "Choisissez ce qui est intégré", "Sans compte, sans pub, sans pistage"],
    "es-ES": ["Firmas y campos integrados en la página", "Sin esto, muchos visores los ocultan",
              "Un toque. 100 % sin conexión.", "Elige qué integrar", "Sin cuenta, sin anuncios, sin rastreo"],
    "de-DE": ["Unterschriften und Felder fest in der Seite", "Sonst blenden viele Viewer sie aus",
              "Ein Tipp. 100 % offline.", "Wähle, was übernommen wird", "Kein Konto, keine Werbung, kein Tracking"],
    "it-IT": ["Firme e campi integrati nella pagina", "Senza, molti visualizzatori li nascondono",
              "Un tocco. 100% offline.", "Scegli cosa integrare", "Niente account, pubblicità o tracciamento"],
    "pt-BR": ["Assinaturas e campos integrados à página", "Sem isso, muitos leitores os escondem",
              "Um toque. 100% offline.", "Escolha o que integrar", "Sem conta, sem anúncios, sem rastreamento"],
    "nl-NL": ["Handtekeningen en velden vast in de pagina", "Zonder dit verbergen veel viewers ze",
              "Eén tik. 100% offline.", "Kies wat je samenvoegt", "Geen account, geen advertenties, geen tracking"],
}

TAGLINES = {
    "en-US": "Make signatures and form fields visible in every PDF viewer",
    "fr-FR": "Rendez signatures et champs visibles dans tous les lecteurs PDF",
    "es-ES": "Haz visibles firmas y campos en cualquier visor de PDF",
    "de-DE": "Unterschriften und Formularfelder in jedem PDF-Viewer sichtbar",
    "it-IT": "Firme e campi visibili in qualsiasi visualizzatore PDF",
    "pt-BR": "Assinaturas e campos visíveis em qualquer leitor de PDF",
    "nl-NL": "Handtekeningen en formuliervelden zichtbaar in elke pdf-viewer",
}

FONTS = ('<link rel="stylesheet" href="https://fonts.googleapis.com/css2?'
         'family=Roboto:wght@400;500;700&display=block">')
BASE_CSS = """
  * { box-sizing: border-box; }
  html, body { margin: 0; }
  body { font-family: Roboto, "Helvetica Neue", Arial, sans-serif; -webkit-font-smoothing: antialiased; }
"""

# The launcher artwork (res/drawable/ic_launcher_foreground.xml), as SVG
ICON_ART = """
  <path fill="#FFFFFF" d="M36,30h26l12,12v36a2,2 0 0 1 -2,2h-36a2,2 0 0 1 -2,-2v-46a2,2 0 0 1 2,-2z"/>
  <path fill="#B9CCEA" d="M62,30v10a2,2 0 0 0 2,2h10z"/>
  <path fill="#B9CCEA" d="M40,46h16v3h-16zM40,53h26v3h-26zM40,60h22v3h-22z"/>
  <path fill="none" stroke="#1E5BB8" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"
        d="M40,73c3,-6 5,-6 6,-1s3,4 5,-1 3,-3 4,0 4,2 10,-1"/>
"""


def icon_svg(size, rounded):
    radius = 'rx="22"' if rounded else ""
    return (f'<svg width="{size}" height="{size}" viewBox="9 10 90 90" xmlns="http://www.w3.org/2000/svg">'
            f'<rect x="9" y="10" width="90" height="90" {radius} fill="#1E5BB8"/>{ICON_ART}</svg>')


def render(markup, width, height, out):
    """Renders [markup] to [out]: PNG, or JPEG when [out] ends with .jpg"""
    os.makedirs(os.path.dirname(out), exist_ok=True)
    png = out[:-4] + ".png" if out.endswith(".jpg") else out
    with tempfile.NamedTemporaryFile("w", suffix=".html", dir=RAW, delete=False) as page:
        page.write(f"<!doctype html><meta charset='utf-8'>{FONTS}<style>{BASE_CSS}</style>{markup}")
    try:
        subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                        "--force-device-scale-factor=1", "--virtual-time-budget=8000",
                        f"--window-size={width},{height}", f"--screenshot={png}", "file://" + page.name],
                       check=True, capture_output=True)
        if png != out:
            subprocess.run(["sips", "-s", "format", "jpeg", "-s", "formatOptions", "92", png, "--out", out],
                           check=True, capture_output=True)
            os.unlink(png)
    finally:
        os.unlink(page.name)


def screenshot(capture, caption):
    return f"""
<style>
  body {{ width: 1080px; height: 1920px; overflow: hidden;
         background: linear-gradient(160deg, #2A6AD0 0%, #1E5BB8 45%, #0F3A80 100%); }}
  h1 {{ position: absolute; top: 96px; left: 80px; right: 80px; margin: 0; color: #fff;
        font-size: 64px; line-height: 1.15; font-weight: 700; text-align: center; letter-spacing: -0.5px;
        text-wrap: balance; }}
  img {{ position: absolute; left: 190px; top: 380px; width: 700px; height: 1556px;
         border-radius: 56px; box-shadow: 0 30px 80px rgba(0, 0, 0, .35); }}
</style>
<h1>{html.escape(caption)}</h1>
<img src="file://{capture}">
"""


def feature_graphic(capture, tagline):
    return f"""
<style>
  body {{ width: 1024px; height: 500px; overflow: hidden; position: relative;
         background: linear-gradient(135deg, #2A6AD0 0%, #1E5BB8 50%, #0F3A80 100%); color: #fff; }}
  .text {{ position: absolute; left: 72px; top: 0; bottom: 0; width: 560px;
           display: flex; flex-direction: column; justify-content: center; gap: 22px; }}
  .name {{ display: flex; align-items: center; gap: 22px; font-size: 52px; font-weight: 700; }}
  .name svg {{ border-radius: 22px; box-shadow: 0 8px 24px rgba(0,0,0,.25); }}
  p {{ margin: 0; font-size: 32px; line-height: 1.3; font-weight: 400; opacity: .95; }}
  img {{ position: absolute; right: 70px; top: 56px; width: 300px; border-radius: 28px;
         box-shadow: 0 20px 60px rgba(0,0,0,.4); }}
</style>
<div class="text">
  <div class="name">{icon_svg(96, True)}<span>PDF Flattener</span></div>
  <p>{html.escape(tagline)}</p>
</div>
<img src="file://{capture}">
"""


def main():
    render(f'<div style="width:512px;height:512px">{icon_svg(512, False)}</div>', 512, 512,
           os.path.join(METADATA, "en-US", "images", "icon.png"))
    for locale, captions in CAPTIONS.items():
        images = os.path.join(METADATA, locale, "images")
        for index, (screen, caption) in enumerate(zip(SCREENS, captions), start=1):
            capture = os.path.join(RAW, f"{locale}_{screen}.png")
            render(screenshot(capture, caption), 1080, 1920, os.path.join(images, "phoneScreenshots", f"{index}.jpg"))
        capture = os.path.join(RAW, f"{locale}_2_result.png")
        render(feature_graphic(capture, TAGLINES[locale]), 1024, 500, os.path.join(images, "featureGraphic.jpg"))
        print(locale, "rendered", flush=True)


if __name__ == "__main__":
    main()
