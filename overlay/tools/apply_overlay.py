#!/usr/bin/env python3
"""Apply the read-only NetHack AI guide to a pinned, upstream checkout.

Usage: python3 overlay/tools/apply_overlay.py upstream
The original NetHack source stays in the upstream directory and is not
committed to the guide repository.
"""
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET


def apply(upstream: Path) -> None:
    project = Path(__file__).resolve().parents[2]
    app = upstream / "sys/android/app"
    manifest_path = app / "AndroidManifest.xml"
    gradle_path = app / "build.gradle"
    if not manifest_path.is_file() or not gradle_path.is_file():
        raise RuntimeError("Not a JodiJodington NetHack-Android checkout: " + str(upstream))

    android_ns = "http://schemas.android.com/apk/res/android"
    ET.register_namespace("android", android_ns)
    document = ET.parse(manifest_path)
    root = document.getroot()
    application = root.find("application")
    if application is None:
        raise RuntimeError("Missing Android application manifest")
    changed = False
    for activity in application.findall("activity"):
        name = activity.get(f"{{{android_ns}}}name")
        if name == "com.tbd.forkfront.ForkFront":
            activity.set(f"{{{android_ns}}}name", "com.tbd.NetHack5.ai.GuideActivity")
            changed = True
    if not changed:
        raise RuntimeError("Cannot find ForkFront launcher: upstream structure changed")
    application.set(f"{{{android_ns}}}label", "NetHack 5 Guide")
    document.write(manifest_path, encoding="utf-8", xml_declaration=True)

    gradle = gradle_path.read_text()
    if "java.srcDirs(['src'])" not in gradle:
        needle = "manifest.srcFile('AndroidManifest.xml')"
        if needle not in gradle:
            raise RuntimeError("Gradle sourceSet layout changed upstream")
        gradle = gradle.replace(needle, "java.srcDirs(['src'])\n      " + needle, 1)
    if 'applicationId = "com.tbd.nethack5.guide"' not in gradle:
        needle = "defaultConfig {"
        if needle not in gradle:
            raise RuntimeError("Missing Gradle defaultConfig")
        gradle = gradle.replace(
            needle, needle + '\n    applicationId = "com.tbd.nethack5.guide"', 1
        )
    # The encrypted-key implementation relies on Android Keystore's API 23.
    if "minSdkVersion = 7" not in gradle and "minSdkVersion = 23" not in gradle:
        raise RuntimeError("Unexpected upstream minSdkVersion")
    gradle = gradle.replace("minSdkVersion = 7", "minSdkVersion = 23", 1)
    # Make versionCode strictly larger than the original preview (5000).
    # Important: matching package name + higher versionCode is NOT enough;
    # the signing certificate must also match or the user must migrate data.
    if "versionCode = 5000" not in gradle:
        raise RuntimeError("Unexpected upstream versionCode")
    gradle = gradle.replace("versionCode = 5000", "versionCode = 5002", 1)
    gradle = gradle.replace("versionName = '5.0.0'",
                            "versionName = '5.0.0-guide-hud1'", 1)
    gradle_path.write_text(gradle)

    sources = project / "overlay/src"
    installed = app / "src"
    for source in sources.rglob("*.java"):
        target = installed / source.relative_to(sources)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)

    # Application overlays replace matching library resource names at merge time.
    for source in (project / "overlay/res").rglob("*.xml"):
        dest = app / "res" / source.relative_to(project / "overlay/res")
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, dest)

    assert (installed / "com/tbd/NetHack5/ai/GuideActivity.java").is_file()
    assert "com.tbd.NetHack5.ai.GuideActivity" in manifest_path.read_text()
    print("Guide overlay applied successfully")
    print("Application ID: com.tbd.nethack5.guide")
    print("OpenAI API key: not included; entered/encrypted on device")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply_overlay.py /path/to/upstream")
    apply(Path(sys.argv[1]).resolve())
