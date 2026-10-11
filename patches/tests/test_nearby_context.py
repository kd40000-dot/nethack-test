#!/usr/bin/env python3
"""Source scanner/display coverage; runtime semantics are checked by NearbyContextTest.java."""
from pathlib import Path
import sys


def main(upstream: Path) -> None:
    src = upstream / 'sys/android/app/src/com/tbd/NetHack5/tutorial'
    probe = (src / 'StateProbe.java').read_text()
    engine = (src / 'TutorialEngine.java').read_text()
    ui = (src / 'TutorialOverlay.java').read_text()
    gradle = (upstream / 'sys/android/app/build.gradle').read_text()
    assert 'LOCAL_CONTEXT_RADIUS = 2' in engine
    assert 'dy=-TutorialEngine.LOCAL_CONTEXT_RADIUS;dy<=TutorialEngine.LOCAL_CONTEXT_RADIUS' in probe
    assert 'dx=-TutorialEngine.LOCAL_CONTEXT_RADIUS;dx<=TutorialEngine.LOCAL_CONTEXT_RADIUS' in probe
    assert 'for(int dy=-6;dy<=6' not in probe
    assert 'inLocalContext(candidate.dx,candidate.dy)' in engine
    assert 'if(hasNearbyStair)' in ui
    assert 'if(stair.underfoot() || !TutorialEngine.inLocalContext(stair.dx,stair.dy))continue' in ui
    assert 'NEARBY MAPPED STAIRCASES' in ui
    assert 'versionCode = 50044' in gradle
    print('PASS: nearby context source, hidden distant stairs and version guard')


if __name__ == '__main__':
    main(Path(sys.argv[1]))
