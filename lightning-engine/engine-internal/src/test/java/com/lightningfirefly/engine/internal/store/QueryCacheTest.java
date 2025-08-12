package com.lightningfirefly.engine.internal.store;

import com.lightningfirefly.engine.internal.core.store.QueryCache;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class QueryCacheTest {

    private QueryCache queryCache = new QueryCache();

    private static final int COMPONENT_1 = 1;
    private static final int COMPONENT_2 = 2;

    @Test
    void getSet_roundTrip_oneComponent() {
        Set<Long> expected = Set.of(123L);
        queryCache.put(expected, COMPONENT_1);
        Set<Long> actual = queryCache.get(COMPONENT_1);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void getSet_roundTrip_multipleComponent() {
        Set<Long> expected = Set.of(123L);
        queryCache.put(expected, COMPONENT_1, COMPONENT_2);
        Set<Long> actual = queryCache.get(COMPONENT_1, COMPONENT_2);
        assertThat(actual).isEqualTo(expected);
    }
}