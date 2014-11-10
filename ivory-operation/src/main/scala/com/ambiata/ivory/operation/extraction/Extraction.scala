package com.ambiata.ivory.operation.extraction

//import argonaut._
import com.ambiata.ivory.core._
import com.ambiata.ivory.storage.control._
import com.ambiata.ivory.operation.extraction.output._
import com.ambiata.mundane.control._

import scalaz._, Scalaz._, effect.IO

object Extraction {

  def extract(formats: OutputFormats, input: IvoryLocation, dictionary: Dictionary): RepositoryTIO[Unit] = RepositoryT.fromResultTIO(repository =>
    formats.outputs.traverseU({ case (format, output) =>
      ResultT.io(println(s"Storing extracted data '$input' to '${output.show}'")) >> (format match {
        case OutputFormat.Thrift(Form.Sparse) =>
          GroupByEntityOutput.createWithDictionary(repository, input, output, dictionary, GroupByEntityFormat.SparseThrift)
        case OutputFormat.Thrift(Form.Dense) =>
          GroupByEntityOutput.createWithDictionary(repository, input, output, dictionary, GroupByEntityFormat.DenseThrift)
        case OutputFormat.Text(Form.Sparse, delimiter, encoding) =>
          SparseOutput.extractWithDictionary(repository, input, output, dictionary, delimiter.character, formats.missingValue, encoding === EncodedAs.Escaped)
        case OutputFormat.Text(Form.Dense, delimiter, encoding) =>
          GroupByEntityOutput.createWithDictionary(repository, input, output, dictionary, GroupByEntityFormat.DenseText(delimiter.character, formats.missingValue, encoding === EncodedAs.Escaped))
      }) }).void
  )
}
