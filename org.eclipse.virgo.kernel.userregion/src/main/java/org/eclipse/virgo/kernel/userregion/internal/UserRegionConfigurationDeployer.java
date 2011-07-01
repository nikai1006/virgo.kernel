package org.eclipse.virgo.kernel.userregion.internal;

import java.io.IOException;
import java.util.Properties;

import org.eclipse.virgo.kernel.deployer.config.ConfigurationDeployer;
import org.eclipse.virgo.kernel.osgi.framework.OsgiFrameworkUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * This service is registered in the user region so that it has access to the configuration admin in the 
 * user region. The kernel region can use it as a proxy to access the configuration admin in the user region. 
 * 
 * <strong>Concurrent Semantics</strong><br />
 * Thread-safe.
 */
public class UserRegionConfigurationDeployer implements ConfigurationDeployer {

	private ConfigurationAdmin configurationAdmin;
	private Object monitor = new Object();
	
	public UserRegionConfigurationDeployer(BundleContext context) {
		this.configurationAdmin = OsgiFrameworkUtils.getService(context, ConfigurationAdmin.class).getService();
	}
	
    /** 
     * {@inheritDoc}
     */
	@Override
	public void publishConfiguration(String pid, Properties configurationProperties) throws IOException {
		synchronized (monitor) {
			Configuration configuration = this.configurationAdmin.getConfiguration(pid, null);
			configuration.update(configurationProperties);
		}
	}

    /** 
     * {@inheritDoc}
     */
	@Override
	public void deleteConfiguration(String pid) throws IOException {
		synchronized (monitor) {
			Configuration configuration = this.configurationAdmin.getConfiguration(pid, null);
			configuration.delete();
		}
	}

}