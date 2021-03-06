/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing.v2

import java.util.UUID

import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.JsonLDUtil
import org.knora.webapi.messages.v2.responder.standoffmessages.{CreateMappingRequestMetadataV2, CreateMappingRequestV2, CreateMappingRequestXMLV2, GetStandoffPageRequestV2}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Provides a function for API routes that deal with search.
 */
class StandoffRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route = {

        path("v2" / "standoff" / Segment / Segment / Segment) { (resourceIriStr: String, valueIriStr: String, offsetStr: String) =>
            get {
                requestContext => {
                    val resourceIri: SmartIri = resourceIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid resource IRI: $resourceIriStr"))

                    if (!resourceIri.isKnoraResourceIri) {
                        throw BadRequestException(s"Invalid resource IRI: $resourceIriStr")
                    }

                    val valueIri: SmartIri = valueIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid value IRI: $valueIriStr"))

                    if (!valueIri.isKnoraValueIri) {
                        throw BadRequestException(s"Invalid value IRI: $valueIriStr")
                    }

                    val offset: Int = stringFormatter.validateInt(offsetStr, throw BadRequestException(s"Invalid offset: $offsetStr"))
                    val schemaOptions: Set[SchemaOption] = SchemaOptions.ForStandoffSeparateFromTextValues

                    val targetSchema: ApiV2Schema = RouteUtilV2.getOntologySchema(requestContext)

                    val requestMessageFuture: Future[GetStandoffPageRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield GetStandoffPageRequestV2(
                        resourceIri = resourceIri.toString,
                        valueIri = valueIri.toString,
                        offset = offset,
                        targetSchema = targetSchema,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = schemaOptions
                    )
                }
            }
        } ~ path("v2" / "mapping") {
            post {
                entity(as[Multipart.FormData]) { formdata: Multipart.FormData =>
                    requestContext =>

                        val JSON_PART = "json"
                        val XML_PART = "xml"
                        type Name = String

                        val apiRequestID = UUID.randomUUID

                        // collect all parts of the multipart as it arrives into a map
                        val allPartsFuture: Future[Map[Name, String]] = formdata.parts.mapAsync[(Name, String)](1) {
                            case b: BodyPart if b.name == JSON_PART =>
                                //loggingAdapter.debug(s"inside allPartsFuture - processing $JSON_PART")
                                b.toStrict(2.seconds).map { strict =>
                                    //loggingAdapter.debug(strict.entity.data.utf8String)
                                    (b.name, strict.entity.data.utf8String)
                                }

                            case b: BodyPart if b.name == XML_PART =>
                                //loggingAdapter.debug(s"inside allPartsFuture - processing $XML_PART")

                                b.toStrict(2.seconds).map {
                                    strict =>
                                        //loggingAdapter.debug(strict.entity.data.utf8String)
                                        (b.name, strict.entity.data.utf8String)
                                }

                            case b: BodyPart if b.name.isEmpty => throw BadRequestException("part of HTTP multipart request has no name")

                            case b: BodyPart => throw BadRequestException(s"multipart contains invalid name: ${b.name}")

                            case _ => throw BadRequestException("multipart request could not be handled")
                        }.runFold(Map.empty[Name, String])((map, tuple) => map + tuple)

                        val requestMessageFuture: Future[CreateMappingRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            allParts: Map[Name, String] <- allPartsFuture
                            jsonldDoc = JsonLDUtil.parseJsonLD(allParts.getOrElse(JSON_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$JSON_PART' part!")).toString)

                            metadata: CreateMappingRequestMetadataV2 <- CreateMappingRequestMetadataV2.fromJsonLD(
                                jsonLDDocument = jsonldDoc,
                                apiRequestID = apiRequestID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )

                            xml: String = allParts.getOrElse(XML_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$XML_PART' part!")).toString
                        } yield CreateMappingRequestV2(
                            metadata = metadata,
                            xml = CreateMappingRequestXMLV2(xml),
                            requestingUser = requestingUser,
                            apiRequestID = apiRequestID
                        )

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = log,
                            targetSchema = ApiV2Complex,
                            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                        )
                }
            }

        }
    }
}