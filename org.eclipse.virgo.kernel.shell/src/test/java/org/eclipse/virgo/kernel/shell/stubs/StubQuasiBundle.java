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
package org.eclipse.virgo.kernel.shell.stubs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.equinox.region.Region;
import org.eclipse.virgo.kernel.artifact.plan.PlanDescriptor.Provisioning;
import org.eclipse.virgo.kernel.osgi.quasi.QuasiBundle;
import org.eclipse.virgo.kernel.osgi.quasi.QuasiExportPackage;
import org.eclipse.virgo.kernel.osgi.quasi.QuasiImportPackage;
import org.eclipse.virgo.kernel.osgi.quasi.QuasiRequiredBundle;
import org.eclipse.virgo.test.stubs.framework.StubBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/**
 * 
 *
 */
public class StubQuasiBundle implements QuasiBundle {

    private final Version version;

    private final String name;

    private volatile Provisioning provisioning = Provisioning.AUTO;

	private Long id;

    public StubQuasiBundle(Long id, String name, Version version) {
        this.id = id;
		this.name = name;
        this.version = version;
    }

    public StubQuasiBundle(StubBundle bundle) {
        this.id = bundle.getBundleId();
		this.name = bundle.getSymbolicName();
        this.version = bundle.getVersion();
    }

    @Override
    public String getSymbolicName() {
        return this.name;
    }

    @Override
    public Version getVersion() {
        return this.version;
    }

    @Override
    public boolean isResolved() {
        return false;
    }

    @Override
    public void uninstall() {
    }

    @Override
    public Bundle getBundle() {
        return null;
    }

    @Override
    public long getBundleId() {
        return this.id;
    }

    @Override
    public List<QuasiBundle> getFragments() {
        return new ArrayList<QuasiBundle>();
    }

    @Override
    public List<QuasiBundle> getHosts() {
        return new ArrayList<QuasiBundle>();
    }

    @Override
    public List<QuasiExportPackage> getExportPackages() {
        return new ArrayList<QuasiExportPackage>();
    }

    @Override
    public List<QuasiImportPackage> getImportPackages() {
        return new ArrayList<QuasiImportPackage>();
    }

    @Override
    public List<QuasiRequiredBundle> getRequiredBundles() {
        return new ArrayList<QuasiRequiredBundle>();
    }

    @Override
    public List<QuasiBundle> getDependents() {
        return new ArrayList<QuasiBundle>();
    }

    @Override
    public File getBundleFile() {
        return null;
    }

    @Override
    public void setProvisioning(Provisioning provisioning) {
        this.provisioning = provisioning;

    }

    @Override
    public Provisioning getProvisioning() {
        return this.provisioning;
    }

	@Override
	public Region getRegion() {
		return null;
	}

}