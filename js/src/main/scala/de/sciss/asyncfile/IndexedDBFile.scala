/*
 *  IndexedDBFile.scala
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

package de.sciss.asyncfile

import java.io.IOException
import java.net.URI
import de.sciss.asyncfile.AsyncFile.log
import de.sciss.asyncfile.impl.IndexedDBFileImpl
import org.scalajs.dom
import org.scalajs.dom.DOMException
import org.scalajs.dom.raw.{IDBKeyRange, IDBObjectStore, IDBRequest}

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.{typedarray => jsta}
import scala.util.control.NonFatal

object IndexedDBFile {
  private val BLOCK_SIZE      = 8192
  private val META_COOKIE     = 0x4d455441  // "META"
  private val KEY_META        = "meta"

  private[asyncfile] val STORE_FILES  = "files"
  private[asyncfile] val STORES_FILES = js.Array(STORE_FILES)

  private[asyncfile] val READ_ONLY    = "readonly"
  private[asyncfile] val READ_WRITE   = "readwrite"

  private def uriFromPath(path: String): URI =
    new URI(IndexedDBFileSystemProvider.scheme, null, path, null)

  object Meta {
    def fromArrayBuffer(uri: URI, b: js.typedarray.ArrayBuffer): Meta = {
      val bi  = new jsta.Int32Array(b)  // N.B.: little endian (OS dependent)
      val cookie: Int = bi(0)
      if (cookie != META_COOKIE) {
        throw new IOException(s"Expected cookie 0x${META_COOKIE.toHexString}, but found 0x${cookie.toHexString}")
      }
      val blockSize: Int = bi(1)
      val lastModHi: Int = bi(2)
      val lastModLo: Int = bi(3)
      val szHi     : Int = bi(4)
      val szLo     : Int = bi(5)
      val flags    : Int = bi(6)
      val lastModified  = (lastModHi .toLong << 32) | (lastModLo .toLong & 0xFFFFFFFFL)
      val size          = (szHi      .toLong << 32) | (szLo      .toLong & 0xFFFFFFFFL)
      val info = FileInfo(
        uri, flags = flags, lastModified = lastModified, size = size
      )
      Meta(blockSize = blockSize, info = info)
    }
  }
  case class Meta(blockSize: Int, info: FileInfo) {
    def toArrayBuffer: jsta.ArrayBuffer = {
      val b   = new jsta.ArrayBuffer(7 * 4)
      val bi  = new jsta.Int32Array(b)  // N.B.: little endian (OS dependent)
      bi(0)   = META_COOKIE
      bi(1)   = blockSize
      import info._
      bi(2)   = (lastModified >> 32).toInt
      bi(3)   =  lastModified       .toInt
      bi(4)   = (size         >> 32).toInt
      bi(5)   =  size               .toInt
      bi(6)   = flags
      b
    }
  }

//  private[asyncfile] def mkExceptionMessage(e: dom.ErrorEvent): String =
//    s"in ${e.filename} line ${e.lineno} column ${e.colno}: ${e.message}"

  private[asyncfile] def mkExceptionMessage(e: DOMException): String =
    s"${e.name}: ${e.message}"

//  private[asyncfile] def mkException(e: dom.ErrorEvent): Exception =
//    new IOException(mkExceptionMessage(e))

  private[asyncfile] def mkException(e: DOMException): Exception =
    new IOException(mkExceptionMessage(e))

//  private[asyncfile] def mkStoreName(path: String): String = s"fs:$path"

  private[asyncfile] def reqToFuture[A](req: IDBRequest, failure: DOMException => Throwable = mkException)
                                       (success: dom.Event => A): Future[A] = {
    val pr = Promise[A]()
    req.onerror = { _ =>
      pr.failure(failure(req.error))
    }
    req.onsuccess = { e =>
      try {
        val v = success(e)
        pr.success(v)
      } catch {
        case NonFatal(ex) =>
          pr.failure(ex)
      }
    }
    pr.future
  }

  def openRead(uri: URI)(implicit fs: IndexedDBFileSystem): Future[IndexedDBFile] = {
    val path = uri.getPath
    log.info(s"openRead($path)")

    import fs.{db, executionContext}

    val tx = db.transaction(STORES_FILES, mode = READ_ONLY)
    implicit val store: IDBObjectStore = tx.objectStore(STORE_FILES)
    val futMeta = readMeta(uri)
    futMeta.map { meta =>
      val ch    = new IndexedDBFileImpl(
        fileSystem        = fs,
        meta0     = meta,
        readOnly  = true,
      )
      ch
    }
  }

  def readMeta(uri: URI)(implicit store: IDBObjectStore): Future[Meta] = {
    val path = uri.getPath
    log.debug(s"readMeta($path)")
    val req = store.get(js.Array(path, KEY_META))
    reqToFuture(req) { _ =>
      val bMetaOpt  = req.result.asInstanceOf[js.UndefOr[jsta.ArrayBuffer]]
      val bMeta     = bMetaOpt.getOrElse(throw new FileNotFoundException(uriFromPath(path)))
      val meta      = Meta.fromArrayBuffer(uri, bMeta)
      meta
    }
  }

//  def updateMeta(path: String)(meta: Meta => Meta)(implicit store: IDBObjectStore,
//                                                   executionContext: ExecutionContext): Future[Unit] =
//    readMeta(path).flatMap { metaIn =>
//      val metaOut = meta(metaIn)
//      writeMeta(path, metaOut)
//    }

  def writeMeta(meta: Meta)(implicit store: IDBObjectStore): Future[Unit] = {
    val path = meta.info.uri.getPath
    log.debug(s"writeMeta($path, $meta)")
    val bMeta = meta.toArrayBuffer
    val req   = store.put(key = js.Array(path, KEY_META), value = bMeta)
    reqToFuture(req)(_ => ())
  }

  def delete(uri: URI)(implicit fs: IndexedDBFileSystem): Future[Unit] = {
    import fs.{db, executionContext}
    val path  = uri.getPath
    val tx    = db.transaction(STORES_FILES, mode = READ_WRITE)
    implicit val store: IDBObjectStore = tx.objectStore(STORE_FILES)
    val reqDelMeta = store.delete(js.Array(path, KEY_META))
    val reqDelData = store.delete(IDBKeyRange.bound(js.Array(path, 0), js.Array(path, Int.MaxValue)))
    val futDelMeta = reqToFuture(reqDelMeta)(_ => ())
    val futDelData = reqToFuture(reqDelData)(_ => ())
    futDelMeta.flatMap(_ => futDelData)
  }

  def openWrite(uri: URI, append: Boolean = false)(implicit fs: IndexedDBFileSystem): Future[IndexedDWritableBFile] = {
    val path = uri.getPath
    log.info(s"openWrite($path, append = $append)")

    import fs.{db, executionContext}

    val tx = db.transaction(STORES_FILES, mode = READ_WRITE)
    implicit val store: IDBObjectStore = tx.objectStore(STORE_FILES)
    log.info(s"opened object store for $path")

    def clear(): Future[Meta] = {
      val reqClear = store.clear()
      reqToFuture(reqClear) { _ =>
        log.info("creating new initial meta data")
        val fi = FileInfo(
          uri           = uri,
          flags         = FileInfo.IS_FILE | FileInfo.CAN_READ | FileInfo.CAN_WRITE,
          lastModified  = System.currentTimeMillis(),
          size          = 0L,
        )
        Meta(blockSize = BLOCK_SIZE, info = fi)
      }
    }

    val futMeta0: Future[Meta] = if (append) {
      readMeta(uri).recoverWith { case _ => clear() }
    } else {
      clear()
    }

    val futMeta = futMeta0.flatMap { meta =>
      writeMeta(meta).map(_ => meta)
    }

    futMeta.map { meta =>
      new IndexedDBFileImpl(
        fileSystem        = fs,
        meta0     = meta,
        readOnly  = false,
      )
    }
  }
}
trait IndexedDBFile         extends AsyncReadableByteChannel
trait IndexedDWritableBFile extends IndexedDBFile with AsyncWritableByteChannel
