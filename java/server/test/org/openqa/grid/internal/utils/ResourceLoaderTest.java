package org.openqa.grid.internal.utils;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author Kristian Rosenvold
 */
public class ResourceLoaderTest {
    @Test
    public void testLoad() throws Exception {
        assertNotNull(ResourceLoader.load("/org/openqa/grid/common/defaults/DefaultHub.json"));
    }
    @Test
    public void testLoadShortName() throws Exception {
        assertNotNull(ResourceLoader.load("defaults/DefaultHub.json"));

    }
}
