#!/usr/bin/env python3
"""Verify delivery constraints, signatures, ABI, ELF alignment and secret hygiene."""
import argparse, os, pathlib, struct, subprocess, zipfile, hashlib
root=pathlib.Path(__file__).resolve().parent.parent
p=argparse.ArgumentParser();p.add_argument('--sdk',default=os.environ.get('ANDROID_HOME',str(pathlib.Path.home()/'.local/android-sdk')));args=p.parse_args()
tools=sorted((pathlib.Path(args.sdk)/'build-tools').iterdir(),key=lambda f:tuple(int(x) if x.isdigit() else 0 for x in f.name.split('.')))[-1]
apk=root/'dist'/('M34-'+(root/'VERSION').read_text().strip()+'-android17-arm64.apk')
with zipfile.ZipFile(apk) as z:
    names=z.namelist();native=[n for n in names if n.endswith('.so')]
    assert native==['lib/arm64-v8a/libm34.so'],native
    assert 'classes.dex' in names
    assert not any(any(s in n.lower() for s in ['keystore','signing-bundle','.jks','.env','password']) for n in names)
    elf=z.read(native[0]);assert elf[:4]==b'\x7fELF' and elf[4]==2 and struct.unpack_from('<H',elf,18)[0]==183,'Expected 64-bit AArch64 ELF'
    phoff=struct.unpack_from('<Q',elf,32)[0];phsize,phnum=struct.unpack_from('<HH',elf,54)
    for i in range(phnum):
        offset=phoff+i*phsize
        if struct.unpack_from('<I',elf,offset)[0]==1:assert struct.unpack_from('<Q',elf,offset+48)[0]>=16384,'ELF LOAD segments must support 16 KB pages'
badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
assert "versionName='"+(root/'VERSION').read_text().strip()+"'" in badging
assert "versionCode='"+(root/'VERSION_CODE').read_text().strip()+"'" in badging
assert "sdkVersion:'37'" in badging and "targetSdkVersion:'37'" in badging
assert "native-code: 'arm64-v8a'" in badging
assert 'application-debuggable' not in badging
subprocess.run([str(tools/'apksigner'),'verify','--verbose',str(apk)],check=True)
subprocess.run([str(tools/'zipalign'),'-c','-P','16','4',str(apk)],check=True)
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
(root/'dist/SHA256SUMS').write_text(digest+'  '+apk.name+'\n')
print('Verified API 37 minimum/target, ARM64-only ABI, 16 KB ELF alignment, release signature, ZIP alignment, and no bundled signing secrets.')
print('SHA256:',digest)
