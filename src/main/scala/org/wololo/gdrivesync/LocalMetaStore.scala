package org.wololo.gdrivesync

import org.mapdb.DBMaker
import Globals.DATA_STORE_DIR
import Globals.SYNC_STORE_DIR

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
  
  def relativePath(path: String) = path.substring(SYNC_STORE_DIR.getPath.length)

  def add(path: String) = {
    set.add(relativePath(path))
    db.commit
  }

  def remove(path: String) = {
    set.remove(relativePath(path))
    db.commit
  }

  def contains(path: String) = {
    set.contains(relativePath(path))
  }
}