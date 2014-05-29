package org.wololo.gdrivesync

import com.google.api.services.drive.model.File
import com.typesafe.scalalogging.slf4j.LazyLogging
import java.nio.file.Path
import java.nio.file.Files
import com.google.api.services.drive.model.ParentReference
import scala.collection.JavaConverters._
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive

class LocalMetaFetcher(implicit val drive: Drive, implicit val localMetaStore: LocalMetaStore) extends LazyLogging {
  /*def detectMimeType(file: java.io.File) = {
    val tika = new TikaConfig()
    val metadata = new Metadata()
    metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, file.getName())
    val mimetype = tika.getDetector.detect(TikaInputStream.get(file), metadata)
    logger.debug("Detected mimetype: " + mimetype)
    mimetype.getType()
  }*/

  def findOrCreate(folder: SyncFile, file: java.io.File) = {
    folder.childAtPath(file) match {
      case Some(syncFile) => syncFile
      case None => {
        //logger.debug("Creating new SyncFile for path " + file)
        val driveFile = new File()
        driveFile.setTitle(file.getName())
        //driveFile.setDescription()
        driveFile.setModifiedDate(new DateTime(file.lastModified))
        if (file.isDirectory()) {
          driveFile.setMimeType("application/vnd.google-apps.folder");
        } else {
          driveFile.setMimeType(Files.probeContentType(file.toPath()));
        }
        driveFile.setParents(List(new ParentReference().setId(folder.driveFile.getId())).asJava)
        val syncFile = new SyncFile(file, driveFile, drive, localMetaStore)
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