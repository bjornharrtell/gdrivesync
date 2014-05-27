package org.wololo.gdrivesync2

import java.sql.DriverManager
import com.typesafe.scalalogging.slf4j.LazyLogging
import Globals.SYNC_STORE_DIR
import com.google.api.services.drive.Drive
import Globals._

object GDriveSync2 extends App with LazyLogging {

  implicit val credential = GoogleOAuth2.authorize
  
  implicit def drive = {
    val builder = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
    builder.setApplicationName("GDriveSync")
    builder.build
  }
  
  if (SYNC_STORE_DIR.exists()) {
    val root = new DriveMetaFetcher().fetchRoot

    LocalMetaFetcher.findLocalMeta(root)

    root.sync
  } else {
    logger.error("Destination sync directory does not exists, aborting and clearing metadata.")
  }

  DriverManager.getConnection(
    "jdbc:hsqldb:file:" + Globals.DATA_STORE_DIR.getPath() + "/metadb", "SA", "");

  // TODO: implement local metastore, local filesystem meta recurse, sync
}