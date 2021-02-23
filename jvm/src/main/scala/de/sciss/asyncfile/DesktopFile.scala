/*
 *  DesktopFile.scala
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

import java.io.File
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.StandardOpenOption

import de.sciss.asyncfile.impl.DesktopFileImpl

/** A wrapper around `java.nio.channels.AsynchronousFileChannel` implementing the
 * `AsyncWritableByteChannel` interface.
 */
object DesktopFile {
  def openRead(f: File)(implicit fs: DesktopFileSystem): DesktopFile = {
    val p   = f.toPath
    val jch = AsynchronousFileChannel.open(p,
      StandardOpenOption.READ,
    )
    new DesktopFileImpl(fs, jch, f, readOnly = true)
  }

  def openWrite(f: File, append: Boolean = false)
               (implicit fs: DesktopFileSystem): DesktopWritableFile = {
    val p   = f.toPath
    val jch = if (append) {
      AsynchronousFileChannel.open(p,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
      )
    } else {
      // there is a very weird thing: if the file already
      // exists, although `TRUNCATE_EXISTING` is set and works,
      // flushing and closing the file takes _a lot_ (>5 times) longer
      // than preemptively deleting the file before re-open.
      f.delete()
      AsynchronousFileChannel.open(p,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
      )
    }
    new DesktopFileImpl(fs, jch, f, readOnly = false)
  }
}
trait DesktopFile         extends AsyncReadableByteChannel
trait DesktopWritableFile extends DesktopFile with AsyncWritableByteChannel

