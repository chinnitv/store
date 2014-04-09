package com.treode.store.tier

import java.nio.file.Paths

import com.treode.async.{AsyncImplicits, CallbackCaptor, StubScheduler}
import com.treode.async.io.{File, StubFile}
import com.treode.disk.{Disks, DisksConfig, DiskGeometry}
import com.treode.store.{Fruits, StoreConfig}
import com.treode.pickle.Picklers
import org.scalatest.FreeSpec

import AsyncImplicits._
import Fruits.{Grape, Kiwi, Orange}
import TierTable.Meta
import TierTestTools._

class SynthTableSpec extends FreeSpec {

  val tier = TierDescriptor (0x56)

  private def mkTable (disk: File) (
      implicit scheduler: StubScheduler): SynthTable = {

    implicit val disksConfig = TestDisksConfig()
    implicit val recovery = Disks.recover()
    val geometry = TestDiskGeometry()
    implicit val launch = recovery.attach (Seq ((Paths.get ("a"), disk, geometry))) .pass
    implicit val disks = launch.disks
    launch.launch()
    implicit val storeConfig = StoreConfig (4, 1<<12)
    SynthTable (tier, 0x62)
  }

  private def aNonEmptyTable (setup: StubScheduler => SynthTable) {

    "iterate and get those values" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.check (Kiwi##21::3, Kiwi##14::2, Kiwi##7::1)
    }

    "put a new key before existing keys" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.putCells (Grape##7::11)
      table.check (Grape##7::11, Kiwi##21::3, Kiwi##14::2, Kiwi##7::1)
    }

    "put a new key after existing keys" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.putCells (Orange##7::11)
      table.check (Kiwi##21::3, Kiwi##14::2, Kiwi##7::1, Orange##7::11)
    }

    "put an existing key at a time later than exsiting times" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.putCells (Kiwi##28::11)
      table.check (Kiwi##28::11, Kiwi##21::3, Kiwi##14::2, Kiwi##7::1)
    }

    "put an existing key at a time earlier than existing times" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.putCells (Kiwi##1::11)
      table.check (Kiwi##21::3, Kiwi##14::2, Kiwi##7::1, Kiwi##1::11)
    }

    "put an existing key at a time between two existing times" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.putCells (Kiwi##11::11)
      table.check (Kiwi##21::3, Kiwi##14::2, Kiwi##11::11, Kiwi##7::1)
    }

    "put an existing key over an existing time" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.putCells (Kiwi##14::11)
      table.check (Kiwi##21::3, Kiwi##14::11, Kiwi##7::1)
    }

    "delete a new key before existing keys" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.deleteCells (Grape##7)
      table.check (Grape##7, Kiwi##21::3, Kiwi##14::2, Kiwi##7::1)
    }

    "delete a new key after existing keys" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.deleteCells (Orange##7)
      table.check (Kiwi##21::3, Kiwi##14::2, Kiwi##7::1, Orange##7)
    }

    "delete an existing key at a time later than exsiting times" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.deleteCells (Kiwi##28)
      table.check (Kiwi##28, Kiwi##21::3, Kiwi##14::2, Kiwi##7::1)
    }

    "delete an existing key at a time earlier than existing times" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.deleteCells (Kiwi##1)
      table.check (Kiwi##21::3, Kiwi##14::2, Kiwi##7::1, Kiwi##1)
    }

    "delete an existing key at a time between two existing times" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.deleteCells (Kiwi##11)
      table.check (Kiwi##21::3, Kiwi##14::2, Kiwi##11, Kiwi##7::1)
    }

    "delete an existing key over an existing time" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.deleteCells (Kiwi##14)
      table.check (Kiwi##21::3, Kiwi##14, Kiwi##7::1)
    }}

  private def aCheckpointedTable (setup: StubScheduler => SynthTable) {
    "handle a checkpoint" in {
      implicit val scheduler = StubScheduler.random()
      val table = setup (scheduler)
      table.checkpoint() .pass
      assert (table.primary.isEmpty)
      assert (table.secondary.isEmpty)
      assert (!table.tiers.isEmpty)
      table.check (Kiwi##21::3, Kiwi##14::2, Kiwi##7::1)
    }}

  private def aCheckpointingTable (
      setup: StubScheduler => (StubFile, SynthTable, CallbackCaptor [Meta])) {

    "finish the checkpoint" in {
      implicit val scheduler = StubScheduler.random()
      val (disk, table, cb) = setup (scheduler)
      disk.last.pass()
      scheduler.runTasks()
      cb.passed
      assert (table.secondary.isEmpty)
      assert (!table.tiers.isEmpty)
      table.check (Kiwi##21::3, Kiwi##14::2, Kiwi##7::1)
    }}

  "When a SynthTable has" - {

    "only empty tiers, it should" - {

      def setup () (implicit scheduler: StubScheduler): SynthTable = {
        val disk = new StubFile
        val table = mkTable (disk)
        assert (table.primary.isEmpty)
        assert (table.secondary.isEmpty)
        assert (table.tiers.isEmpty)
        table
      }

      "iterate no values" in {
        implicit val scheduler = StubScheduler.random()
        val table = setup()
        table.check ()
      }

      "handle a checkpoint" in {
        implicit val scheduler = StubScheduler.random()
        val table = setup()
        table.checkpoint() .pass
        assert (table.primary.isEmpty)
        assert (table.secondary.isEmpty)
        assert (!table.tiers.isEmpty)
        table.check ()
      }

      "handle a put" in {
        implicit val scheduler = StubScheduler.random()
        val table = setup()
        table.putCells (Kiwi##7::1)
        assert (!table.primary.isEmpty)
        assert (table.secondary.isEmpty)
        assert (table.tiers.isEmpty)
        table.check (Kiwi##7::1)
      }

      "handle a delete" in {
        implicit val scheduler = StubScheduler.random()
        val table = setup()
        table.deleteCells (Kiwi##1)
        assert (!table.primary.isEmpty)
        assert (table.secondary.isEmpty)
        assert (table.tiers.isEmpty)
        table.check (Kiwi##1)
      }}

    "a non-empty primary tier, it should" - {

      def setup () (implicit scheduler: StubScheduler): SynthTable = {
        val disk = new StubFile
        val table = mkTable (disk)
        table.putCells (Kiwi##7::1, Kiwi##14::2, Kiwi##21::3)
        assert (!table.primary.isEmpty)
        assert (table.secondary.isEmpty)
        assert (table.tiers.isEmpty)
        table
      }

      behave like aCheckpointedTable (s => setup () (s))

      behave like aNonEmptyTable (s => setup () (s))
    }

    "a non-empty secondary tier, it should" - {

      def setup () (implicit scheduler: StubScheduler) = {
        val disk = new StubFile
        val table = mkTable (disk)
        table.putCells (Kiwi##7::1, Kiwi##14::2, Kiwi##21::3)
        disk.stop = true
        val cb = table.checkpoint() .capture()
        scheduler.runTasks()
        cb.assertNotInvoked()
        assert (table.primary.isEmpty)
        assert (!table.secondary.isEmpty)
        assert (table.tiers.isEmpty)
        disk.stop = false
        (disk, table, cb)
      }

      behave like aCheckpointingTable (s => setup () (s))

      behave like aNonEmptyTable (s => setup () (s) ._2)
    }

    "a non-empty primary and secondary tier, it should" - {

      def setup () (implicit scheduler: StubScheduler) = {
        val disk = new StubFile
        val table = mkTable (disk)
        table.putCells (Kiwi##7::1, Kiwi##14::2)
        disk.stop = true
        val cb = table.checkpoint() .capture()
        scheduler.runTasks()
        cb.assertNotInvoked()
        disk.stop = false
        table.putCells (Kiwi##21::3)
        assert (!table.primary.isEmpty)
        assert (!table.secondary.isEmpty)
        assert (table.tiers.isEmpty)
        (disk, table, cb)
      }

      behave like aCheckpointingTable (s => setup () (s))

      behave like aNonEmptyTable (s => setup () (s) ._2)
    }

    "non-empty tertiary tiers, it should" - {

      def setup () (implicit scheduler: StubScheduler): SynthTable = {
        val disk = new StubFile
        val table = mkTable (disk)
        table.putCells (Kiwi##7::1, Kiwi##14::2, Kiwi##21::3)
        table.checkpoint() .pass
        assert (table.primary.isEmpty)
        assert (table.secondary.isEmpty)
        assert (!table.tiers.isEmpty)
        table
      }

      behave like aCheckpointedTable (s => setup () (s))

      behave like aNonEmptyTable (s => setup () (s))
    }

    "non-empty primary and tertiary tiers, it should" - {

      def setup () (implicit scheduler: StubScheduler): SynthTable = {
        val disk = new StubFile
        val table = mkTable (disk)
        table.putCells (Kiwi##7::1, Kiwi##14::2)
        table.checkpoint() .pass
        table.putCells (Kiwi##21::3)
        assert (!table.primary.isEmpty)
        assert (table.secondary.isEmpty)
        assert (!table.tiers.isEmpty)
        table
      }

      behave like aCheckpointedTable (s => setup () (s))

      behave like aNonEmptyTable (s => setup () (s))
    }}}
