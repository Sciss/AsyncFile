/*
 *  IndexedDBFileImpl.scala
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

import java.io.IOException
import java.nio.{Buffer, ByteBuffer}

import de.sciss.asyncfile.AsyncFile.log
import de.sciss.asyncfile.IndexedDBFile.{Meta, READ_ONLY, READ_WRITE, STORES_FILES, STORE_FILES, reqToFuture, writeMeta}
import org.scalajs.dom.raw.IDBObjectStore

import scala.concurrent.{Future, Promise}
import scala.math.min
import scala.scalajs.js
import scala.scalajs.js.typedarray.Int8Array
import scala.scalajs.js.{typedarray => jsta}

// XXX TODO: caching is not yet implemented
private[asyncfile] final class IndexedDBFileImpl(val fileSystem: IndexedDBFileSystem,
                                                 meta0: Meta, readOnly: Boolean)
  extends IndexedDWritableBFile {

  private[this] val blockSize       = meta0.blockSize
  private[this] val path            = meta0.info.uri.getPath
  private[this] var _position       = 0L
  private[this] var _size           = meta0.info.size
  private[this] val swapBuf         = new jsta.ArrayBuffer(blockSize)
  private[this] val swapTArray      = new Int8Array(swapBuf)
//  private[this] val swapBB          = AudioFile.allocByteBuffer(blockSize)

  private[this] var _state        = 0   // 0 - none, 1 - read, 2 - write, 3 - closed
  private[this] var _targetState  = 0   // can only ever become 3
  private[this] var _dirty        = false

  private[this] lazy val _closedPr     = Promise[Unit]()

  private[this] var cachedBlockIdx  = -1
  private[this] var cacheBlock: Array[Byte] = _

  def size      : Long = _size
  def position  : Long = _position
  def remaining : Long = _size - _position

  private def key(idx: Int): js.Any =
    js.Array[Any](path, idx)

//  private def keyRange(from: Int, until: Int): IDBKeyRange =
//    IDBKeyRange.bound(js.Array(path, from), js.Array(path, until))

  def position_=(value: Long): Unit = {
    if (value < 0L || value > _size) throw new IOException(s"Illegal seek position $value (file size is $size)")
    _position = value
  }

  private def blockIndex  (pos: Long): Int = (pos / blockSize).toInt
  private def blockOffset (pos: Long): Int = (pos % blockSize).toInt

  /** Advances the position by `len` bytes. Note that negative
    * numbers are allowed, essentially moving the position backwards.
    */
  def skip(len: Long): Unit =
    position += len

  private def checkState(): Unit = _state match {
    case 0 => ()
    case 1 => throw new IOException(s"File $path has a pending read" )
    case 2 => throw new IOException(s"File $path has a pending write")
    case 3 => throw new IOException(s"File $path was already closed" )
  }

  def read(dst: ByteBuffer): Future[Int] = {
    checkState()

    val readLen = min(dst.remaining(), remaining).toInt
    if (readLen == 0) return Future.successful(readLen)

    val posStart  = _position
    val posStop   = posStart + readLen
    var bIdxStart = blockIndex  (posStart )
    var bOffStart = blockOffset (posStart )
    val bIdxStop  = blockIndex  (posStop  )
    val bOffStop  = blockOffset (posStop  )
    val firstStop = if (bOffStart == 0) 0 else if (bIdxStop > bIdxStart) blockSize else bOffStop
    val lastStop  = if (firstStop == 0 || bIdxStop > bIdxStart) bOffStop else 0

    log.debug(s"read([pos ${dst.position()}, rem ${dst.remaining()}]); readLen $readLen")
    log.debug(s"  posStart $posStart; posStop $posStop; bIdxStart $bIdxStart; bOffStart $bOffStart; bIdxStop $bIdxStop bOffStop $bOffStop")

    if (bIdxStart == cachedBlockIdx &&
       (bIdxStop == cachedBlockIdx || (bIdxStop == cachedBlockIdx + 1 && bOffStop == 0))) {

      dst.put(cacheBlock, bOffStart, readLen)
      _position += readLen
      return Future.successful(readLen)
    }

    _state = 1

    if (bIdxStart == cachedBlockIdx) {
      val chunk = blockSize - bOffStart
      dst.put(cacheBlock, bOffStart, chunk)
      position  += chunk
      bIdxStart += 1
      bOffStart  = 0
    }

    val db = fileSystem.db
    val tx = db.transaction(STORES_FILES, mode = READ_ONLY)
    implicit val store: IDBObjectStore = tx.objectStore(STORE_FILES)

    // N.B. do not use outer variables here as this runs in
    // a future callback
    def readSlice(idx: Int, pos: Int, start: Int, stop: Int): Future[Unit] = {
      log.debug(s"getSlice($pos, $idx, $start, $stop)")
      val req = store.get(key(idx))
      val fut = reqToFuture(req) { _ =>
        val buf = req.result.asInstanceOf[jsta.ArrayBuffer]
        val arr = new Int8Array(buf)
        assert (arr.length >= stop)

        var i   = start
        var j   = pos
        while (i < stop) {
          dst.put(j, arr(i))
          i += 1; j += 1
        }
        // TODO: copy data; update `cacheBlock`
      }
      fut
    }

    var txn       = List.empty[Future[Unit]]
    var bIdx      = bIdxStart
    var dstPos    = dst.position()

    // first block needs to update existing data
    if (firstStop > 0) {
      val futFirst = readSlice(idx = bIdxStart, pos = dstPos, start = bOffStart, stop = firstStop)
      txn        ::= futFirst
      bIdx        += 1
      dstPos      += firstStop - bOffStart
    }

    // "middle" blocks are put directly
    while (bIdx < bIdxStop) {
      val futMid    = readSlice(idx = bIdx, pos = dstPos, start = 0, stop = blockSize)
      // according to spec, it is allowed to send multiple write requests at once
      txn          ::= futMid
      dstPos        += blockSize
      bIdx          += 1
    }

    // last block
    if (lastStop > 0) {
      val futLast = readSlice(idx = bIdxStop, pos = dstPos, start = 0, stop = lastStop)
      txn       ::= futLast
      dstPos     += lastStop
      bIdx       += 1
    }

    import fileSystem.executionContext

    // wrap up
    val allUpdates  = Future.sequence(txn)

    allUpdates.map { _ =>
      assert (dst.position() + readLen == dstPos, s"${dst.position()} + $readLen != $dstPos")
      (dst: Buffer).position(dstPos)
      _position = posStop
      readLen
    } .andThen { case _ =>
      reachedTarget()
    }
  }

  private def flush()(implicit store: IDBObjectStore): Unit = {
    log.debug("flush()")
    val now       = System.currentTimeMillis()
    _state        = 2
    val metaNow   = meta0.copy(info = meta0.info.copy(size = _size, lastModified = now))
    val futFlush  = writeMeta(metaNow)

    import fileSystem.executionContext

    val futFlushU = futFlush.andThen { case _ =>
      log.debug(s"flush() complete ${_state} -> ${_targetState}")
      _state = _targetState
      _dirty = false
    }
    _closedPr.completeWith(futFlushU)
  }

  private def reachedTarget()(implicit store: IDBObjectStore): Unit = {
    log.debug(s"reachedTarget() ${_state} -> ${_targetState}")
    _state = _targetState
    if (_targetState == 3 && _dirty) {
      flush()
    }
  }

  def write(src: ByteBuffer): Future[Int] = {
    if (readOnly) throw new IOException(s"File $path was opened for reading only")
    checkState()

    import fileSystem.executionContext

    val writeLen = src.remaining()
    if (writeLen == 0) return Future.successful(writeLen)

    _state  = 2
    _dirty  = true

    // prepare
    val posStart  = _position
    val posStop   = posStart + writeLen
    val bIdxStart = blockIndex  (posStart )
    val bOffStart = blockOffset (posStart )
    val bIdxStop  = blockIndex  (posStop  )
    val bOffStop  = blockOffset (posStop  )
    val firstStop = if (bOffStart == 0) 0 else if (bIdxStop > bIdxStart) blockSize else bOffStop
    val lastStop  = if (firstStop == 0 || bIdxStop > bIdxStart) bOffStop else 0

    log.debug(s"write([pos ${src.position()}, rem ${src.remaining()}])")
    log.debug(s"  posStart $posStart; posStop $posStop; bIdxStart $bIdxStart; bOffStart $bOffStart; bIdxStop $bIdxStop bOffStop $bOffStop")

    val db  = fileSystem.db
    val tx  = db.transaction(STORES_FILES, mode = READ_WRITE)
    implicit val store: IDBObjectStore = tx.objectStore(STORE_FILES)

    // N.B. do not use outer variables here as this runs in
    // a future callback
    def nextSlice(pos: Int, n: Int, copy: Boolean): Int8Array = {
      log.debug(s"nextSlice($pos, $n, $copy)")

//      // Tests conducted show that using the typed-array
//      // has no performance advantage
//      import jsta.TypedArrayBufferOps._
//      if (src.hasTypedArray()) { // most efficient
//        val srcBack = src.typedArray()
//        // N.B.: Chromium stores the entire buffer contents,
//        // irrespective of `subarray` usage. Therefore, we must
//        // always copy into a buffer to the exact size!
//        val bufNew = new jsta.ArrayBuffer(n)
//        val arrNew = new Int8Array(bufNew)
//        val srcSub = srcBack.subarray(pos, pos + n)
//        arrNew.set(srcSub)
//        arrNew
//
//      } else {

        // XXX TODO: we can save the extra copying
        // if in the case of `copy || n < blockSize)`, we
        // create the new array first and use it inside the `while` loop

        var i = 0
        var j = pos
        val _swap = swapTArray
        while (i < n) {
          _swap(i) = src.get(j) // XXX TODO is there no better way?
          i += 1; j += 1
        }
        if (copy || n < blockSize) {
          val bufNew  = new jsta.ArrayBuffer(n)
          val arrNew  = new Int8Array(bufNew)
          val swapSub = if (n == blockSize) _swap else _swap.subarray(0, n)
          arrNew.set(swapSub)
          arrNew
        } else {
          _swap
        }

//      }
    }

    // XXX TODO: the reqRead should set the cacheBlock

    def updateSlice(idx: Int, pos: Int, start: Int, stop: Int): Future[Unit] = {
      log.debug(s"updateSlice($idx, $start, $stop)")

      import fileSystem.executionContext

      val reqRead = store.get(key(idx))
      val futArr  = reqToFuture(reqRead) { _ =>
//        val arrOld    = reqRead.result.asInstanceOf[Int8Array]
        val arrOld    = {
          val b = reqRead.result.asInstanceOf[jsta.ArrayBuffer]
          new Int8Array(b)
        }
        val arrNew    = if (arrOld.length >= stop) arrOld else {
          val bufNew  = new jsta.ArrayBuffer(stop)
          val a       = new Int8Array(bufNew)
          a.set(arrOld, 0)
          a
        }
        val arrSrc = nextSlice(pos = pos, n = stop - start, copy = false)
        arrNew.set(arrSrc, start)
        arrNew
      }

      futArr.flatMap { arrNew =>
        val reqWrite = store.put(key = key(idx), value = arrNew.buffer)
        reqToFuture(reqWrite)(_ => ())
      }
    }

    var txn       = List.empty[Future[Unit]]
    var bIdx      = bIdxStart
    var srcPos    = src.position()

    // first block needs to update existing data
    if (firstStop > 0) {
      // by definition (non-block aligned offset), there must be an old block
      val futFirst = updateSlice(idx = bIdxStart, pos = srcPos, start = bOffStart, stop = firstStop)
      txn        ::= futFirst
      bIdx        += 1
      srcPos      += firstStop - bOffStart
    }

    // "middle" blocks are put directly
    while (bIdx < bIdxStop) {
      val arrNew    = nextSlice(pos = srcPos, n = blockSize, copy = true)
      val reqWrite  = store.put(key = key(bIdx), value = arrNew.buffer)
      val futMid    = reqToFuture(reqWrite)(_ => ())
      // according to spec, it is allowed to send multiple write requests at once
      txn          ::= futMid
      srcPos        += blockSize
      bIdx          += 1
    }

    // last block
    if (lastStop > 0) {
      val hasOld  = _size > posStop - lastStop
      val futLast = if (hasOld) {
        updateSlice(idx = bIdxStop, pos = srcPos, start = 0, stop = lastStop)
      } else {
        val arrNew    = nextSlice(srcPos, lastStop, copy = true)
        val reqWrite  = store.put(key = key(bIdxStop), value = arrNew.buffer)
        reqToFuture(reqWrite)(_ => ())
      }
      txn       ::= futLast
      srcPos     += lastStop
      bIdx       += 1
    }

    // wrap up
    val allUpdates  = Future.sequence(txn)
    val newSize     = if (posStop > _size) posStop else _size

    val futCommit = allUpdates

//    val futCommit = allUpdates.flatMap { _ =>
//      // XXX TODO we may want to do this less frequently
//      val now = System.currentTimeMillis()
//      writeMeta(path, Meta(blockSize = blockSize, length = newSize, lastModified = now))
//    }

    futCommit.map { _ =>
      assert (src.position() + writeLen == srcPos, s"${src.position()} + $writeLen != $srcPos")
      (src: Buffer).position(srcPos)
      _position = posStop
      _size     = newSize
      writeLen
    } .andThen { case _ =>
      reachedTarget()
    }
  }

  def close(): Future[Unit] =
    _state match {
      case 0 =>
        if (_dirty) {
          _targetState = 3
          val db = fileSystem.db
          val tx = db.transaction(STORES_FILES, mode = READ_WRITE)
          implicit val store: IDBObjectStore = tx.objectStore(STORE_FILES)
          flush()
          _closedPr.future
        } else {
          _state = 3
          Future.unit
        }

      case 1 | 2  =>
        _targetState = 3
        _closedPr.future

      case 3 =>
        Future.unit
    }

  def isOpen: Boolean =
    !(_state == 3 || _targetState == 3)
}
