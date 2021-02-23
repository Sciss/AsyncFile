/*
 *  AsyncReadableByteBuffer.scala
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

import java.io.EOFException
import java.nio.{Buffer, BufferOverflowException, ByteBuffer}

import scala.concurrent.{ExecutionContext, Future}
import scala.math.min

/** An auxiliary class for reading ahead in an asynchronous file, by buffering. */
final class AsyncReadableByteBuffer(ch: AsyncReadableByteChannel, capacity0: Int = 128) {
  private[this] val cap     = capacity0
  private[this] var fileRem = ch.size - ch.position
  private[this] val arr     = new Array[Byte](cap)

  /** byte order is big endian */
  val buffer: ByteBuffer = ByteBuffer.wrap(arr)
  (buffer: Buffer).limit(0) // initially 'empty'

  implicit val executionContext: ExecutionContext = ch.fileSystem.executionContext

  def ensure(n: Int): Future[Unit] = {
    val lim = buffer.limit()
    val pos = buffer.position()
    val rem = lim - pos
    if (rem >= n) Future.successful(())
    else {
      val stop = rem + n
      // XXX TODO should be able to grow
      if (stop > cap) throw new BufferOverflowException()
      // move remaining content to the beginning
      System.arraycopy(arr, pos, arr, 0, rem)
      val capM = min(cap, fileRem - rem).toInt
      (buffer: Buffer).position(rem).limit(capM)
      //        ais.position = 0
      val futRead = ch.read(buffer)
      futRead.map { m =>
        if (m < n) throw new EOFException()
        (buffer: Buffer).position(0)
        fileRem -= m
        ()
      }
    }
  }

  def skip(n: Long): Unit = {
    val lim = buffer.limit()
    val pos = buffer.position()
    val rem = lim - pos
    if (n <= rem) {
      (buffer: Buffer).position((pos + n).toInt)
      ()
    } else {
      (buffer: Buffer).position(0).limit(0)
      val skipCh = n - rem
      ch.skip(skipCh)
    }
  }

  // synchronizes channel position with
  // current buffer position (and sets buffer limit)
  def purge(): Unit = {
    val lim = buffer.limit()
    val pos = buffer.position()
    val rem = lim - pos
    if (rem > 0) {
      (buffer: Buffer).position(0).limit(0)
      ch.skip(-rem)
    }
  }
}
