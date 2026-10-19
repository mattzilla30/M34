#!/usr/bin/env python3
import os, pathlib, subprocess
root=pathlib.Path(__file__).resolve().parent.parent
out=root/'app/src/main/jniLibs/arm64-v8a/libm34.so'
out.parent.mkdir(parents=True,exist_ok=True)
subprocess.run([os.environ.get('CC','clang'),'--target=aarch64-linux-android37','-fuse-ld=lld','-nostdlib','-shared','-fPIC','-O2','-Wl,-z,max-page-size=16384','-Wl,-soname,libm34.so',str(root/'app/src/main/cpp/architecture.c'),'-o',str(out)],check=True)
