package org.wololo.gdrivesync2

import java.sql.DriverManager
import com.google.api.services.drive.Drive
import com.typesafe.scalalogging.slf4j.LazyLogging
import Globals.JSON_FACTORY
import Globals.SYNC_STORE_DIR
import Globals.httpTransport
import Globals.DATA_STORE_DIR 
import org.mapdb.DBMaker

object GDriveSync2 extends App with LazyLogging {
  
  // TODO: handle args perhaps with http://felix.apache.org/site/61-extending-the-console.html
  
  implicit val credential = GoogleOAuth2.authorize
  
  implicit def drive = {
    val builder = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
    builder.setApplicationName("GDriveSync")
    builder.build
  }
  
  implicit val localMetaStore = new LocalMetaStore()
  
  if (SYNC_STORE_DIR.exists) {
    val root = new DriveMetaFetcher().fetchRoot

    new LocalMetaFetcher().findLocalMeta(root)

    root.sync
  } else {
    logger.error("Destination sync directory does not exists, aborting and clearing metadata.")
    localMetaStore.clear
  }
  
}