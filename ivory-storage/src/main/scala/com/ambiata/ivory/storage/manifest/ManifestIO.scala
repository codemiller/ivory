package com.ambiata.ivory.storage.manifest

import com.ambiata.ivory.core._
import com.ambiata.mundane.io._
import com.ambiata.notion.core._

object ManifestIO {
  def location(repository: Repository, key: Key): IvoryLocation =
    repository.toIvoryLocation(key) </> FileName.unsafe(".manifest.json")
}
