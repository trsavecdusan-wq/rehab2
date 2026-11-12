"""Execute the real recipient store with a file-backed Context test double.
This tests storage keys/three slots/clearing, not Android UI or mail delivery."""
from pathlib import Path
import os, subprocess, tempfile
R=Path(__file__).resolve().parents[2]
cache=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'
jars=[]
for a in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']:
    jars+=list((cache/'org.jetbrains.kotlin'/a/'1.9.24').rglob('*.jar'))
jars+=list((cache/'org.jetbrains.intellij.deps/trove4j').rglob('*.jar'))+list((cache/'org.jetbrains/annotations').rglob('*.jar'))
stdlib=next(p for p in jars if p.name.startswith('kotlin-stdlib-'))
out=Path(tempfile.mkdtemp(prefix='novarehab-email-820-'))
stub=out/'Context.kt'
stub.write_text("""
package android.content
import java.io.File
import java.util.Properties
class Context(val directory: File) {
 companion object { const val MODE_PRIVATE=0 }
 fun getSharedPreferences(name:String,mode:Int)=Preferences(File(directory,name))
}
class Preferences(val file:File) {
 private val values=Properties().apply { if(file.exists()) file.inputStream().use { load(it) } }
 fun getString(key:String,fallback:String)=values.getProperty(key,fallback)
 fun edit()=Editor()
 inner class Editor {
  fun putString(key:String,value:String):Editor {values.setProperty(key,value);return this}
  fun remove(key:String):Editor {values.remove(key);return this}
  fun apply(){file.outputStream().use { values.store(it,"test double") }}
 }
}
""",encoding='utf-8')
test=out/'Recipients.kt'
test.write_text("""
import android.content.Context
import com.rehab2.aac.AacEmailRecipients
import java.io.File
fun main(args:Array<String>) {
 val directory=File(args[0])
 check(AacEmailRecipients.load(Context(directory))==listOf("","",""))
 val three=listOf("first@example.test","second@example.test","third@example.test")
 AacEmailRecipients.save(Context(directory),three)
 check(AacEmailRecipients.load(Context(directory))==three)
 AacEmailRecipients.save(Context(directory),listOf(three[0],"",three[2]))
 check(AacEmailRecipients.load(Context(directory))==listOf(three[0],"",three[2]))
 AacEmailRecipients.save(Context(directory),listOf("","",""))
 check(AacEmailRecipients.load(Context(directory))==listOf("","",""))
 check(runCatching { AacEmailRecipients.save(Context(directory),listOf("only@example.test")) }.isFailure)
 println("PASS: 5 recipient storage checks using real application store and disk-backed Context double; Android restart and delivery not tested")
}
""",encoding='utf-8')
source=R/'app/src/main/java/com/rehab2/aac/AacEmailRecipients.kt'
subprocess.run(['java','-cp',os.pathsep.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(out),str(stub),str(source),str(test)],check=True)
subprocess.run(['java','-cp',os.pathsep.join([str(out),str(stdlib)]),'RecipientsKt',str(out)],check=True)