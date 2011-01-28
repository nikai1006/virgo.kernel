/*******************************************************************************
 * Copyright (c) 2008, 2010 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   VMware Inc. - initial contribution
 *******************************************************************************/

package org.eclipse.virgo.kernel.userregion.internal.quasi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.eclipse.osgi.internal.baseadaptor.StateManager;
import org.eclipse.osgi.service.resolver.PlatformAdmin;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.virgo.kernel.osgi.framework.OsgiFrameworkUtils;
import org.eclipse.virgo.kernel.osgi.framework.OsgiServiceHolder;
import org.eclipse.virgo.kernel.osgi.quasi.QuasiFramework;
import org.eclipse.virgo.kernel.osgi.quasi.QuasiFrameworkFactory;
import org.eclipse.virgo.kernel.osgi.region.Region;
import org.eclipse.virgo.kernel.osgi.region.RegionDigraph;
import org.eclipse.virgo.kernel.userregion.internal.equinox.TransformedManifestProvidingBundleFileWrapper;
import org.eclipse.virgo.repository.Repository;
import org.eclipse.virgo.util.io.FileSystemUtils;

/**
 * {@link StandardQuasiFrameworkFactory} is the default implementation of {@link QuasiFrameworkFactory}.
 * <p />
 * 
 * <strong>Concurrent Semantics</strong><br />
 * 
 * This class is thread safe.
 * 
 */
public final class StandardQuasiFrameworkFactory implements QuasiFrameworkFactory {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final BundleContext bundleContext;

    private final PlatformAdmin platformAdmin;

    private final StateManager stateManager;

    private ResolutionFailureDetective detective;

    private final Repository repository;
    
    private final TransformedManifestProvidingBundleFileWrapper bundleTransformationHandler;

    private final Region userRegion;

    public StandardQuasiFrameworkFactory(BundleContext bundleContext, ResolutionFailureDetective detective, Repository repository, TransformedManifestProvidingBundleFileWrapper bundleTransformationHandler, Region userRegion) {
        this.bundleContext = bundleContext;
        this.platformAdmin = getPlatformAdminService(bundleContext);
        this.detective = detective;
        this.repository = repository;
        ServiceReference<PlatformAdmin> platformAdminServiceReference = bundleContext.getServiceReference(PlatformAdmin.class);
        this.stateManager = (StateManager) bundleContext.getService(platformAdminServiceReference);
        this.bundleTransformationHandler = bundleTransformationHandler;
        this.userRegion = userRegion;
    }

    /**
     * {@inheritDoc}
     */
    public QuasiFramework create() {
        return new StandardQuasiFramework(this.bundleContext, createState(), this.platformAdmin, this.detective, this.repository, this.bundleTransformationHandler, this.userRegion);
    }
    
    /** 
     * {@inheritDoc}
     */
    public QuasiFramework create(File stateDump) {
        return new StandardQuasiFramework(this.bundleContext, readStateDump(stateDump), this.platformAdmin, this.detective, this.repository, this.bundleTransformationHandler, this.userRegion);
    }

    @SuppressWarnings("deprecation")
    private State createState() {        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        State state;
        
        try {
            this.platformAdmin.getFactory().writeState(this.stateManager.getSystemState(), baos);
            state = this.platformAdmin.getFactory().readState(new ByteArrayInputStream(baos.toByteArray()));
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to create a copy of the OSGi state", ioe);
        }
        
        if (state.getResolver() == null) {
            state.setResolver(this.platformAdmin.createResolver());
        }
        
        if (!state.isResolved()) {
            state.resolve(true);
        }
        
        return state;
    }

    private State readStateDump(File outdir) {
        State state = null;

        try {
            StateObjectFactory sof = this.platformAdmin.getFactory();
            state = sof.readState(outdir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read resolver state", e);
        } finally {
            try { // delete all state files written to this directory
                if (outdir.isDirectory()) {
                    for (String filename : FileSystemUtils.list(outdir)) {
                        File file = new File(outdir, filename);
                        if (!file.delete()) {
                            this.logger.warn("Temporary file '{}' not deleted", file.getAbsolutePath());
                        }
                    }
                }
            } finally {
                if (!outdir.delete() && outdir.exists()) {
                    this.logger.warn("Temporary state directory '{}' was not removed after use.", outdir.getAbsolutePath());
                }
            }
        }
        
        if (state.getResolver() == null) {
            state.setResolver(this.platformAdmin.createResolver());
        }

        if (!state.isResolved()) {
            state.resolve(true);
        }
        
        return state;
    }

    /**
     * Gets the {@link PlatformAdmin} service.
     * 
     * @param bundleContext the {@link BundleContext} to use for lookup.
     * @return the <code>PlatformAdmin</code> service.
     */
    private static PlatformAdmin getPlatformAdminService(BundleContext bundleContext) {
        OsgiServiceHolder<PlatformAdmin> service = OsgiFrameworkUtils.getService(bundleContext, PlatformAdmin.class);
        PlatformAdmin platformAdmin = service.getService();
        if (platformAdmin == null) {
            throw new IllegalStateException("Unable to locate PlatformAdmin service");
        }
        return platformAdmin;
    }

}
