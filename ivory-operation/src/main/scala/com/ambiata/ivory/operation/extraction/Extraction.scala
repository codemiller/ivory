package com.ambiata.ivory.operation.extraction

import argonaut._
import com.ambiata.ivory.core._
import com.ambiata.ivory.storage.control._
import com.ambiata.ivory.storage.manifest._
import com.ambiata.ivory.operation.extraction.output._
import com.ambiata.mundane.control._
import com.ambiata.mundane.io._

import scalaz._, Scalaz._, effect.IO

object Extraction {

  def extract(formats: OutputFormats, input: IvoryLocation, dictionary: Dictionary): RepositoryTIO[Unit] = RepositoryT.fromResultTIO(repository =>
    formats.outputs.traverse {
      case (DenseFormat(format), output) =>
        println(s"Storing extracted data '$input' to '${output.show}'")
        GroupByEntityOutput.createWithDictionary(repository, input, output, dictionary, format match {
          case DelimitedFile(delim) => GroupByEntityFormat.DenseText(delim, formats.missingValue)
          case ThriftFile           => GroupByEntityFormat.DenseThrift
        })
      case (SparseFormat(ThriftFile), output) =>
        println(s"Storing extracted data '$input' to '${output.show}'")
        GroupByEntityOutput.createWithDictionary(repository, input, output, dictionary, GroupByEntityFormat.SparseThrift)
      case (SparseFormat(DelimitedFile(delim)), output) =>
        println(s"Storing extracted data '$input' to '${output.show}'")
        SparseOutput.extractWithDictionary(repository, input, output, dictionary, delim, formats.missingValue)
    }.void
  )
}
