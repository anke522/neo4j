/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.index.schema;

import org.neo4j.kernel.api.schema.index.TestIndexDescriptorFactory;
import org.neo4j.kernel.impl.api.index.sampling.IndexSamplingConfig;
import org.neo4j.values.storable.ValueGroup;

public class DateTimeNonUniqueSchemaIndexPopulatorTest extends NativeNonUniqueSchemaIndexPopulatorTest<ZonedDateTimeSchemaKey,NativeSchemaValue>
{
    @Override
    NativeSchemaIndexPopulator<ZonedDateTimeSchemaKey,NativeSchemaValue> createPopulator( IndexSamplingConfig samplingConfig )
    {
        TemporalIndexFiles.FileLayout<ZonedDateTimeSchemaKey> fileLayout =
                new TemporalIndexFiles.FileLayout<>( getIndexFile(), layout, ValueGroup.ZONED_DATE_TIME );
        return new TemporalIndexPopulator.PartPopulator<>( pageCache, fs, fileLayout, monitor, indexDescriptor, samplingConfig );
    }

    @Override
    protected LayoutTestUtil<ZonedDateTimeSchemaKey,NativeSchemaValue> createLayoutTestUtil()
    {
        return new DateTimeLayoutTestUtil( TestIndexDescriptorFactory.forLabel( 42, 666 ) );
    }
}
