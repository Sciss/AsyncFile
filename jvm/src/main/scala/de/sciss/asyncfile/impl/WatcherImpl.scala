/*
 *  WatcherImpl.scala
 *  (AsyncFile)
 *
 *  Copyright (c) 2020-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.asyncfile.impl

import de.sciss.asyncfile.Watch
import de.sciss.model.Model
import de.sciss.model.impl.ModelImpl

import java.net.URI
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{FileSystems, Path, Paths, WatchEvent, WatchKey, WatchService}
import scala.collection.mutable
import scala.util.Try
import scala.util.control.NonFatal

object WatcherImpl {
  private lazy val _service = Try(FileSystems.getDefault.newWatchService())

  def service: Try[WatchService] = _service

  def fileModel(uri: URI, modify: Boolean): Model[Watch.File] = {
    val s = service.get
    new FileModel(s, uri, modify = modify)
  }

  def dirModel(uri: URI, modify: Boolean): Model[Watch.Base] = {
    val s = service.get
    new DirModel(s, uri, modify = modify)
  }

  private object thread extends Thread("File Watcher") {
    setDaemon(true)

    lazy val activated: Unit = start()

    private val sync  = new AnyRef
    private val map   = mutable.Map.empty[WatchKey, List[WatchListener]]

    def add(key: WatchKey, listener: WatchListener): Unit = sync.synchronized {
      val xs1 = map.get(key) match {
        case None       => listener :: Nil
        case Some(xs0)  => xs0 :+ listener
      }
      map.put(key, xs1)
    }

    def remove(key: WatchKey, listener: WatchListener): Unit = sync.synchronized {
      map(key) match {
        case `listener` :: Nil =>
          map.remove(key)
          key.cancel()
        case xs0 =>
          val xs1 = xs0.diff(listener :: Nil)
          map.put(key, xs1)
      }
    }

    override def run(): Unit = {
      val s = service.get
      while (true) {
        val key = s.take()
        val xs  = sync.synchronized {
          map.getOrElse(key, Nil)
        }
        val it = key.pollEvents().iterator()
        while (it.hasNext) {
          val e       = it.next()
          val kind    = e.kind()
          val context = e.context() match {
            case p: Path  => Some(p)
            case _        => None
          }
          xs.foreach { listener =>
            try {
              listener.watchEvent(kind, context)
            } catch {
              case NonFatal(ex) =>
                ex.printStackTrace()
            }
          }
          key.reset()
        }
      }
    }
  }

  trait WatchListener {
    def watchEvent(kind: WatchEvent.Kind[_], context: Option[Path]): Unit
  }

  private final class FileModel(s: WatchService, uri: URI, modify: Boolean)
    extends BaseModel[Watch.File](s, uri) {

    override protected def selfKinds: Array[WatchEvent.Kind[_]] =
      if (modify) Array(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY) else Array(ENTRY_CREATE, ENTRY_DELETE)
  }

  private final class DirModel(s: WatchService, uri: URI, modify: Boolean)
    extends BaseModel[Watch.Base](s, uri) {

    override protected def selfKinds: Array[WatchEvent.Kind[_]] = Array(ENTRY_CREATE, ENTRY_DELETE)

    private var dirKeyOption = Option.empty[WatchKey]

    private object DirWatch extends WatchListener {
      override def watchEvent(kind: WatchEvent.Kind[_], context: Option[Path]): Unit =
        context.foreach { childPath =>
//          val childURI = path.resolve(childPath).toUri
          val child = childPath.toString  // XXX TODO: according to API a relative path; always correct?
          kind match {
            case ENTRY_CREATE => dispatch(Watch.ChildCreated (uri, child /* childURI */))
            case ENTRY_DELETE => dispatch(Watch.ChildDeleted (uri, child /* childURI */))
            case ENTRY_MODIFY => dispatch(Watch.ChildModified(uri, child /* childURI */))
            case _ => ()
          }
        }
    }

    override protected def startListening(): Unit = {
      super.startListening()
      try {
        val dirKinds  = if (modify) Array(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY) else Array(ENTRY_CREATE, ENTRY_DELETE)
        val key       = path.register(s, dirKinds: _*)
        dirKeyOption = Some(key)
        thread.add(key, DirWatch)
        thread.activated
      } catch {
        case NonFatal(_) => ()
      }
    }

    override protected def stopListening(): Unit = {
      super.stopListening()
      dirKeyOption.foreach { key =>
        thread.remove(key, DirWatch)
        dirKeyOption = None
      }
    }
  }

  private abstract class BaseModel[U >: Watch.File](s: WatchService, uri: URI)
    extends ModelImpl[U] {

    // ---- abstract ----

    protected def selfKinds: Array[WatchEvent.Kind[_]]

    // ---- impl ----

    protected final val path = Paths.get(uri)

    private val parentPath: Path = {
      val d = path.toFile.getParentFile
      if (d == null) null else d.toPath
    }
    private val relPath: Path =
      if (parentPath == null) null else parentPath.relativize(path)

    private var selfKeyOption = Option.empty[WatchKey]

    protected object SelfWatch extends WatchListener {
      override def watchEvent(kind: WatchEvent.Kind[_], context: Option[Path]): Unit =
        if (context.contains(relPath)) kind match {
          case ENTRY_CREATE => dispatch(Watch.Created (uri))
          case ENTRY_DELETE => dispatch(Watch.Deleted (uri))
          case ENTRY_MODIFY => dispatch(Watch.Modified(uri))
          case _ => ()
        }
    }

    override protected def startListening(): Unit =
      if (parentPath != null) try {
        val key   = parentPath.register(s, selfKinds: _*)
        selfKeyOption = Some(key)
        thread.add(key, SelfWatch)
        thread.activated
      } catch {
        case NonFatal(_) => ()
      }

    override protected def stopListening(): Unit =
      selfKeyOption.foreach { key =>
        thread.remove(key, SelfWatch)
        selfKeyOption = None
      }
  }
}
