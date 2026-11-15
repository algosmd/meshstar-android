"""Package LGPL sources and build instructions for offline export in the app."""
from pathlib import Path
import zipfile
root=Path(__file__).resolve().parent
out=root.parent/'app/src/main/assets'
out.mkdir(parents=True,exist_ok=True)
files=[root/'upstream/codec2-1.2.0.tar.gz',root/'upstream/COPYING',root/'upstream/KISS-FFT-COPYING',
       root/'README.md',root/'prepare.py',root/'build.gradle.kts',root/'consumer-rules.pro',
       root/'src/main/cpp/CMakeLists.txt',root/'src/main/cpp/bridge.c',root/'src/main/java/net/meshstar/codec2/Codec2.java']
with zipfile.ZipFile(out/'codec2-source.zip','w',zipfile.ZIP_DEFLATED) as z:
    for file in files:
        info=zipfile.ZipInfo(str(file.relative_to(root.parent)),date_time=(2026,1,1,0,0,0))
        info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16
        z.writestr(info,file.read_bytes())
(out/'codec2-licenses.txt').write_text('Codec2 1.2.0 — LGPL-2.1\n'+(root/'upstream/COPYING').read_text()+'\nKISS FFT\n'+(root/'upstream/KISS-FFT-COPYING').read_text())
