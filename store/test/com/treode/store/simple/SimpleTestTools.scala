package com.treode.store.simple

import com.treode.async._
import com.treode.store.Bytes
import org.scalatest.Assertions

import Assertions._

private object SimpleTestTools {

  implicit class RichInt (v: Int) {
    def :: (k: Bytes): SimpleCell = SimpleCell (k, Some (Bytes (v)))
  }

  implicit class RichOption (v: Option [Bytes]) {
    def :: (k: Bytes): SimpleCell = SimpleCell (k, v)
  }

  implicit class RichTable (table: SimpleTable) (implicit scheduler: StubScheduler) {

    def getAndPass (key: Int): Option [Int] = {
      val cb = new CallbackCaptor [Option [Bytes]]
      table.get (Bytes (key), cb)
      scheduler.runTasks()
      cb.passed map (_.int)
    }

    def putAll (kvs: (Int, Int)*) {
      for ((key, value) <- kvs)
        table.put (Bytes (key), Bytes (value))
      scheduler.runTasks()
    }

    def deleteAll (ks: Int*) {
      for (key <- ks)
        table.delete (Bytes (key))
      scheduler.runTasks()
    }

    def toMap(): Map [Int, Int] = {
      val builder = Map.newBuilder [Int, Int]
      val cb = new CallbackCaptor [Unit]
      table.iterator (continue (cb) { iter =>
        AsyncIterator.foreach (iter, cb) { case (cell, cb) =>
          invoke (cb) {
            if (cell.value.isDefined)
              builder += cell.key.int -> cell.value.get.int
          }}})
      scheduler.runTasks()
      cb.passed
      builder.result
    }

    def toSeq(): Seq [(Int, Int)] = {
      val builder = Seq.newBuilder [(Int, Int)]
      val cb = new CallbackCaptor [Unit]
      table.iterator (continue (cb) { iter =>
        AsyncIterator.foreach (iter, cb) { case (cell, cb) =>
          invoke (cb) {
            if (cell.value.isDefined)
              builder += cell.key.int -> cell.value.get.int
          }}})
      scheduler.runTasks()
      cb.passed
      builder.result
    }

    def expectNone (key: Int): Unit =
      expectResult (None) (getAndPass (key))

    def expectValue (key: Int, value: Int): Unit =
      expectResult (Some (value)) (getAndPass (key))

    def expectValues (kvs: (Int, Int)*): Unit =
      expectResult (kvs.sorted) (toSeq)
  }

  implicit class RichSynthTable (table: SynthTable) (implicit scheduler: StubScheduler) {

    def checkpointAndPass(): SimpleTable.Meta = {
      val cb = new CallbackCaptor [SimpleTable.Meta]
      table.checkpoint (cb)
      scheduler.runTasks()
      cb.passed
    }}
}
