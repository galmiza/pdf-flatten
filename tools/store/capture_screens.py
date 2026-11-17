"""Captures the app's screens in each language, for the store listing.

Needs a running emulator (or a device on Android 13+) with the app installed and the sample
document in Downloads:

    python3 tools/make_sample_pdf.py contract.pdf && adb push contract.pdf /sdcard/Download/
    python3 tools/store/capture_screens.py build/store/raw en-US,fr-FR,es-ES,de-DE,it-IT,pt-BR,nl-NL

Then render the store images with tools/store/render_store.py.
"""
import os, re, subprocess, sys, time

PKG = "net.galmiza.android.pdfflattener"
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
LOCALES = sys.argv[2].split(",")

def adb(*args, capture=False):
    r = subprocess.run(["adb", *args], capture_output=True)
    return r.stdout if capture else None

def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml", capture=True).decode()

def find(attr, value, xml=None):
    xml = xml or dump()
    for node in re.findall(r"<node [^>]*>", xml):
        if f'{attr}="{value}"' in node:
            x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def tap(point):
    adb("shell", "input", "tap", str(point[0]), str(point[1]))

def wait_for(attr, value, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        p = find(attr, value)
        if p:
            return p
        time.sleep(0.7)
    raise RuntimeError(f"{value} not found")

def shot(name):
    time.sleep(1.5)
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", capture=True))

# Clean status bar for store screenshots (System UI demo mode)
adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
for cmd in (["enter"], ["clock", "-e", "hhmm", "1200"], ["battery", "-e", "level", "100", "-e", "plugged", "false"],
            ["network", "-e", "wifi", "show", "-e", "level", "4"], ["network", "-e", "mobile", "show", "-e", "level", "4"],
            ["notifications", "-e", "visible", "false"]):
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", *cmd)
adb("shell", "cmd", "uimode", "night", "no")
for loc in LOCALES:
    adb("shell", "cmd", "locale", "set-app-locales", PKG, "--user", "0", "--locales", loc)
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    tap(wait_for("resource-id", f"{PKG}:id/pick"))
    time.sleep(1.5)
    tap(wait_for("text", "contract.pdf"))
    wait_for("resource-id", f"{PKG}:id/result_title")
    shot(f"{loc}_2_result")
    tap(find("resource-id", f"{PKG}:id/show_original"))
    shot(f"{loc}_3_original")
    tap(find("resource-id", f"{PKG}:id/show_flattened"))
    tap(find("resource-id", f"{PKG}:id/settings"))
    wait_for("resource-id", f"{PKG}:id/categories")
    shot(f"{loc}_4_settings")
    adb("shell", "input", "keyevent", "BACK")
    time.sleep(1)
    adb("shell", "cmd", "uimode", "night", "yes")
    time.sleep(3)
    shot(f"{loc}_5_dark")
    adb("shell", "cmd", "uimode", "night", "no")
    # Start screen last: a fresh launch shows it
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    wait_for("resource-id", f"{PKG}:id/pick")
    shot(f"{loc}_1_start")
    print(loc, "done", flush=True)
adb("shell", "cmd", "locale", "set-app-locales", PKG, "--user", "0", "--locales", "")
adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "exit")
