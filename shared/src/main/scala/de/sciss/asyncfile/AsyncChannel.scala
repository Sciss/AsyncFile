/*
 *  Channel.scala
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

import scala.concurrent.Future

//trait Channel extends Closeable {
//  /** Whether or not this channel is still open. */
//  def isOpen: Boolean
//}

trait AsyncChannel {
  /** When a file is opened, this reports `true`. As soon as `close`
    * is called, this method reports `false`, even if the asynchronous
    * closure is still going on. At this point no further reads or writes
    * should be made.
    */
  def isOpen: Boolean

  /** It is allowed to call this method even if there is an ongoing previous closure process.
    * In such case the future of the ongoing closure is returned.
    */
  def close(): Future[Unit]
}