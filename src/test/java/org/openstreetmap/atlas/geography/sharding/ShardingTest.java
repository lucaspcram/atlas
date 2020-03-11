package org.openstreetmap.atlas.geography.sharding;

import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * @author lcram
 */
public class ShardingTest
{
    private static final Logger logger = LoggerFactory.getLogger(ShardingTest.class);

    @Test
    public final void test()
    {
        final SlippyTileSharding zoom6 = new SlippyTileSharding(6);
        final SlippyTileSharding zoom7 = new SlippyTileSharding(7);
        final List<Shard> zoom6IntersectWith7_19_43 = Lists
                .newArrayList(zoom6.shards(SlippyTile.forName("7-19-43").bounds()));
        logger.info("{}", zoom6IntersectWith7_19_43);

        final List<Shard> zoom6IntersectWith7_19_44 = Lists
                .newArrayList(zoom6.shards(SlippyTile.forName("7-19-44").bounds()));
        logger.info("{}", zoom6IntersectWith7_19_44);
    }
}
