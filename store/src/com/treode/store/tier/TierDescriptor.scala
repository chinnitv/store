package com.treode.store.tier

import com.treode.disk.{PageDescriptor, TypeId}
import com.treode.store.StorePicklers
import com.treode.pickle.Pickler

class TierDescriptor [K, V] private (
    val id: TypeId,
    val pkey: Pickler [K],
    val pval: Pickler [V]
) {

  private [tier] val pickler = {
    import StorePicklers._
    tagged [TierPage] (
      0x1 -> IndexPage.pickler,
      0x2 -> CellPage.pickler)
  }

  private [tier] val pager = {
    import StorePicklers._
    PageDescriptor (id, ulong, pickler)
  }}

object TierDescriptor {

  def apply [K, V] (id: TypeId, pkey: Pickler [K], pval: Pickler [V]): TierDescriptor [K, V] =
    new TierDescriptor (id, pkey, pval)
}