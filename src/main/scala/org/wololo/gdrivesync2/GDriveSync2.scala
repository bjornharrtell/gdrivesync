package org.wololo.gdrivesync2

import com.typesafe.scalalogging.slf4j.LazyLogging
import Globals.SYNC_STORE_DIR
import java.sql.DriverManager

object GDriveSync2 extends App with LazyLogging {
  
  if (SYNC_STORE_DIR.exists()) {
    val credential = GoogleOAuth2.authorize
    val drive = new DriveMetaFetcher(credential)
    val root = drive.fetchRoot
    root.sync
  } else {
    logger.error("Destination sync directory does not exists, aborting and clearing metadata.")
  }
  
  DriverManager.getConnection(
         "jdbc:hsqldb:file:" + Globals.DATA_STORE_DIR.getPath() +  "/metadb", "SA", "");
  
  // TODO: implement local metastore, local filesystem meta recurse, sync
}