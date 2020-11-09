/*
 *  DesktopFileSystem.scala
 *  (AsyncFile)
 *
 *  Copyright (c) 2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.asyncfile

import java.io.{File, IOException}
import java.net.URI

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

final class DesktopFileSystem()(implicit val executionContext: ExecutionContext) extends AsyncFileSystem {
  def provider: AsyncFileSystemProvider = DesktopFileSystemProvider

  def scheme: String = provider.scheme
  def name  : String = provider.name

  def release(): Unit = ()

  def openRead(uri: URI): Future[AsyncReadableByteChannel] = {
    val f   = getFile(uri)
    val tr  = Try(DesktopFile.openRead(f))
    tr match {
      case Success(ch) => Future.successful (ch)
      case Failure(ex) => Future.failed     (ex)
    }
  }

  def openWrite(uri: URI, append: Boolean = false): Future[AsyncWritableByteChannel] = {
    val f   = getFile(uri)
    val tr  = Try(DesktopFile.openWrite(f, append = append))
    tr match {
      case Success(ch) => Future.successful (ch)
      case Failure(ex) => Future.failed     (ex)
    }
  }

  def mkDir(uri: URI): Future[Unit] = {
    val f   = getFile(uri)
    val res = f.mkdir()
    if (res) Future.unit else Future.failed(new IOException(s"Could not create directory $uri"))
  }

  def mkDirs(uri: URI): Future[Unit] = {
    val f   = getFile(uri)
    val res = f.mkdirs()
    if (res) Future.unit else Future.failed(new IOException(s"Could not create directories $uri"))
  }

  def delete(uri: URI): Future[Unit] = {
    val f   = getFile(uri)
    val res = f.delete()
    if (res) Future.unit else Future.failed(new IOException(s"Could not delete file $uri"))
  }

  def info(uri: URI): Future[FileInfo] = {
    val f = getFile(uri)
    if (f.exists()) {
      var flags   = 0
      if (f.isFile      ) flags |= FileInfo.IS_FILE
      if (f.isDirectory ) flags |= FileInfo.IS_DIRECTORY
      if (f.isHidden    ) flags |= FileInfo.IS_HIDDEN
      if (f.canRead     ) flags |= FileInfo.CAN_READ
      if (f.canWrite    ) flags |= FileInfo.CAN_WRITE
      if (f.canExecute  ) flags |= FileInfo.CAN_EXECUTE
      val info = FileInfo(uri, flags = flags, lastModified = f.lastModified(), size = f.length())
      Future.successful(info)
    } else {
      Future.failed(new FileNotFoundException(uri))
    }
  }

  def listDir(uri: URI): Future[ISeq[URI]] = {
    val f   = getFile(uri)
    val arr = f.listFiles()
    val res = arr.iterator.map(_.toURI).toSeq
    Future.successful(res)
  }

  private def getFile(uri: URI): File = new File(uri)
}
