/*
 *  IndexedDBFileSystem.scala
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

import java.net.URI
import de.sciss.asyncfile.IndexedDBFile.{READ_ONLY, STORES_FILES, STORE_FILES}
import de.sciss.model.Model
import org.scalajs.dom.raw.{IDBDatabase, IDBObjectStore}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.{ExecutionContext, Future}

final class IndexedDBFileSystem(private[asyncfile] val db: IDBDatabase)
                               (implicit val executionContext: ExecutionContext)
  extends AsyncFileSystem { self =>

  override def scheme: String = IndexedDBFileSystemProvider.scheme
  override def name  : String = IndexedDBFileSystemProvider.name

  override def provider: AsyncFileSystemProvider = IndexedDBFileSystemProvider

  override def release(): Unit =
    db.close()

  private def requireScheme(uri: URI): Unit = {
    val _scheme = uri.getScheme
    if (_scheme != scheme) throw new IllegalArgumentException(s"Scheme ${_scheme} is not $scheme")
  }

  override def openRead(uri: URI): Future[AsyncReadableByteChannel] = {
    requireScheme(uri)
    IndexedDBFile.openRead(uri)(self)
  }

  override def openWrite(uri: URI, append: Boolean = false): Future[AsyncWritableByteChannel] = {
    requireScheme(uri)
    IndexedDBFile.openWrite(uri, append = append)(self)
  }

  override def mkDir(uri: URI): Future[Unit] =
    Future.failed(new NotImplementedError("idb.mkDir")) // XXX TODO

  override def mkDirs(uri: URI): Future[Unit] =
    Future.failed(new NotImplementedError("idb.mkDirs")) // XXX TODO

  override def delete(uri: URI): Future[Unit] = {
    requireScheme(uri)
    IndexedDBFile.delete(uri)(this)
  }

  override def info(uri: URI): Future[FileInfo] = {
    requireScheme(uri)
    val tx = db.transaction(STORES_FILES, mode = READ_ONLY)
    implicit val store: IDBObjectStore = tx.objectStore(STORE_FILES)
    val futMeta = IndexedDBFile.readMeta(uri)
    futMeta.map(_.info)
  }

  override def listDir(uri: URI): Future[ISeq[URI]] =
    Future.failed(new NotImplementedError("idb.listDir")) // XXX TODO

  override def watchFile(uri: URI, modify: Boolean): Model[Watch.File] = throw new NotImplementedError("idb.watchFile" ) // XXX TODO
  override def watchDir (uri: URI, modify: Boolean): Model[Watch.Base] = throw new NotImplementedError("idb.watchDir"  ) // XXX TODO
}
