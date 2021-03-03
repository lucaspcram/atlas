package org.openstreetmap.atlas.geography.atlas;

import java.nio.file.FileSystems;

import org.junit.Test;
import org.openstreetmap.atlas.geography.atlas.items.complex.RelationOrAreaToMultiPolygonConverter;
import org.openstreetmap.atlas.geography.atlas.packed.PackedAtlasBuilder;
import org.openstreetmap.atlas.geography.converters.jts.JtsMultiPolygonToMultiPolygonConverter;
import org.openstreetmap.atlas.streaming.resource.File;

/**
 * @author lcram
 */
public class RelationMultipolygonGeometryTest
{
    @Test
    public void initialTest()
    {
        final PackedAtlasBuilder builder = new PackedAtlasBuilder();
        final Atlas original = new AtlasResourceLoader().load(
                new File("/Users/lucascram/Desktop/2336472000000.atlas", FileSystems.getDefault()));
        original.lines().forEach(line ->
        {
            builder.addLine(line.getIdentifier(), line.asPolyLine(), line.getTags());
        });
        original.points().forEach(point ->
        {
            builder.addPoint(point.getIdentifier(), point.getLocation(), point.getTags());
        });
        original.nodes().forEach(node ->
        {
            builder.addNode(node.getIdentifier(), node.getLocation(), node.getTags());
        });
        original.edges().forEach(edge ->
        {
            builder.addEdge(edge.getIdentifier(), edge.asPolyLine(), edge.getTags());
        });
        original.areas().forEach(area ->
        {
            builder.addArea(area.getIdentifier(), area.asPolygon(), area.getTags());
        });
        original.relations().forEach(relation ->
        {
            if (relation.isMultiPolygon())
            {
                builder.addRelation(relation.getIdentifier(), relation.getOsmIdentifier(),
                        relation.getBean(), relation.getTags(),
                        new JtsMultiPolygonToMultiPolygonConverter().backwardConvert(
                                new RelationOrAreaToMultiPolygonConverter().convert(relation)));
            }
            else
            {
                builder.addRelation(relation.getIdentifier(), relation.getOsmIdentifier(),
                        relation.getBean(), relation.getTags());
            }
        });
        final Atlas atlas = builder.get();
        assert atlas != null;
        final File atlasFile = new File("/Users/lucascram/Desktop/2336472000000_withGeom.atlas",
                FileSystems.getDefault());
        atlas.save(atlasFile);

        new AtlasResourceLoader().load(atlasFile).relations().forEach(relation ->
        {
            relation.getJtsGeometry().ifPresent(jts -> System.err.println(jts.getCentroid()));
        });

    }

    @Test
    public void testSaveOldAtlas()
    {
        final File atlasFile = new File("/Users/lucascram/Desktop/2336472000000.atlas",
                FileSystems.getDefault());
        final File atlasFileCopy = new File("/Users/lucascram/Desktop/2336472000000_copy.atlas",
                FileSystems.getDefault());
        new AtlasResourceLoader().load(atlasFile).save(atlasFileCopy);
    }
}
