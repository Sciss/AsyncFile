/*
 *  AsyncFileSystemProvider.scala
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

import scala.concurrent.{ExecutionContext, Future}

abstract class AsyncFileSystemProvider {
  /** The URI scheme of this file system, such as `"file"` (desktop) or `"idb"` (IndexedDB in the browser). */
  def scheme: String

  /** The logical name of the file system. */
  def name  : String

  def obtain()(implicit executionContext: ExecutionContext): Future[AsyncFileSystem]
}
