/*
 *  AsyncFile.scala
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

import de.sciss.log.{Level, Logger}

import scala.concurrent.{ExecutionContext, Future}

trait AsyncFileCompanion {
  private var fileSystemMap: Map[String, AsyncFileSystemProvider] = Map.empty

  def fileSystemProviders: Iterable[AsyncFileSystemProvider] = fileSystemMap.values

  def addFileSystemProvider(fs: AsyncFileSystemProvider): Unit =
    fileSystemMap += (fs.scheme -> fs)

  def getFileSystemProvider(scheme: String): Option[AsyncFileSystemProvider] =
    if (scheme == null) None else fileSystemMap.get(scheme)

  def getFileSystemProvider(uri: URI): Option[AsyncFileSystemProvider] =
    getFileSystemProvider(uri.getScheme)

  def openRead(uri: URI)(implicit executionContext: ExecutionContext): Future[AsyncReadableByteChannel] = {
    val scheme  = uri.getScheme
    val fs      = getFileSystemProvider(scheme)
      .getOrElse(throw new UnsupportedFileSystemException(uri))
    fs.obtain().flatMap(_.openRead(uri))
  }

  def openWrite(uri: URI)(implicit executionContext: ExecutionContext): Future[AsyncWritableByteChannel] = {
    val scheme  = uri.getScheme
    val fs      = getFileSystemProvider(scheme)
      .getOrElse(throw new UnsupportedFileSystemException(uri))
    fs.obtain().flatMap(_.openWrite(uri))
  }

  private final val PROP_LOG_LEVEL = "de.sciss.asyncfile.log-level"

  val log: Logger = new Logger("AsyncFile",
    try {
      sys.props.getOrElse(PROP_LOG_LEVEL, null).toInt
    } catch {
      case _: Exception => Level.Warn
    }
  )
}
