/*
 *  DesktopFileImpl.scala
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
package impl

import java.io.{File, IOException}
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel, ClosedChannelException, CompletionHandler, ReadPendingException, WritePendingException}

import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

/** A wrapper around `java.nio.channels.AsynchronousFileChannel` implementing the
  * `AsyncWritableByteChannel` interface.
  */
final class DesktopFileImpl(val fileSystem: DesktopFileSystem, peer: AsynchronousFileChannel,
                            f: File, readOnly: Boolean)
    extends DesktopWritableFile with CompletionHandler[java.lang.Integer, Promise[Int]] {

//    private[this] val reqThread   = Thread.currentThread()

  private[this] val sync          = new AnyRef
  private[this] var _position     = 0L
  private[this] var _state        = 0     // 0 - none, 1 - read, 2 - write, 3 - closed
  private[this] var _targetState  = 0

  private[this] lazy val _closedPr     = Promise[Unit]()

  def position        : Long        = sync.synchronized { _position }
  def position_=(value: Long): Unit = sync.synchronized { _position = value }

  def skip(len: Long): Unit = sync.synchronized {
    _position += len
  }

  private def checkState(): Unit = _state match {
    case 0 => ()
    case 1 => throw new ReadPendingException
    case 2 => throw new WritePendingException
    case 3 => throw new ClosedChannelException
  }

  def read(dst: ByteBuffer): Future[Int] = {
    sync.synchronized {
      checkState()
      _state = 1
      val pr  = Promise[Int]()
      peer.read(dst, _position, pr, this)
      pr.future
    }
  }

  def write(src: ByteBuffer): Future[Int] = {
    if (readOnly) throw new IOException(s"File $f was opened for reading only")

    sync.synchronized {
      checkState()
      _state  = 2
      val pr  = Promise[Int]()
      peer.write(src, _position, pr, this)
      pr.future
    }
  }

  def size: Long = sync.synchronized { peer.size() }

  def remaining: Long = sync.synchronized { size - position }

  def close(): Future[Unit] = sync.synchronized {
    _state match {
      case 0 =>
        _state = 3
        try {
          peer.close()
          Future.unit
        } catch {
          case NonFatal(ex) =>
            Future.failed(ex)
        }

      case 1 | 2  =>
        _targetState = 3
        _closedPr.future

      case 3 =>
        Future.unit
    }
  }

  def isOpen: Boolean = sync.synchronized {
    !(_state == 3 || _targetState == 3)
    // peer.isOpen
  }

  // ---- CompletionHandler ----

  private def reachedTarget(): Unit = {
    _state = _targetState
    if (_state == 3) {
      try {
        peer.close()
        _closedPr.success(())
      } catch {
        case NonFatal(ex) =>
          _closedPr.failure(ex)
      }
    }
  }

  def completed(res: Integer, pr: Promise[Int]): Unit = sync.synchronized {
    _position += res
    try {
      reachedTarget()
      pr.success(res)
      ()
    } catch {
      case NonFatal(ex) => pr.failure(ex)
    }
  }

  def failed(e: Throwable, pr: Promise[Int]): Unit = sync.synchronized {
    try {
      reachedTarget()
    } finally {
      pr.failure(e)
    }
  }
}