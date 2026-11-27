"""Exercise real loader concurrency with Android API substitutes, not a device test."""
from pathlib import Path
exec(Path(__file__).with_name('cache.py').read_text(encoding='utf8').split("source('Context.kt'")[0])
source('Bitmap.kt', '''package android.graphics
class Bitmap(val allocationByteCount:Int=1048576)
object BitmapFactory {
 class Options {var inJustDecodeBounds=false;var outWidth=0;var outHeight=0;var inSampleSize=1}
 val samples=java.util.concurrent.CopyOnWriteArrayList<Int>()
 val threads=java.util.concurrent.CopyOnWriteArrayList<String>()
 fun decodeFile(path:String,o:Options):Bitmap? {
  threads.add(Thread.currentThread().name)
  if(o.inJustDecodeBounds){o.outWidth=4096;o.outHeight=2048;return null}
  samples.add(o.inSampleSize);return Bitmap()
 }
}''')
source('Handler.kt', '''package android.os
class Looper {companion object {fun getMainLooper()=Looper()}}
class Handler(l:Looper) {
 companion object {val queue=java.util.concurrent.ConcurrentLinkedQueue<()->Unit>();fun drain(){while(true)(queue.poll()?:break).invoke()}}
 fun post(r:()->Unit){queue.add(r)}
 fun removeCallbacksAndMessages(token:Any?){queue.clear()}
}''')
source('Cache.kt', '''package android.util
open class LruCache<K,V>(limit:Int) {
 private val values=java.util.concurrent.ConcurrentHashMap<K,V>()
 open fun sizeOf(key:K,value:V)=1
 fun get(key:K)=values[key]
 fun put(key:K,value:V){values[key]=value}
 fun evictAll(){values.clear()}
}''')
source('Test.kt', '''import com.rehab2.aac.AacIconLoader
import android.os.Handler
import android.graphics.BitmapFactory
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
fun main(args:Array<String>) {
 var count=0
 fun verify(ok:Boolean){check(ok);count++}
 val file=File(args[0]).apply{writeText("source")}
 val loader=AacIconLoader();var callbacks=0
 loader.load({file}){verify(it!=null);callbacks++}.get(5,TimeUnit.SECONDS)
 verify(callbacks==0);Handler.drain();verify(callbacks==1)
 verify(BitmapFactory.samples.toList()==listOf(8))
 verify(BitmapFactory.threads.none{it==Thread.currentThread().name})
 loader.load({file}){callbacks++}.get(5,TimeUnit.SECONDS);Handler.drain()
 verify(BitmapFactory.samples.size==1)
 file.writeText("changed source")
 loader.load({file}){callbacks++}.get(5,TimeUnit.SECONDS);Handler.drain()
 verify(BitmapFactory.samples.size==2)
 loader.load({throw java.io.IOException("missing")}){verify(it==null)}.get(5,TimeUnit.SECONDS);Handler.drain()
 val entered=CountDownLatch(1);val release=CountDownLatch(1)
 val pending=loader.load({entered.countDown();release.await();file}){error("cancelled callback")}
 verify(entered.await(5,TimeUnit.SECONDS));pending.cancel(true);release.countDown()
 loader.load({file}){}.get(5,TimeUnit.SECONDS);Handler.drain()
 loader.load({file}){error("closed callback")}.get(5,TimeUnit.SECONDS)
 loader.close();Handler.drain();verify(file.readText()=="changed source")
 println("PASS: $count asynchronous image loader checks; Android bitmap/handler/cache fakes")
}''')
files.append(str(a/'AacIconLoader.kt'))
subprocess.run(['java','-cp',os.pathsep.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(out),*files],check=True)
subprocess.run(['java','-cp',os.pathsep.join([str(out),str(stdlib)]),'TestKt',str(out/'image.png')],check=True)
