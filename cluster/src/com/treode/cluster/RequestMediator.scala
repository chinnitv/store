package com.treode.cluster

import com.treode.pickle.Pickler

class RequestMediator [A] private [cluster] (prsp: Pickler [A], mbx: MailboxId, peer: Peer) {

  def respond (rsp: A): Unit =
    peer.send (prsp, mbx, rsp)

  override def toString = "RequestMediator" + (mbx, peer)
}
