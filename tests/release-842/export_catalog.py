from pathlib import Path
import os, subprocess
r=Path(__file__).resolve().parents[2]
cache=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'
stdlib=next((cache/'org.jetbrains.kotlin/kotlin-stdlib/1.9.24').rglob('*.jar'))
cp=os.pathsep.join(map(str,[r/'app/build/tmp/kotlin-classes/debug',r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',stdlib]))
out=r/'build/catalog-review'
out.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-classpath',cp,'-d',str(out),str(r/'tests/release-842/ExportCatalog.java')],check=True)
subprocess.run(['java','-cp',str(out)+os.pathsep+cp,'ExportCatalog',str(r/'docs/speech-review-842.tsv')],check=True)
