"""Standalone deterministic JVM regressions. Requires ANDROID_STUDIO_HOME or --studio.
Synthetic fixtures are temporary; no backend or private patient data is used.
"""
from pathlib import Path
import argparse, os, subprocess, tempfile, re, zipfile, hashlib
ROOT=Path(__file__).resolve().parents[2]
# Reviewed cartoon contact fixtures introduced by 29a6a6d; exact hashes only.
LEGACY_CONTACT_FIXTURES={'NovaRehab/icons/patient/contacts/dusan.jpg': '29148d109363c55f4bdb24b7d34506579fc5127f8e321f70ff909267728ebb3f', 'NovaRehab/icons/patient/contacts/franc.jpg': '399b46fad710a513c245baec0b64a0b21a21d7dd1cae9e63f68fb3b6c2b418da', 'NovaRehab/icons/patient/contacts/inna.jpg': '28306158e229f3382c216a0c3f07a6e155bb840ce55f0a4c096a1ab2f0d1f853', 'NovaRehab/icons/patient/contacts/julija.jpg': 'e4fc31dcfb60c3fa3d55df2264b67b4d69c571ee0db7a444d7fd5cc52e2a8f47', 'NovaRehab/icons/patient/contacts/oksana.jpg': 'b57c9691881ce354275a7d4be242ed62feddfc6c3e2b15688253a4ccaabccfa8', 'NovaRehab/icons/patient/contacts/sergej.jpg': '57760d59bd32c94f723be2ea9a65d2ec8097e63d0871b09805921d81b2145be0', 'NovaRehab/icons/patient/contacts/zana.jpg': '8c690b8be2263ad7fc18acb8dedfdb092f32f2f4f3776eefc77f4c65b228a7ae'}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--studio',default=os.environ.get('ANDROID_STUDIO_HOME'))
    parser.add_argument('--apk',type=Path)
    parser.add_argument('--privacy-only',action='store_true')
    args=parser.parse_args()
    if not args.studio: parser.error('Set ANDROID_STUDIO_HOME or --studio')
    studio=Path(args.studio); kotlin=studio/'plugins/Kotlin/kotlinc/lib'
    java=studio/'jbr/bin'/('java.exe' if os.name=='nt' else 'java')
    cp=os.pathsep.join([str(studio/'plugins/design-tools/lib/layoutlib.jar'),str(kotlin/'kotlin-stdlib.jar')])
    src=ROOT/'app/src/main/java/com/rehab2'
    names=['AacItem','AacV2Models','AacFixedTopRow','AacStarterContentV1','AacStoragePaths',
        'AacGenderResolvers','AacLocalizedTextResolver','AacLocalizedText','AacQuestionResolver','AacSpeechRole',
        'AacImageResolver','AacLanguageResolver','PatientProfileSettings','AacV2JsonParser','AacV2PageAdapter','AacV2Page','AacPage']
    sources=[src/'aac'/(n+'.kt') for n in names]
    sources += [src/'patient'/(n+'.kt') for n in ['PatientProfile','PatientProfileCatalog','PatientIconCatalog','PatientIconGenerationRequest','PatientIconGenerationTransport']]
    if not args.privacy_only:
        with tempfile.TemporaryDirectory(prefix='aac802_') as tmp:
            tmp=Path(tmp)
            # Compile the actual Activity merge method, without Android UI scaffolding.
            activity=(src/'MainActivity.kt').read_text(encoding='utf-8')
            start=activity.index('    private fun mergeMainAacFallbackItem(')
            opening=activity.index('{',start); depth=1;end=opening+1
            while depth:
                depth += (activity[end]=='{')-(activity[end]=='}');end+=1
            method=activity[start:end].replace('private fun','fun',1)
            merge=tmp/'MainMerge802.kt'
            merge.write_text('package com.rehab2.aac\nobject MainMerge802 {\nconst val CORE_V2_HOME_PAGE_ID="home"\nval CORE_V2_HOME_PAGE_POSITIONS=emptyMap<String,Int>()\n'+method+'\n}',encoding='utf-8')
            sources.append(merge)
            log=tmp/'Log.kt'
            log.write_text('package android.util\nobject Log {\n'+'\n'.join('@JvmStatic @JvmOverloads fun '+level+'(tag:String?,message:String?,error:Throwable?=null):Int=0' for level in ['v','d','i','w','e'])+'\n}',encoding='utf-8')
            subprocess.run([str(java),'-cp',str(kotlin/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(tmp/'classes'),*[str(p) for p in sources],str(log),str(ROOT/'NovaRehab/tools/tests/Patient802Tests.kt')],check=True)
            subprocess.run([str(java),'-Dfile.encoding=UTF-8','-cp',str(tmp/'classes')+os.pathsep+cp,'com.rehab2.aac.Patient802TestsKt',str(tmp/'fixtures')],check=True)
    assert not (src/'remote').exists() and not (ROOT/'firebase').exists()
    assert not (src/'RemotePatientsActivity.kt').exists()
    private_name=re.compile(r'(?i)(patient_reference|patient_[0-9a-f-]{36}\.(png|jpg|webp)|icons/patient/.+\.(png|jpg|webp))')
    secret=re.compile(rb'(?:sk-(?:proj-)?[A-Za-z0-9_-]{32,}|AIza[0-9A-Za-z_-]{35}|-----BEGIN (?:RSA |EC )?PRIVATE KEY-----)')
    for folder in [ROOT/'app/src/main/assets',ROOT/'app/src/main/res']:
        for file in folder.rglob('*'):
            if file.is_file(): assert not private_name.search(file.as_posix()),file
    for file in (ROOT/'app/src/main').rglob('*'):
        if file.is_file() and file.suffix in {'.kt','.java','.json','.xml'}: assert not secret.search(file.read_bytes()),file
    tracked=subprocess.check_output(['git','ls-files'],cwd=ROOT,text=True).splitlines()
    for path in tracked:
        if private_name.search(path):
            assert path in LEGACY_CONTACT_FIXTURES,path
            expected=LEGACY_CONTACT_FIXTURES[path]
            assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==expected,path
            assert hashlib.sha256(subprocess.check_output(['git','show',':'+path],cwd=ROOT)).hexdigest()==expected,path
    if args.apk:
        with zipfile.ZipFile(args.apk) as apk:
            for name in apk.namelist():
                assert not private_name.search(name),name
                assert hashlib.sha256(apk.read(name)).hexdigest() not in LEGACY_CONTACT_FIXTURES.values(),name
                assert not secret.search(apk.read(name)),name
    else: print('APK privacy pending: pass --apk after build',flush=True)
    print('O PASS reference isolation from source/assets/Git'+('/APK' if args.apk else ''),flush=True)
    print('P PASS no generation secret in source'+('/APK' if args.apk else ''),flush=True)
    print(('802 PRIVACY PASS' if args.privacy_only else '802 PASS')+'; actual installed voices, device lifecycle and remote backend are not simulated.',flush=True)
if __name__=='__main__': main()
