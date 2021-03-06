/**
 * Copyright (C) 2016 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.persistence.relational.search

import org.orbeon.oxf.fr.persistence.relational.Provider._
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.Logger
import org.orbeon.oxf.fr.persistence.relational.search.adt._
import org.orbeon.oxf.util.{NetUtils, ScalaUtils}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.XML._

trait SearchRequest {

  val SearchPath = "/fr/service/([^/]+)/search/([^/]+)/([^/]+)".r


  def httpRequest = NetUtils.getExternalContext.getRequest

  def parseRequest(searchDocument: DocumentInfo): Request = {

    if (Logger.isDebugEnabled)
      Logger.logDebug("search request", TransformerUtils.tinyTreeToString(searchDocument))

    httpRequest.getRequestPath match {
      case SearchPath(provider, app, form) ⇒

        val searchElement = searchDocument.rootElement
        val queryEls      = searchElement.child("query").toList
        val draftsElOpt   = searchElement.child("drafts").headOption
        val username      = Option(httpRequest.getUsername)
        val group         = Option(httpRequest.getUserGroup)

        Request(
          provider       = providerFromToken(provider),
          app            = app,
          form           = form,
          username       = username,
          group          = group,
          pageSize       = searchElement.firstChild("page-size")  .get.stringValue.toInt,
          pageNumber     = searchElement.firstChild("page-number").get.stringValue.toInt,
          freeTextSearch =
            queryEls
              // Free text is in the first <query>
              .headOption.map(_.stringValue)
              // Empty means no search
              .flatMap(ScalaUtils.trimAllToOpt),
          columns        =
            queryEls
              // First is for free text search
              .drop(1)
              .map(c ⇒
                Column(
                  path       = c.attValue("path"),
                  filterWith = ScalaUtils.trimAllToOpt(c.stringValue)
                )
              ),
          drafts         =
            username match {
              case None ⇒
                ExcludeDrafts
              case Some(_) ⇒
                draftsElOpt match {
                  case None ⇒
                    IncludeDrafts
                  case Some(draftsEl) ⇒
                    draftsEl.stringValue match {
                      case "exclude" ⇒ ExcludeDrafts
                      case "include" ⇒ IncludeDrafts
                      case "only"    ⇒ OnlyDrafts(
                        draftsEl.attValueOpt("for-document-id") match {
                          case Some(documentId) ⇒ DraftsForDocumentId(documentId)
                          case None ⇒
                            draftsEl.attValueOpt("for-never-saved-document") match {
                              case Some(_) ⇒ DraftsForNeverSavedDocs
                              case None    ⇒ AllDrafts
                            }
                        }
                      )
                    }
                }
            }
        )
    }
  }
}
