package com.treode.disk

import scala.reflect.ClassTag
import com.treode.async.Callback
import com.treode.pickle.Pickler

class PageDescriptor [G, P] (val id: TypeId, val pgrp: Pickler [G], val ppag: Pickler [P]) (
    implicit val tpag: ClassTag [P]) {

  def read (reload: Reload, pos: Position, cb: Callback [P]): Unit =
    reload.read (this, pos, cb)

  def read (launch: Launch, pos: Position, cb: Callback [P]): Unit =
    launch.read (this, pos, cb)

  def handle (handler: PageHandler [G]) (implicit launch: Launch): Unit =
    launch.handle (this, handler)

  def read (pos: Position, cb: Callback [P]) (implicit disks: Disks): Unit =
    disks.read (this, pos, cb)

  def write (group: G, page: P, cb: Callback [Position]) (implicit disks: Disks): Unit =
    disks.write (this, group, page, cb)

  override def toString = s"PageDescriptor($id)"
}
