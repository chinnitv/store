package com.treode.store.disk2

import java.util.ArrayList
import com.treode.async.{Callback, Scheduler}
import com.treode.buffer.PagedBuffer
import com.treode.pickle.Pickler

private class PageDispatcher (scheduler: Scheduler) {

  private val dsp = new Dispatcher [PickledPage] (scheduler)

  def write [G, P] (desc: PageDescriptor [G, P], group: G, page: P, cb: Callback [Position]): Unit =
    dsp.send (PickledPage (desc, group, page, cb))

  def engage (writer: PageWriter): Unit =
    dsp.receive (writer.receiver)

  def replace (rejects: ArrayList [PickledPage]): Unit =
    dsp.replace (rejects)
}
