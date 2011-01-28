/*******************************************************************************
 * This file is part of the Virgo Web Server.
 *
 * Copyright (c) 2011 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SpringSource, a division of VMware - initial API and implementation and/or initial documentation
 *******************************************************************************/

package org.eclipse.virgo.kernel.osgi.region.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Set;

import org.easymock.EasyMock;
import org.eclipse.virgo.kernel.osgi.region.Region;
import org.eclipse.virgo.kernel.osgi.region.RegionDigraph;
import org.eclipse.virgo.kernel.osgi.region.RegionDigraph.FilteredRegion;
import org.eclipse.virgo.kernel.osgi.region.RegionFilter;
import org.eclipse.virgo.util.math.OrderedPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

public class StandardRegionDigraphTests {

    private RegionDigraph digraph;

    private Region mockRegion1;

    private Region mockRegion2;

    private Region mockRegion3;

    private RegionFilter regionFilter1;

    private RegionFilter regionFilter2;

    private Bundle mockBundle;

    @Before
    public void setUp() throws Exception {
        this.digraph = new StandardRegionDigraph();
       
        this.mockRegion1 = EasyMock.createMock(Region.class);
        EasyMock.expect(this.mockRegion1.getName()).andReturn("mockRegion1").anyTimes();
      
        this.mockRegion2 = EasyMock.createMock(Region.class);
        EasyMock.expect(this.mockRegion2.getName()).andReturn("mockRegion2").anyTimes();

        this.mockRegion3 = EasyMock.createMock(Region.class);
        EasyMock.expect(this.mockRegion3.getName()).andReturn("mockRegion3").anyTimes();

        this.regionFilter1 = EasyMock.createMock(RegionFilter.class);
        this.regionFilter2 = EasyMock.createMock(RegionFilter.class);
        this.mockBundle = EasyMock.createMock(Bundle.class);
    }

    private void setDefaultMockFilters() {
        setMockFilterAllowedBundles(this.regionFilter1);
        setMockFilterAllowedBundles(this.regionFilter2);
    }

    private void setMockFilterAllowedBundles(RegionFilter regionFilter) {
        EasyMock.expect(regionFilter.isBundleAllowed(EasyMock.isA(String.class), EasyMock.isA(Version.class))).andReturn(false).anyTimes();
    }

    private void setMockFilterAllowedBundle(RegionFilter regionFilter, OrderedPair<String, Version> bundle) {
        EasyMock.expect(regionFilter.isBundleAllowed(EasyMock.eq(bundle.getFirst()), EasyMock.eq(bundle.getSecond()))).andReturn(true).anyTimes();
        EasyMock.expect(regionFilter.isBundleAllowed(EasyMock.not(EasyMock.eq(bundle.getFirst())), EasyMock.eq(bundle.getSecond()))).andReturn(false).anyTimes();
        EasyMock.expect(regionFilter.isBundleAllowed(EasyMock.eq(bundle.getFirst()), EasyMock.not(EasyMock.eq(bundle.getSecond())))).andReturn(false).anyTimes();
        EasyMock.expect(regionFilter.isBundleAllowed(EasyMock.not(EasyMock.eq(bundle.getFirst())), EasyMock.not(EasyMock.eq(bundle.getSecond())))).andReturn(false).anyTimes();
    }

    private void replayMocks() {
        EasyMock.replay(this.mockRegion1, this.mockRegion2, this.mockRegion3, this.regionFilter1, this.regionFilter2, this.mockBundle);
    }

    @After
    public void tearDown() throws Exception {
        EasyMock.verify(this.mockRegion1, this.mockRegion2, this.mockRegion3, this.regionFilter1, this.regionFilter2, this.mockBundle);
    }

    @Test
    public void testConnect() throws BundleException {
        setDefaultMockFilters();
        replayMocks();

        this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
    }

    @Test
    public void testConnectWithFilterContents() throws BundleException {
        OrderedPair<String, Version> b1 = new OrderedPair<String, Version>("b1", new Version("0"));
        setMockFilterAllowedBundle(this.regionFilter1, b1);
        EasyMock.expect(this.mockRegion1.getBundle(b1.getFirst(), b1.getSecond())).andReturn(null).anyTimes();

        OrderedPair<String, Version> b2 = new OrderedPair<String, Version>("b2", new Version("0"));
        setMockFilterAllowedBundle(this.regionFilter2, b2);
        EasyMock.expect(this.mockRegion1.getBundle(b2.getFirst(), b2.getSecond())).andReturn(null).anyTimes();

        replayMocks();

        this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
        this.digraph.connect(this.mockRegion1, this.regionFilter2, this.mockRegion3);
    }

    @Test(expected = BundleException.class)
    public void testConnectLoop() throws BundleException {
        setDefaultMockFilters();
        replayMocks();

        this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion1);
    }

    @Test(expected = BundleException.class)
    public void testDuplicateConnection() throws BundleException {
        setDefaultMockFilters();
        replayMocks();

        this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
        this.digraph.connect(this.mockRegion1, this.regionFilter2, this.mockRegion2);
    }

    @Test
    public void testAddRegion() {
        setDefaultMockFilters();
        replayMocks();

        this.digraph.addRegion(this.mockRegion1);
        boolean found = false;
        for (Region region : this.digraph) {
            if (this.mockRegion1.equals(region)) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testGetEdges() throws BundleException {
        setDefaultMockFilters();
        replayMocks();

        this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
        this.digraph.connect(this.mockRegion1, this.regionFilter2, this.mockRegion3);
        this.digraph.connect(this.mockRegion2, this.regionFilter2, this.mockRegion1);

        Set<FilteredRegion> edges = this.digraph.getEdges(this.mockRegion1);

        assertEquals(2, edges.size());

        for (FilteredRegion edge : edges) {
            if (edge.getRegion().equals(this.mockRegion2)) {
                assertEquals(this.regionFilter1, edge.getFilter());
            } else if (edge.getRegion().equals(this.mockRegion3)) {
                assertEquals(this.regionFilter2, edge.getFilter());
            } else {
                fail("unexpected edge");
            }
        }
    }

    @Test
    public void testRemoveRegion() throws BundleException {
        setDefaultMockFilters();
        replayMocks();

        this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
        this.digraph.connect(this.mockRegion2, this.regionFilter2, this.mockRegion1);
        assertNotNull(this.digraph.getRegion("mockRegion1"));
        assertNotNull(this.digraph.getRegion("mockRegion2"));
        this.digraph.removeRegion(this.mockRegion1);
        assertNull(this.digraph.getRegion("mockRegion1"));
        assertNotNull(this.digraph.getRegion("mockRegion2"));
    }
    
}
