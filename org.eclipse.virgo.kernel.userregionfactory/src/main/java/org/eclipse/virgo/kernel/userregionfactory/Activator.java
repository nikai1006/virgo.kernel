/*******************************************************************************
 * This file is part of the Virgo Web Server.
 *
 * Copyright (c) 2010 Eclipse Foundation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SpringSource, a division of VMware - initial API and implementation and/or initial documentation
 *******************************************************************************/

package org.eclipse.virgo.kernel.userregionfactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.virgo.kernel.core.Shutdown;
import org.eclipse.virgo.kernel.osgi.framework.OsgiFrameworkLogEvents;
import org.eclipse.virgo.kernel.osgi.framework.OsgiFrameworkUtils;
import org.eclipse.virgo.kernel.osgi.framework.OsgiServiceHolder;
import org.eclipse.virgo.kernel.osgi.region.ImmutableRegion;
import org.eclipse.virgo.kernel.osgi.region.Region;
import org.eclipse.virgo.kernel.osgi.region.RegionMembership;
import org.eclipse.virgo.medic.eventlog.EventLogger;
import org.eclipse.virgo.osgi.launcher.parser.ArgumentParser;
import org.eclipse.virgo.osgi.launcher.parser.BundleEntry;
import org.eclipse.virgo.util.osgi.ServiceRegistrationTracker;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

/**
 *{@link Activator} initialises the user region factory bundle.
 * <p />
 * 
 * <strong>Concurrent Semantics</strong><br />
 * 
 * Not thread safe.
 * 
 */
public final class Activator implements BundleActivator {
    
    private static final long MAX_SECONDS_WAIT_FOR_SERVICE = 30;

    private static final long MAX_MILLIS_WAIT_FOR_SERVICE = TimeUnit.SECONDS.toMillis(MAX_SECONDS_WAIT_FOR_SERVICE);
    
    private static final String USER_REGION_CONFIGURATION_PID = "org.eclipse.virgo.kernel.userregion";
    
    private static final String USER_REGION_BASE_BUNDLES_PROPERTY = "baseBundles";

    private static final String USER_REGION_PACKAGE_IMPORTS_PROPERTY = "packageImports";

    private static final String USER_REGION_SERVICE_IMPORTS_PROPERTY = "serviceImports";

    private static final String USER_REGION_SERVICE_EXPORTS_PROPERTY = "serviceExports";
    
    private static final String USER_REGION_BUNDLE_CONTEXT_SERVICE_PROPERTY = "org.eclipse.virgo.kernel.regionContext";
    
    private static final String REGION_USER = "org.eclipse.virgo.region.user";
    
    private static final String EVENT_REGION_STARTING = "org/eclipse/virgo/kernel/region/STARTING";

    private static final String EVENT_PROPERTY_REGION_BUNDLECONTEXT = "region.bundleContext";
    
    private static final String USER_REGION_LOCATION_TAG = "userregion@";
    
    private static final String REFERENCE_SCHEME = "reference:";

    private static final String FILE_SCHEME = "file:";

    private EventAdmin eventAdmin;
    
    private String regionBundles;

    private String regionImports;

    private String regionServiceImports;

    private String regionServiceExports;

    private BundleContext bundleContext;
    
    private final ArgumentParser parser = new ArgumentParser();
    
    private final ServiceRegistrationTracker tracker = new ServiceRegistrationTracker();

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(BundleContext bundleContext) throws Exception {
        this.bundleContext = bundleContext;
        RegionMembership regionMembership = getPotentiallyDelayedService(bundleContext, RegionMembership.class);
        this.eventAdmin = getPotentiallyDelayedService(bundleContext, EventAdmin.class);
        ConfigurationAdmin configAdmin= getPotentiallyDelayedService(bundleContext, ConfigurationAdmin.class);
        EventLogger eventLogger = getPotentiallyDelayedService(bundleContext, EventLogger.class);
        Shutdown shutdown = getPotentiallyDelayedService(bundleContext, Shutdown.class);
        getRegionConfiguration(configAdmin, eventLogger, shutdown);

        createUserRegion(bundleContext, regionMembership);
    }
    
    private void getRegionConfiguration(ConfigurationAdmin configAdmin, EventLogger eventLogger, Shutdown shutdown) {
        try {
            Configuration config = configAdmin.getConfiguration(USER_REGION_CONFIGURATION_PID, null);

            @SuppressWarnings("unchecked")
            Dictionary<String, String> properties = config.getProperties();

            if (properties != null) {
                this.regionBundles = properties.get(USER_REGION_BASE_BUNDLES_PROPERTY);
                this.regionImports = properties.get(USER_REGION_PACKAGE_IMPORTS_PROPERTY);
                this.regionServiceImports = properties.get(USER_REGION_SERVICE_IMPORTS_PROPERTY);
                this.regionServiceExports = properties.get(USER_REGION_SERVICE_EXPORTS_PROPERTY);
            } else {
                eventLogger.log(OsgiFrameworkLogEvents.USER_REGION_CONFIGURATION_UNAVAILABLE);
                shutdown.immediateShutdown();
            }
        } catch (Exception e) {
            eventLogger.log(OsgiFrameworkLogEvents.USER_REGION_CONFIGURATION_UNAVAILABLE, e);
            shutdown.immediateShutdown();
        }
    }

    
    private void createUserRegion(BundleContext userRegionBundleContext, RegionMembership regionMembership) throws BundleException {
        
        ImmutableRegion userRegion = new ImmutableRegion(REGION_USER, userRegionBundleContext);
        regionMembership.setUserRegion(userRegion);
        notifyUserRegionStarting(userRegionBundleContext);

        initialiseUserRegionBundles();

        registerRegionService(userRegion);
        publishUserRegionBundleContext(userRegionBundleContext);
    }
    
    private void notifyUserRegionStarting(BundleContext userRegionBundleContext) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(EVENT_PROPERTY_REGION_BUNDLECONTEXT, userRegionBundleContext);
        this.eventAdmin.sendEvent(new Event(EVENT_REGION_STARTING, properties));
    }

    private void initialiseUserRegionBundles() throws BundleException {

        String userRegionBundlesProperty = this.regionBundles != null ? this.regionBundles
            : this.bundleContext.getProperty(USER_REGION_BASE_BUNDLES_PROPERTY);

        if (userRegionBundlesProperty != null) {
            List<Bundle> bundlesToStart = new ArrayList<Bundle>();

            for (BundleEntry entry : this.parser.parseBundleEntries(userRegionBundlesProperty)) {
                URI uri = entry.getURI();
                Bundle bundle = this.bundleContext.installBundle(USER_REGION_LOCATION_TAG + uri.toString(), openBundleStream(uri));

                if (entry.isAutoStart()) {
                    bundlesToStart.add(bundle);
                }
            }

            for (Bundle bundle : bundlesToStart) {
                try {
                    bundle.start();
                } catch (BundleException e) {
                    throw new BundleException("Failed to start bundle " + bundle.getSymbolicName() + " " + bundle.getVersion(), e);
                }
            }
        }
    }
    
    private InputStream openBundleStream(URI uri) throws BundleException {
        String absoluteBundleUriString = getAbsoluteUriString(uri);

        try {
            // Use the reference: scheme to obtain an InputStream for either a file or a directory.
            return new URL(REFERENCE_SCHEME + absoluteBundleUriString).openStream();

        } catch (MalformedURLException e) {
            throw new BundleException(USER_REGION_BASE_BUNDLES_PROPERTY + " property resulted in an invalid bundle URI '" + absoluteBundleUriString
                + "'", e);
        } catch (IOException e) {
            throw new BundleException(USER_REGION_BASE_BUNDLES_PROPERTY + " property referred to an invalid bundle at URI '"
                + absoluteBundleUriString + "'", e);
        }
    }

    private String getAbsoluteUriString(URI uri) throws BundleException {
        String bundleUriString = uri.toString();

        if (!bundleUriString.startsWith(FILE_SCHEME)) {
            throw new BundleException(USER_REGION_BASE_BUNDLES_PROPERTY + " property contained an entry '" + bundleUriString
                + "' which did not start with '" + FILE_SCHEME + "'");
        }

        String filePath = bundleUriString.substring(FILE_SCHEME.length());

        return FILE_SCHEME + new File(filePath).getAbsolutePath();
    }



    private void registerRegionService(Region region) {
        Dictionary<String, String> props = new Hashtable<String, String>();
        props.put("org.eclipse.virgo.kernel.region.name", region.getName());
        this.tracker.track(this.bundleContext.registerService(Region.class, region, props));
    }

    private void publishUserRegionBundleContext(BundleContext userRegionBundleContext) {
        Dictionary<String, String> properties = new Hashtable<String, String>();
        properties.put(USER_REGION_BUNDLE_CONTEXT_SERVICE_PROPERTY, "true");
        this.bundleContext.registerService(BundleContext.class, userRegionBundleContext, properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(BundleContext context) throws Exception {
    }

    private static <T> T getPotentiallyDelayedService(BundleContext context, Class<T> serviceClass) throws TimeoutException, InterruptedException {
        T service = null;
        OsgiServiceHolder<T> serviceHolder;
        long millisWaited = 0;
        while (service == null && millisWaited <= MAX_MILLIS_WAIT_FOR_SERVICE) {
            try {
                serviceHolder = OsgiFrameworkUtils.getService(context, serviceClass);
                if (serviceHolder != null) {
                    service = serviceHolder.getService();
                } else {
                    millisWaited += sleepABitMore();
                }
            } catch (IllegalStateException e) {
            }
        }
        if (service == null) {
            throw new TimeoutException(serviceClass.getName());
        }
        return service;
    }

    private static long sleepABitMore() throws InterruptedException {
        long before = System.currentTimeMillis();
        Thread.sleep(100);
        return (System.currentTimeMillis() - before);
    }
}
