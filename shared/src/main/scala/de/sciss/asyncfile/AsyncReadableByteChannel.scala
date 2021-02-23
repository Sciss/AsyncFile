/*
 *  AsyncReadableByteChannel.scala
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

import java.nio.ByteBuffer

import scala.concurrent.Future

/** Similar to a mix between `java.nio.channels.AsynchronousFileChannel` and
  * `java.nio.channels.AsynchronousByteChannel`, allowing random access positioning,
  * but internally keeping track to the current position. One should assume that
  * at most one read operation can be submitted at a time (although this constraint
  * might be lifted in the future).
  */
trait AsyncReadableByteChannel extends AsyncChannel {
  var position: Long

  def size: Long

  def remaining: Long

  /** Advances the position by `len` bytes. Note that negative
    * numbers are allowed, essentially moving the position backwards.
    */
  def skip(len: Long): Unit

  val fileSystem: AsyncFileSystem

  def read(dst: ByteBuffer): Future[Int]
}
