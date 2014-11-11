package com.ambiata.ivory.storage.version

case class IvoryVersion(version: String)

object IvoryVersion {
  def current: IvoryVersion =
    IvoryVersion(com.ambiata.ivory.core.BuildInfo.version)
}
