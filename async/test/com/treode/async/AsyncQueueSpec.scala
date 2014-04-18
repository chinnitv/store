package com.treode.async

import java.util.ArrayDeque

import org.scalatest.FlatSpec

import Async.{async, supply}
import AsyncImplicits._
import AsyncTestTools._
import Callback.{ignore => disregard}

class AsyncQueueSpec extends FlatSpec {

  class DistinguishedException extends Exception

  class AsyncCaptor [A] {

    private var cb: Callback [A] = null

    def start(): Async [A] =
      async { cb =>
        require (this.cb == null)
        this.cb = cb
      }

    def pass (v: A) {
      require (this.cb != null)
      val cb = this.cb
      this.cb = null
      cb.pass (v)
    }

    def fail (t: Throwable) {
      require (this.cb != null)
      val cb = this.cb
      this.cb = null
      cb.fail (t)
    }}

  class TestQueue (implicit scheduler: StubScheduler) {

    val fiber = new Fiber (scheduler)
    val queue = AsyncQueue (fiber) (next())
    var callbacks = new ArrayDeque [Callback [Unit]]
    var captor = new AsyncCaptor [Unit]

    queue.launch()

    def next(): Option [Runnable] = {
      if (callbacks.isEmpty) {
        None
      } else {
        queue.run (callbacks.remove()) (captor.start())
      }}

    def start(): CallbackCaptor [Unit] = {
      val cb = queue.async [Unit] (cb => callbacks.add (cb)) .capture()
      scheduler.runTasks()
      cb
    }

    def pass() {
      captor.pass()
      scheduler.runTasks()
    }

    def fail (t: Throwable) {
      captor.fail (t)
      scheduler.runTasks()
    }}

  "An AsyncQueue" should "run one task" in {
    implicit val s = StubScheduler.random()
    val q = new TestQueue
    val cb = q.start()
    cb.assertNotInvoked()
    q.pass()
    cb.passed
  }

  it should "run two queue tasks" in {
    implicit val s = StubScheduler.random()
    val q = new TestQueue
    val cb1 = q.start()
    val cb2 = q.start()
    cb1.assertNotInvoked()
    cb2.assertNotInvoked()
    q.pass()
    cb1.passed
    cb2.assertNotInvoked()
    q.pass()
    cb2.passed
  }

  it should "run two tasks one after the other" in {
    implicit val s = StubScheduler.random()
    val q = new TestQueue
    val cb1 = q.start()
    cb1.assertNotInvoked()
    q.pass()
    cb1.passed
    val cb2 = q.start()
    cb2.assertNotInvoked()
    q.pass()
    cb2.passed
  }

  it should "report an exception through the callback and continue" in {
    implicit val s = StubScheduler.random()
    val q = new TestQueue
    val cb1 = q.start()
    val cb2 = q.start()
    cb1.assertNotInvoked()
    cb2.assertNotInvoked()
    q.fail (new DistinguishedException)
    cb1.failed [DistinguishedException]
    cb2.assertNotInvoked()
    q.pass()
    cb2.passed
  }}