/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora

package object webapi {
    /**
     * The version of `knora-base` and of the other built-in ontologies that this version of Knora requires.
     * Must be the same as the object of `knora-base:ontologyVersion` in the `knora-base` ontology being used.
     */
    val KnoraBaseVersion: String = "knora-base v8"

    /**
     * `IRI` is a synonym for `String`, used to improve code readability.
     */
    type IRI = String

}
