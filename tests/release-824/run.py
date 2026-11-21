"""Run actual compiled Android-independent catalog code after assembleDebug."""
from pathlib import Path
import os, subprocess, tempfile
R=Path(__file__).resolve().parents[2]
cache=Path(os.environ.get('USERPROFILE',str(Path.home())))/'.gradle/caches/modules-2/files-2.1'
jars=[]
for a in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']:
    jars+=list((cache/'org.jetbrains.kotlin'/a/'1.9.24').rglob('*.jar'))
jars+=list((cache/'org.jetbrains.intellij.deps/trove4j').rglob('*.jar'))+list((cache/'org.jetbrains/annotations').rglob('*.jar'))
stdlib=next(p for p in jars if p.name.startswith('kotlin-stdlib-'))
classes=R/'app/build/tmp/kotlin-classes/debug'
java=R/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
out=Path(tempfile.mkdtemp(prefix='novarehab-824-'))
cp=os.pathsep.join(map(str,[classes,java,stdlib]))
subprocess.run(['java','-cp',os.pathsep.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(out),str(R/'tests/release-824/HomeLayoutTest.kt')],check=True)
subprocess.run(['java','-cp',str(out)+os.pathsep+cp,'HomeLayoutTestKt',str(R/'app/src/main/assets/NovaRehab/icons')],check=True)
