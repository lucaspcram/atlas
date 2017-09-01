package org.openstreetmap.atlas.geography;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.atlas.geography.clipping.Clip;
import org.openstreetmap.atlas.geography.clipping.Clip.ClipType;
import org.openstreetmap.atlas.geography.converters.MultiPolygonStringConverter;
import org.openstreetmap.atlas.geography.converters.WktMultiPolygonConverter;
import org.openstreetmap.atlas.geography.geojson.GeoJsonBuilder;
import org.openstreetmap.atlas.geography.geojson.GeoJsonBuilder.LocationIterableProperties;
import org.openstreetmap.atlas.geography.geojson.GeoJsonObject;
import org.openstreetmap.atlas.streaming.resource.WritableResource;
import org.openstreetmap.atlas.streaming.writers.JsonWriter;
import org.openstreetmap.atlas.utilities.collections.Iterables;
import org.openstreetmap.atlas.utilities.collections.MultiIterable;
import org.openstreetmap.atlas.utilities.collections.StringList;
import org.openstreetmap.atlas.utilities.maps.MultiMap;
import org.openstreetmap.atlas.utilities.scalars.Surface;

/**
 * Multiple {@link Polygon}s some inner, some outer.
 *
 * @author matthieun
 */
public class MultiPolygon implements Iterable<Polygon>, Located, Serializable
{
    private static final long serialVersionUID = 4198234682870043547L;
    private static final int SIMPLE_STRING_LENGTH = 200;

    public static final MultiPolygon MAXIMUM = forPolygon(Rectangle.MAXIMUM);
    public static final MultiPolygon TEST_MULTI_POLYGON;

    static
    {
        final MultiMap<Polygon, Polygon> outerToInners = new MultiMap<>();
        final Polygon outer = new Polygon(Location.CROSSING_85_280, Location.CROSSING_85_17,
                Location.TEST_1, Location.TEST_5);
        final Polygon inner = new Polygon(Location.TEST_6, Location.TEST_2, Location.TEST_7);
        outerToInners.add(outer, inner);
        TEST_MULTI_POLYGON = new MultiPolygon(outerToInners);
    }

    private final MultiMap<Polygon, Polygon> outerToInners;
    private Rectangle bounds;

    /**
     * @param polygon
     *            A simple {@link Polygon}
     * @return A {@link MultiPolygon} with the provided {@link Polygon} as a single outer
     *         {@link Polygon}, with no inner {@link Polygon}
     */
    public static MultiPolygon forPolygon(final Polygon polygon)
    {
        final MultiMap<Polygon, Polygon> multiMap = new MultiMap<>();
        multiMap.put(polygon, new ArrayList<>());
        return new MultiPolygon(multiMap);
    }

    /**
     * Generate a {@link MultiPolygon} from Well Known Text
     *
     * @param wkt
     *            The {@link MultiPolygon} in well known text
     * @return The parsed {@link MultiPolygon}
     */
    public static MultiPolygon wkt(final String wkt)
    {
        return new WktMultiPolygonConverter().backwardConvert(wkt);
    }

    public MultiPolygon(final MultiMap<Polygon, Polygon> outerToInners)
    {
        this.outerToInners = outerToInners;
    }

    public GeoJsonObject asGeoJson()
    {
        return new GeoJsonBuilder().create(asLocationIterableProperties());
    }

    public Iterable<LocationIterableProperties> asLocationIterableProperties()
    {
        final Iterable<LocationIterableProperties> outers = Iterables.translate(outers(), polygon ->
        {
            final Map<String, String> tags = new HashMap<>();
            tags.put("MultiPolygon", "outer");
            return new LocationIterableProperties(polygon, new HashMap<>());
        });
        final Iterable<LocationIterableProperties> inners = Iterables.translate(inners(), polygon ->
        {
            final Map<String, String> tags = new HashMap<>();
            tags.put("MultiPolygon", "inner");
            return new LocationIterableProperties(polygon, new HashMap<>());
        });
        return new MultiIterable<>(outers, inners);
    }

    @Override
    public Rectangle bounds()
    {
        if (this.bounds == null)
        {
            final Set<Location> locations = new HashSet<>();
            forEach(polygon -> polygon.forEach(location -> locations.add(location)));
            this.bounds = Rectangle.forLocations(locations);
        }
        return this.bounds;
    }

    /**
     * @param clipping
     *            The {@link MultiPolygon} clipping that {@link MultiPolygon}
     * @param clipType
     *            The type of clip (union, or, and or xor)
     * @return The {@link Clip} container, that can return the clipped {@link MultiPolygon}
     */
    public Clip clip(final MultiPolygon clipping, final ClipType clipType)
    {
        return new Clip(clipType, this, clipping);
    }

    /**
     * Concatenate multiple {@link MultiPolygon}s into one. If the two {@link MultiPolygon}s happen
     * to have the same outer polygon, then the other's inner polygons will be added and the
     * current's inner polygons will be erased.
     *
     * @param other
     *            The other {@link MultiPolygon} to concatenate.
     * @return The concatenated {@link MultiPolygon}
     */
    public MultiPolygon concatenate(final MultiPolygon other)
    {
        final MultiMap<Polygon, Polygon> result = new MultiMap<>();
        result.putAll(getOuterToInners());
        result.putAll(other.getOuterToInners());
        return new MultiPolygon(result);
    }

    @Override
    public boolean equals(final Object other)
    {
        if (other instanceof MultiPolygon)
        {
            final MultiPolygon that = (MultiPolygon) other;
            final Set<Polygon> thatOuters = that.outers();
            if (thatOuters.size() != this.outers().size())
            {
                return false;
            }
            for (final Polygon outer : this.outers())
            {
                if (!thatOuters.contains(outer))
                {
                    return false;
                }
                final List<Polygon> thatInners = that.innersOf(outer);
                if (thatInners.size() != this.innersOf(outer).size())
                {
                    return false;
                }
                for (final Polygon inner : this.innersOf(outer))
                {
                    if (!thatInners.contains(inner))
                    {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * @param location
     *            A {@link Location} item
     * @return True if the {@link MultiPolygon} contains the provided item (i.e. it is within the
     *         outer polygons and not within the inner polygons)
     */
    public boolean fullyGeometricallyEncloses(final Location location)
    {
        for (final Polygon inner : inners())
        {
            if (inner.fullyGeometricallyEncloses(location))
            {
                return false;
            }
        }
        for (final Polygon outer : outers())
        {
            if (outer.fullyGeometricallyEncloses(location))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @param polyLine
     *            A {@link PolyLine} item
     * @return True if the {@link MultiPolygon} contains the provided {@link PolyLine}.
     */
    public boolean fullyGeometricallyEncloses(final PolyLine polyLine)
    {
        for (final Polygon inner : inners())
        {
            if (inner.overlaps(polyLine))
            {
                return false;
            }
        }
        for (final Polygon outer : outers())
        {
            if (outer.fullyGeometricallyEncloses(polyLine))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        int result = 0;
        for (final Polygon polygon : this)
        {
            result += polygon.hashCode();
        }
        return result;
    }

    public List<Polygon> inners()
    {
        return this.outerToInners.allValues();
    }

    public List<Polygon> innersOf(final Polygon outer)
    {
        if (this.outerToInners.containsKey(outer))
        {
            return this.outerToInners.get(outer);
        }
        return new ArrayList<>();
    }

    @Override
    public Iterator<Polygon> iterator()
    {
        return new MultiIterable<>(outers(), inners()).iterator();
    }

    /**
     * Merge multiple {@link MultiPolygon}s into one. If the two {@link MultiPolygon}s happen to
     * have the same outer polygon, then the two's inner polygons will be added to the same list.
     *
     * @param other
     *            The other {@link MultiPolygon} to merge.
     * @return The concatenated {@link MultiPolygon}
     */
    public MultiPolygon merge(final MultiPolygon other)
    {
        final MultiMap<Polygon, Polygon> result = new MultiMap<>();
        result.putAll(getOuterToInners());
        result.addAll(other.getOuterToInners());
        return new MultiPolygon(result);
    }

    public Set<Polygon> outers()
    {
        return this.outerToInners.keySet();
    }

    public boolean overlaps(final PolyLine polyLine)
    {
        for (final Location location : polyLine)
        {
            if (fullyGeometricallyEncloses(location))
            {
                return true;
            }
        }
        return false;
    }

    public void saveAsGeoJson(final WritableResource resource)
    {
        final JsonWriter writer = new JsonWriter(resource);
        writer.write(asGeoJson().jsonObject());
        writer.close();
    }

    public Surface surface()
    {
        Surface result = Surface.MINIMUM;
        for (final Polygon outer : this.outers())
        {
            result = result.add(outer.surface());
        }
        for (final Polygon inner : this.inners())
        {
            result = result.subtract(inner.surface());
        }
        return result;
    }

    public String toCompactString()
    {
        return new MultiPolygonStringConverter().backwardConvert(this);
    }

    public String toReadableString()
    {
        final String separator1 = "\n\t";
        final String separator2 = "\n\t\t";
        final StringBuilder builder = new StringBuilder();
        final StringList outers = new StringList();
        for (final Polygon outer : this.outers())
        {
            final StringList inners = new StringList();
            for (final Polygon inner : innersOf(outer))
            {
                inners.add("Inner: " + inner.toCompactString());
            }
            outers.add("Outer: " + outer.toCompactString() + separator2 + inners.join(separator2));
        }
        builder.append(outers.join(separator1));
        return builder.toString();
    }

    public String toSimpleString()
    {
        final String string = toCompactString();
        if (string.length() > SIMPLE_STRING_LENGTH + 1)
        {
            return string.substring(0, SIMPLE_STRING_LENGTH) + "...";
        }
        return string;
    }

    @Override
    public String toString()
    {
        return toWkt();
    }

    public String toWkt()
    {
        return new WktMultiPolygonConverter().convert(this);
    }

    protected MultiMap<Polygon, Polygon> getOuterToInners()
    {
        return this.outerToInners;
    }
}