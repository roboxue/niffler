package com.roboxue.decanlp
import java.io.File
import java.net.URL
import java.nio.file.Path

import org.apache.commons.io.FileUtils

object DownloadUtils {
  def downloadFromGoogleDrive(from: String, destination: File, overwrite: Boolean = false): File = {
    if (!overwrite && destination.exists()) {
      // don't overwrite
    } else {
      val r1 = requests.get(from)
      r1.cookies.filterKeys(_.startsWith("download_warning")).headOption match {
        case Some(confirm) =>
          val r2 = requests.get(
            s"$from&confirm=${confirm._2.getValue}",
            cookieValues = Map("NID" -> r1.cookies("NID").getValue, confirm._1 -> confirm._2.getValue),
            autoDecompress = false
          )
          FileUtils.writeByteArrayToFile(destination, r2.contents)
        case None =>
          FileUtils.writeByteArrayToFile(destination, r1.contents)
      }
    }
    destination
  }

  def downloadOneFile(from: String, relativeFileName: String, overwrite: Boolean = false): Path => File = { path =>
    {
      val dest = path.resolve(relativeFileName).toFile
      if (!overwrite && dest.exists()) {
        // don't overwrite
      } else {
        FileUtils.copyURLToFile(new URL(from), dest)
      }
      dest
    }
  }
}
