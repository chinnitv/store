package com.treode.disk

import com.treode.async.{Callback, callback, delay}

class SegmentCleaner (disks: Map [Int, DiskDrive], pages: PageRegistry) {

  type Groups = Map [TypeId, Set [PageGroup]]

  val minLivePercentage = 0.9
  val liveByteRange = 1 << 12

  def threshold (alloc: IntSet): Boolean =
    disks.values.flatMap (_.allocated.iterator) .take (3) .size == 3

  def union (maps: Seq [Groups]): Groups = {
    var result = Map.empty [TypeId, Set [PageGroup]]
    for {
      groups <- maps
      (id, gs1) <- groups
    } {
      result.get (id) match {
        case Some (gs0) => result += (id -> (gs0 ++ gs1))
        case None => result += (id -> gs1)
      }}
    result
  }

  def compact (groups: Groups, cb: Callback [Unit]) {
    val latch = Callback.latch (groups.size, cb)
    for ((id, gs) <- groups)
      pages.compact (id, gs, latch)
  }

  def probe (cb: Callback [List [(Segment, SegmentMap)]]) {

    var diskIter = disks.values.iterator
    if (!diskIter.hasNext) {
      cb (List.empty)
      return
    }
    var disk = diskIter.next

    var allocIter = disk.allocated.iterator
    while (!allocIter.hasNext && diskIter.hasNext) {
      disk = diskIter.next
      allocIter = disk.allocated.iterator
    }
    if (!allocIter.hasNext) {
      cb (List.empty)
      return
    }
    var alloc = allocIter.next

    var seg: Segment = null

    val loop = new Callback [SegmentMap] {

      var map: SegmentMap = null
      var min = disk.config.segmentBytes * minLivePercentage
      var cut = min
      var target = List.empty [(Segment, SegmentMap, Long)]

      val pagesProbed = delay (cb) { live: Long =>
        if (live < min) {
          min = live
          cut = live + liveByteRange
          target = target.filter (_._3 < cut)
          target ::= (seg, map, live)
        } else if (live < cut) {
          target ::= (seg, map, live)
        }
        while (!allocIter.hasNext && diskIter.hasNext) {
          disk = diskIter.next
          min = disk.config.segmentBytes * 0.9
          cut = min
          allocIter = disk.allocated.iterator
        }
        if (allocIter.hasNext) {
          alloc = allocIter.next
          seg = disk.config.segment (alloc)
          SegmentMap.read (disk.file, seg.pos, this)
        } else {
          cb (target.map (v => (v._1, v._2)))
        }}

      def pass (_map: SegmentMap) {
        this.map = map
        map.probe (pages, pagesProbed)
      }

      def fail (t: Throwable) = cb.fail (t)
    }

    seg = disk.config.segment (alloc)
    SegmentMap.read (disk.file, seg.pos, loop)
  }

  def clean (cb: Callback [Boolean]) {
    probe (delay (cb) { segments =>
      val groups = union (segments map (_._2.groups))
      compact (groups, callback (cb) { _ =>
        // TODO: free segment via epoch
        !groups.isEmpty
      })
    })
  }

}
