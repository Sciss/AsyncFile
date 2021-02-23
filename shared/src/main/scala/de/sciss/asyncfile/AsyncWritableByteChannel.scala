/*
 *  AsyncWritableByteChannel.scala
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

trait AsyncWritableByteChannel extends AsyncReadableByteChannel {
  def write(src: ByteBuffer): Future[Int]
}
