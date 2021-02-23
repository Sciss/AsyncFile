/*
 *  AsyncFileSystem.scala
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

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.{ExecutionContext, Future}

trait AsyncFileSystem {
  /** The provider that created this file system. */
  def provider: AsyncFileSystemProvider

  /** States the intent to no longer use the file system.
    * Calling any of its methods afterwards is undefined behavior.
    */
  def release(): Unit

  /** The URI scheme of this file system. Same as `provider.scheme` */
  def scheme: String

  /** The logical name of the file system. */
  def name  : String

  /** Opens a file for reading. */
  def openRead(uri: URI): Future[AsyncReadableByteChannel]

  /** Opens a file for writing. */
  def openWrite(uri: URI, append: Boolean = false): Future[AsyncWritableByteChannel]

  /** Creates a directory within a parent directory.
   * The future value indicates success.
   */
  def mkDir(uri: URI): Future[Unit]

  /** Creates a directory along with all necessary parent directories if they do not yet exist.
   * The future value indicates success.
   */
  def mkDirs(uri: URI): Future[Unit]

  /** Deletes a file. */
  def delete(uri: URI): Future[Unit]

  /** Obtains meta data of a file.
    * If the file does not exist, the failure is `FileNotFoundException`.
    */
  def info(uri: URI): Future[FileInfo]

  /** Lists the contents of a directory. */
  def listDir(uri: URI): Future[ISeq[URI]]

  implicit def executionContext: ExecutionContext
}