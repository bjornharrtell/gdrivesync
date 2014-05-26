package org.wololo.gdrivesync2

import org.apache.tika.config.TikaConfig
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaMetadataKeys

import com.google.api.services.drive.model.File
import com.typesafe.scalalogging.slf4j.LazyLogging

object LocalMetaFetcher extends LazyLogging {
  def detectMimeType(file: java.io.File) = {
    val tika = new TikaConfig()
    val metadata = new Metadata()
    metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, file.getName())
    val mimetype = tika.getDetector.detect(TikaInputStream.get(file), metadata)
    logger.debug("Detected mimetype: " + mimetype)
    mimetype.getType()
  }

  def findOrCreate(folder: SyncFile, file: java.io.File) = {
    folder.childAtPath(file) match {
      case Some(syncFile) => syncFile
      case None => {
        logger.debug("Creating new SyncFile for path " + file)
        val driveFile = new File()
        driveFile.setTitle(file.getName())
        //driveFile.setDescription()
        if (file.isDirectory()) {
          driveFile.setMimeType("application/vnd.google-apps.folder");
        } else {
          driveFile.setMimeType(detectMimeType(file));
        }
        val syncFile = new SyncFile(file, driveFile)
        folder.children += syncFile
        syncFile
      }
    }
  }

  def findLocalMeta(folder: SyncFile) {
    //logger.debug("Search local file meta at " + folder.localFile.getPath())
    var files = folder.localFile.listFiles
    //logger.debug("Found " + files.length + " files")
    files.foreach(file => {
      val syncFile = findOrCreate(folder, file)
      if (file.isDirectory()) findLocalMeta(syncFile)
    })
  }
}