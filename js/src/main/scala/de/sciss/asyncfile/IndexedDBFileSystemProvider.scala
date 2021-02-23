/*
 *  IndexedDBFileSystemProvider.scala
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

import de.sciss.asyncfile.AsyncFile.log
import de.sciss.asyncfile.IndexedDBFile.{STORE_FILES, reqToFuture}
import org.scalajs.dom
import org.scalajs.dom.raw.IDBDatabase

import scala.concurrent.{ExecutionContext, Future}

object IndexedDBFileSystemProvider  extends AsyncFileSystemProvider {
  final val scheme  = "idb"
  final val name    = "IndexedDB File System"

  private val VERSION         = 1
  private val DB_FILE_SYSTEM  = "fs"

  private def openFileSystem(): Future[IDBDatabase] = {
    val req = dom.window.indexedDB.open(DB_FILE_SYSTEM, VERSION)

    req.onupgradeneeded = { _ =>
      val db = req.result.asInstanceOf[IDBDatabase]
      val storeNames = db.objectStoreNames
      log.info(s"Upgrading to version ${db.version}. Deleting ${storeNames.length} local objects")
      var i = 0
      while (i < storeNames.length) {
        val storeName = storeNames(i)
        db.deleteObjectStore(storeName)
        i += 1
      }
      db.createObjectStore(STORE_FILES)
    }

    reqToFuture(req) { _ =>
      log.info("Success creating/accessing IndexedDB database")
      req.result.asInstanceOf[IDBDatabase]
    }
  }
  def obtain()(implicit executionContext: ExecutionContext): Future[AsyncFileSystem] =
    openFileSystem().map { db =>
      new IndexedDBFileSystem(db)
    }
}
