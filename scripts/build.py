#!/usr/bin/env python3
"""Build with the Android SDK and JDK, without Maven or Gradle downloads."""
import argparse, base64, os, pathlib, shutil, subprocess, tempfile, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
VERSION = (ROOT/"VERSION").read_text().strip()
VERSION_CODE = (ROOT/"VERSION_CODE").read_text().strip()
if not VERSION.isdecimal() or not VERSION_CODE.isdecimal():
    raise SystemExit("VERSION and VERSION_CODE must use plain integer numbering.")

def run(*args):
    subprocess.run([str(a) for a in args], check=True)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME', str(pathlib.Path.home()/'.local/android-sdk')))
    parser.add_argument('--signing-dir', type=pathlib.Path, default=ROOT/'signing-bundle')
    parser.add_argument('--debug', action='store_true')
    args = parser.parse_args()
    sdk = pathlib.Path(args.sdk)
    platform = next((p for p in [sdk/'platforms/android-37.0',sdk/'platforms/android-37'] if (p/'android.jar').exists()), None)
    if platform is None:
        raise SystemExit('Install Android 17 / API 37 SDK platform first.')
    candidates = sorted((sdk/'build-tools').iterdir(), key=lambda p:tuple(int(x) if x.isdigit() else 0 for x in p.name.split('.')))
    tools = candidates[-1]
    out = ROOT/'app/build'
    work = out/'intermediates/manual'
    work.mkdir(parents=True,exist_ok=True)
    classes = work/'classes'
    if classes.exists(): shutil.rmtree(classes)
    classes.mkdir()
    native = ROOT/'app/src/main/jniLibs/arm64-v8a/libm34.so'
    run(os.environ.get('CC','clang'),'--target=aarch64-linux-android37','-fuse-ld=lld','-nostdlib','-shared','-fPIC','-O2','-Wl,-z,max-page-size=16384','-Wl,-soname,libm34.so',ROOT/'app/src/main/cpp/architecture.c','-o',native)
    run(tools/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',work/'resources.zip')
    manifest = ROOT/'app/src/main/AndroidManifest.xml'
    if args.debug:
        debug_manifest=work/'AndroidManifest.xml'
        debug_manifest.write_text(manifest.read_text().replace('<application ', '<application android:debuggable="true" '))
        manifest=debug_manifest
    run(tools/'aapt2','link','-o',work/'base.apk','-I',platform/'android.jar','--manifest',manifest,'--min-sdk-version','37','--target-sdk-version','37','--version-code',VERSION_CODE,'--version-name',VERSION,work/'resources.zip')
    sources = list((ROOT/'app/src/main/java').rglob('*.java'))
    run('javac','--release','17','-encoding','UTF-8','-classpath',platform/'android.jar','-d',classes,*sources)
    jar = work/'classes.jar'
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        for p in classes.rglob('*.class'): z.write(p,p.relative_to(classes))
    dex=work/'dex';dex.mkdir(exist_ok=True)
    run(tools/'d8','--release','--min-api','37','--lib',platform/'android.jar','--output',dex,jar)
    unsigned=work/'unsigned.apk'
    shutil.copy2(work/'base.apk',unsigned)
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
        for p in dex.glob('*.dex'):z.write(p,p.name)
        z.write(native,'lib/arm64-v8a/libm34.so')
    aligned=work/'aligned.apk'
    run(tools/'zipalign','-f','-P','16','4',unsigned,aligned)
    output=ROOT/'dist';output.mkdir(exist_ok=True)
    apk=output/('M34-android17-arm64-debug.apk' if args.debug else f'M34-{VERSION}-android17-arm64.apk')
    if args.debug:
        key=work/'debug.jks'
        if not key.exists():run('keytool','-genkeypair','-noprompt','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Android Debug')
        storepass=keypass='android';alias='androiddebugkey'
    else:
        secret=args.signing_dir
        key=secret/'m34-release.jks'
        if not key.exists():
            # CI passes the same four names as repository Actions secrets.
            required=['KEYSTORE_BASE64','KEYSTORE_PASSWORD','KEY_ALIAS','KEY_PASSWORD']
            if not all(os.environ.get(k) for k in required):raise SystemExit('Release signing requires signing-bundle/ or all four KEYSTORE/KEY environment variables.')
            key=work/'ci-release.jks';key.write_bytes(base64.b64decode(os.environ['KEYSTORE_BASE64']));os.chmod(key,0o600)
            storepass=os.environ['KEYSTORE_PASSWORD'];keypass=os.environ['KEY_PASSWORD'];alias=os.environ['KEY_ALIAS']
        else:
            storepass=(secret/'KEYSTORE_PASSWORD.txt').read_text().strip();keypass=(secret/'KEY_PASSWORD.txt').read_text().strip();alias=(secret/'KEY_ALIAS.txt').read_text().strip()
    env=os.environ.copy();env['M34_STORE_PASS']=storepass;env['M34_KEY_PASS']=keypass
    try:
        subprocess.run([str(tools/'apksigner'),'sign','--ks',str(key),'--ks-key-alias',alias,'--ks-pass','env:M34_STORE_PASS','--key-pass','env:M34_KEY_PASS','--out',str(apk),str(aligned)],env=env,check=True)
    finally:
        if key==work/'ci-release.jks': key.unlink(missing_ok=True)
    run(tools/'apksigner','verify','--verbose',apk)
    run(tools/'zipalign','-c','-P','16','4',apk)
    print(f'Built {apk}')

if __name__=='__main__': main()
