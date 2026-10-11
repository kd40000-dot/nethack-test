#!/usr/bin/env python3
"""Keep NetHack Tutorial Mode's contextual hints within two map squares.

Applied after extracting the pinned tutorial source archive. A read-only
source assertion fails the build if the archived layout unexpectedly changes.
"""
from pathlib import Path
import sys


def replace_exact(path: Path, before: str, after: str) -> None:
    source = path.read_text(encoding="utf-8")
    if after in source:
        return  # idempotent even when replacement retains its original anchor
    if source.count(before) == 1:
        path.write_text(source.replace(before, after, 1), encoding="utf-8")
    else:
        raise RuntimeError(f"Unexpected source layout; refusing unsafe patch: {path} / {before[:72]!r}")


def main(root: Path, exported: Path) -> None:
    sources = root / "sys/android/app/src/com/tbd/NetHack5/tutorial"
    engine = sources / "TutorialEngine.java"
    probe = sources / "StateProbe.java"
    ui = sources / "TutorialOverlay.java"
    gradle = root / "sys/android/app/build.gradle"
    replace_exact(engine,
        "    private TutorialEngine() { }\n",
        "    private TutorialEngine() { }\n"
        "    // Chebyshev (king-move) distance: two tiles includes diagonal squares.\n"
        "    public static final int LOCAL_CONTEXT_RADIUS = 2;\n"
        "    public static boolean inLocalContext(int dx, int dy) {\n"
        "        return Math.max(Math.abs(dx), Math.abs(dy)) <= LOCAL_CONTEXT_RADIUS;\n"
        "    }\n")
    replace_exact(probe,
        "                for(int dy=-6;dy<=6;dy++)for(int dx=-6;dx<=6;dx++) {",
        "                for(int dy=-TutorialEngine.LOCAL_CONTEXT_RADIUS;dy<=TutorialEngine.LOCAL_CONTEXT_RADIUS;dy++)\n"
        "                    for(int dx=-TutorialEngine.LOCAL_CONTEXT_RADIUS;dx<=TutorialEngine.LOCAL_CONTEXT_RADIUS;dx++) {")
    replace_exact(engine,
        "        if (!s.visibleStairs.isEmpty() && !s.stairsUpHere && !s.stairsDownHere) {\n"
        "            Stair stair=s.visibleStairs.get(0);",
        "        if (!s.visibleStairs.isEmpty() && !s.stairsUpHere && !s.stairsDownHere) {\n"
        "            // A stale state may still contain distant landmarks.\n"
        "            Stair stair=null;\n"
        "            for(Stair candidate:s.visibleStairs)\n"
        "                if(!candidate.underfoot() && inLocalContext(candidate.dx,candidate.dy)\n"
        "                        && (stair==null || candidate.distance()<stair.distance()))\n"
        "                    stair=candidate;\n"
        "            if(stair!=null) {"
    )
    replace_exact(engine,
        '                " away. Move to it when you\'re ready; you can change levels only when standing on the stairs.","stairs",out);\n        }',
        '                " away. Move to it when you\'re ready; you can change levels only when standing on the stairs.","stairs",out);\n            }\n        }')
    replace_exact(ui,
        '        for(TutorialEngine.Stair stair:s.visibleStairs)k.append("|stairs:").append(stair.down).append(":").append(stair.dx).append(":").append(stair.dy);',
        '        for(TutorialEngine.Stair stair:s.visibleStairs)\n'
        '            if(TutorialEngine.inLocalContext(stair.dx,stair.dy))\n'
        '                k.append("|stairs:").append(stair.down).append(":").append(stair.dx).append(":").append(stair.dy);')
    replace_exact(ui,
        '        if(!s.visibleStairs.isEmpty()) {\n            TextView title=text("MAPPED STAIRCASES",10,MINT,true);',
        '        boolean hasNearbyStair=false;\n'
        '        for(TutorialEngine.Stair stair:s.visibleStairs)\n'
        '            if(!stair.underfoot() && TutorialEngine.inLocalContext(stair.dx,stair.dy)) {\n'
        '                hasNearbyStair=true;break;\n'
        '            }\n'
        '        if(hasNearbyStair) {\n'
        '            TextView title=text("NEARBY MAPPED STAIRCASES",10,MINT,true);')
    replace_exact(ui,
        '            for(TutorialEngine.Stair stair:s.visibleStairs) {\n                if(shown++>=2)break;',
        '            for(TutorialEngine.Stair stair:s.visibleStairs) {\n'
        '                if(stair.underfoot() || !TutorialEngine.inLocalContext(stair.dx,stair.dy))continue;\n'
        '                if(shown++>=2)break;')
    replace_exact(gradle,'versionCode = 50043','versionCode = 50044')
    replace_exact(gradle,'5.0.0-tutorial-audit14','5.0.0-tutorial-nearby2')
    replace_exact(exported / "tests/ContextPriorityTest.java",
        "state.visibleStairs.add(new TutorialEngine.Stair(3,2,true));",
        "state.visibleStairs.add(new TutorialEngine.Stair(2,2,true));")
    print("Applied two-tile contextual visibility limit (v50044)")


if __name__ == '__main__':
    if len(sys.argv)!=3:
        raise SystemExit("Usage: limit_nearby_context.py <upstream root> <exported tutorial source>")
    main(Path(sys.argv[1]),Path(sys.argv[2]))
