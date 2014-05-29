package org.wololo.gdrivesync

import java.sql.DriverManager
import com.google.api.services.drive.Drive
import com.typesafe.scalalogging.slf4j.LazyLogging
import Globals.JSON_FACTORY
import Globals.SYNC_STORE_DIR
import Globals.httpTransport
import Globals.DATA_STORE_DIR
import org.mapdb.DBMaker
import org.clapper.argot._
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import org.slf4j.Logger

object GDriveSync extends App with LazyLogging {

  implicit val localMetaStore = new LocalMetaStore()

  import ArgotConverters._
  val parser = new ArgotParser("gdrivesync")
  val dest = parser.option[String](List("d"), "PATH", "Directory to represent root of Google Drive when syncing (defaults to ~/gdrive)")
  val quiet = parser.flag[Boolean](List("q"), "Produce no output")
  val clear = parser.flag[Boolean](List("c"), "Clear metadata store")
  val help = parser.flag[Boolean](List("help"), "Show usage")
  
  try {
    parser.parse(args)
    if (help.value.isDefined) {
      parser.usage
      System.exit(0)
    }
    if (clear.value.isDefined) {
      logger.info("Clearing metadata")
      localMetaStore.clear
      System.exit(0)
    }
    if (quiet.value.isDefined) {
        LoggerFactory
        	.getLogger(Logger.ROOT_LOGGER_NAME)
        	.asInstanceOf[ch.qos.logback.classic.Logger]
            .setLevel(Level.OFF)
    }
    if (dest.value.isDefined) {
      Globals.SYNC_STORE_DIR = new java.io.File(dest.value.get)
    }
  } catch {
    case e: ArgotUsageException => {
      println(e.message)
      System.exit(0)
    }
  }

  implicit val drive = new Drive.Builder(httpTransport, JSON_FACTORY, GoogleOAuth2.authorize)
    .setApplicationName("GDriveSync")
    .build

  if (SYNC_STORE_DIR.exists) {
    val root = new DriveMetaFetcher().fetchRoot
    new LocalMetaFetcher().findLocalMeta(root)
    root.sync
  } else {
    logger.error("Destination sync directory does not exists, aborting and clearing metadata.")
    localMetaStore.clear
  }

}