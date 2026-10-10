#!/usr/bin/env python3
"""Repair image lookups for Tutorial Mode's Android application ID.

Run AFTER tools/apply_to_upstream.py. ForkFront's Tileset renderer uses
R.string.namespace as Resources.getIdentifier's package hint, but the
tutorial's installed applicationId differs from NetHack's original name.
"""
from pathlib import Path
import sys

PKG = "com.tbd.nethack5.guide"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if text.count(old) != 1:
        raise RuntimeError(f"Expected exactly one source reference in {path}: {old!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch(upstream: Path) -> None:
    app = upstream / "sys/android/app"
    cfg = app / "res/values/config.xml"
    gradle = app / "build.gradle"
    tutorial = app / "src/com/tbd/NetHack5/tutorial/TutorialOverlay.java"
    for p in (cfg, gradle, tutorial):
        if not p.is_file():
            raise RuntimeError(f"Required source file missing: {p}")

    # Resource table belongs to the installed app, not the Java R namespace.
    # This repairs the 3 built-in PNG tilesheets and the overlays image.
    replace_once(cfg, '<string name="namespace">com.tbd.NetHack5</string>',
                      f'<string name="namespace">{PKG}</string>')
    replace_once(gradle, "versionCode = 50037", "versionCode = 50038")
    replace_once(gradle, "versionName = '5.0.0-tutorial-ui8'",
                         "versionName = '5.0.0-tutorial-tiles9'")

    anchor = '                boolean saved=prefs.edit().putString("tileset",value)'
    replacement = '''                // Ensure sheet and overlay resolve before saving the choice.
                if(!value.equals("TTY")){
                    int sheet=activity.getResources().getIdentifier(value,"drawable",activity.getPackageName());
                    int marks=activity.getResources().getIdentifier("overlays","drawable",activity.getPackageName());
                    if(sheet==0 || marks==0){
                        Toast.makeText(activity,"Tileset image unavailable in this APK",Toast.LENGTH_LONG).show();
                        return;
                    }
                }
''' + anchor
    replace_once(tutorial, anchor, replacement)

    for name in ("default_16x16", "geoduck_15x25", "nevanda_32x32", "overlays"):
        image = app / "res/drawable-nodpi" / f"{name}.png"
        if not image.is_file() or image.stat().st_size < 1000:
            raise RuntimeError(f"Bundled tilesheet resource missing: {image}")

    assert f'<string name="namespace">{PKG}</string>' in cfg.read_text()
    assert 'getIdentifier(value,"drawable",activity.getPackageName())' in tutorial.read_text()
    print("Verified built-in tilesheets and installed app resource namespace:", PKG)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 patches/fix_runtime_tilesets.py /path/to/NetHack-Android")
    patch(Path(sys.argv[1]).resolve())
