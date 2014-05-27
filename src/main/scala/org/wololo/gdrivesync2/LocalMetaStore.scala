package org.wololo.gdrivesync2

import org.mapdb.DBMaker
import Globals.DATA_STORE_DIR

class LocalMetaStore {
  var db = DBMaker.newFileDB(new java.io.File(DATA_STORE_DIR, "metadb"))
    .closeOnJvmShutdown
    .make
  val set = db.getHashSet[String]("paths")

  def clear = {
    set.clear
    db.commit
    db.compact
  }

  def add(path: String) = {
    set.add(path)
    db.commit
  }

  def remove(path: String) = {
    set.remove(path)
    db.commit
  }

  def contains(path: String) = {
    set.contains(path)
  }
}