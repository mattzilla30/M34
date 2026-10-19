#!/usr/bin/env python3
from pathlib import Path
import subprocess,zipfile,os
r=Path(__file__).resolve().parent.parent;sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'.local/android-sdk')));b=sorted((sdk/'build-tools').iterdir(),key=lambda p:tuple(int(n) if n.isdigit() else 0 for n in p.name.split('.')))[-1];platform=next(p/'android.jar' for p in [sdk/'platforms/android-37.0',sdk/'platforms/android-37'] if (p/'android.jar').exists());w=r/'app/build/device-qa';w.mkdir(parents=True,exist_ok=True);classes=w/'classes';classes.mkdir(exist_ok=True)
def run(*args):subprocess.run([str(a) for a in args],check=True)
run('javac','--release','17','-classpath',platform,'-d',classes,r/'tests/device/PhoneInstrumentation.java')
jar=w/'tests.jar'
with zipfile.ZipFile(jar,'w')as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
run(b/'d8','--min-api','37','--lib',platform,'--output',w,jar)
run(b/'aapt2','link','-o',w/'base.apk','-I',platform,'--manifest',r/'tests/device/AndroidManifest.xml')
with zipfile.ZipFile(w/'base.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(w/'classes.dex','classes.dex')
run(b/'zipalign','-f','4',w/'base.apk',w/'aligned.apk')
secret=r/'signing-bundle';env=os.environ.copy();env['M34_QA_STORE_PASS']=(secret/'KEYSTORE_PASSWORD.txt').read_text().strip();env['M34_QA_KEY_PASS']=(secret/'KEY_PASSWORD.txt').read_text().strip()
subprocess.run([str(b/'apksigner'),'sign','--ks',str(secret/'m34-release.jks'),'--ks-key-alias',(secret/'KEY_ALIAS.txt').read_text().strip(),'--ks-pass','env:M34_QA_STORE_PASS','--key-pass','env:M34_QA_KEY_PASS','--out',str(w/'M34-device-qa.apk'),str(w/'aligned.apk')],env=env,check=True)
print('Built temporary same-signature instrumentation APK; no secrets included.')
