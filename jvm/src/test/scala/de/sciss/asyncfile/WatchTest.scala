package de.sciss.asyncfile

import de.sciss.model.Model

import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object WatchTest {
  def main(args: Array[String]): Unit = {
    val tpe     = args.head
    val isFile  = tpe == "--file"
    val isDir   = tpe == "--dir"
    val twice   = args.length >= 3 && args(2) == "--twice"
    require (isFile || isDir)
    val uri = new URI(args(1))
    val fs  = Await.result(AsyncFile.getFileSystemProvider(uri).get.obtain(), Duration.Inf)
    val m   = if (isFile) fs.watchFile(uri, modify = true) else fs.watchDir(uri, modify = true)
    var modCount = 0
    val num = if (twice) 2 else 1
    val listeners = (1 to num).map { id =>
      val l: Model.Listener[Watch.Base] = {
        case Watch.Modified (_) => modCount += 1
        case Watch.Deleted  (_) => println(s"($id) Deleted. modCount = $modCount")
        case Watch.Created  (_) => println(s"($id) Created"); modCount = 0
        case event              => println(s"($id) $event")
      }
      l
    }
    listeners.foreach { l =>
      println("Listening...")
      m.addListener(l)
      println("Wait...")
      Thread.sleep(12000)
    }
    listeners.foreach { l =>
      println("Stop listening")
      m.removeListener(l)
      println("Wait...")
      Thread.sleep(12000)
    }
    println("Exit")
    sys.exit()
  }
}
