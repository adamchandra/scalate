/**
 * Copyright (C) 2009-2010 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.scalate.support

import io.Source
import java.io._
import org.fusesource.scalate.util.{IOUtil, Logging}
import java.net.{URISyntaxException, URL}

/**
 * Represents a string, file or URI based resource
 */
trait Resource extends Logging {

  /**
   * Returns the URI of the resource
   */
  def uri: String

  /**
   * Returns the text content of the resource
   */
  def text: String = IOUtil.loadText(inputStream)

  /**
   * Returns the reader of the content of the resource
   */
  def reader: Reader = new InputStreamReader(inputStream)

  /**
   * Returns the input stream of the content of the resource
   */
  def inputStream: InputStream

  /**
   * Returns the last modified time of the resource
   */
  def lastModified: Long

  def toFile: Option[File] = None
}

/**
 * Not all resources are writeable so this optional trait is for those
 */
trait WriteableResource extends Resource {
  /**
   * Writes text to the resource replacing its previous content
   */
  def text_=(value: String): Unit = IOUtil.writeText(outputStream, value)

  /**
   * Returns the output stream of the resource
   */
  def outputStream: OutputStream

  /**
   * Returns the writer to the content of the resource
   */
  def writer: Writer = new OutputStreamWriter(outputStream)

}

abstract class TextResource extends Resource {
  override def reader = new StringReader(text)

  def inputStream = new ByteArrayInputStream(text.getBytes)

  // just return current time as we have no way to know
  def lastModified: Long = System.currentTimeMillis
}

case class StringResource(uri: String, override val text: String) extends TextResource

case class UriResource(override val uri: String, resourceLoader: ResourceLoader) extends DelegateResource {
  protected def delegate = resourceLoader.resourceOrFail(uri)
}

case class FileResource(file: File, uri: String) extends WriteableResource {
  override def text = IOUtil.loadTextFile(file)

  override def reader = new FileReader(file)

  def inputStream = new FileInputStream(file)

  def outputStream = new FileOutputStream(file)

  def lastModified = file.lastModified

  /**
   * Create a child file
   */
  def /(name: String) = new FileResource(new File(file, name), uri + "/" + name)

  implicit def asFile: File = file

  override def toFile = Some(file)
}

case class URLResource(url: URL) extends WriteableResource {
  def uri = url.toExternalForm

  lazy val connection = url.openConnection

  def inputStream = connection.getInputStream

  def outputStream = connection.getOutputStream

  def lastModified = {
    val con = url.openConnection
    con.getLastModified
  }

  override def toFile: Option[File] = {
    var f: File = null
    if (url.getProtocol == "file") {
      try {
        try {
          f = new File(url.toURI)
        } catch {
          case e: URISyntaxException => f = new File(url.getPath)
        }
      } catch {
        case e => debug("While converting " + url + " to a File I caught: " + e, e)
      }
    }
    if (f != null && f.exists && f.isFile) {
      Some(f)
    } else {
      None
    }
  }
}

case class SourceResource(uri: String, source: Source) extends TextResource {
  override def text = {
    val builder = new StringBuilder
    for (c <- source) {
      builder.append(c)
    }
    builder.toString
  }
}

abstract class DelegateResource extends Resource {
  override def uri = delegate.uri

  override def text = delegate.text

  override def reader = delegate.reader

  override def inputStream = delegate.inputStream

  def lastModified = delegate.lastModified

  protected def delegate: Resource
}

/**
 * Helper methods to create a  [[org.fusesource.scalate.support.Resource]] from various sources
 */
object Resource {

  /**
   * Creates a [[org.fusesource.scalate.support.Resource]] from the actual String contents using the given
   * URI.
   *
   * The URI is used to determine the package name to put the template in along with
   * the template kind (using the extension of the URI)
   */
  def fromText(uri: String, templateText: String) = StringResource(uri, templateText)

  /**
   * Creates a [[org.fusesource.scalate.support.Resource]] from a local URI such as in a web application using the
   * class loader to resolve URIs to actual resources
   */
  def fromUri(uri: String, resourceLoader: ResourceLoader) = UriResource(uri, resourceLoader)

  /**
   * Creates a [[org.fusesource.scalate.support.Resource]] from a file
   */
  def fromFile(file: File): FileResource = fromFile(file, file.getPath)
  
  def fromFile(file: File, uri: String): FileResource = FileResource(file, uri)

  /**
   * Creates a [[org.fusesource.scalate.support.Resource]] from a file name
   */
  def fromFile(fileName: String): FileResource = fromFile(new File(fileName))

  /**
   * Creates a [[org.fusesource.scalate.support.Resource]] from a URL
   */
  def fromURL(url: URL): URLResource = URLResource(url)

  /**
   * Creates a [[org.fusesource.scalate.support.Resource]] from a URL
   */
  def fromURL(url: String): URLResource = fromURL(new URL(url))

  /**
   * Creates a [[org.fusesource.scalate.support.Resource]] from the [[scala.io.Source]] and the given URI.
   *
   * The URI is used to determine the package name to put the template in along with
   * the template kind (using the extension of the URI)
   */
  def fromSource(uri: String, source: Source) = SourceResource(uri, source)
}
