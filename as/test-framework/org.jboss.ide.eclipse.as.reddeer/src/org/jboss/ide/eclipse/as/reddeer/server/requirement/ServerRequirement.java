package org.jboss.ide.eclipse.as.reddeer.server.requirement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.jboss.ide.eclipse.as.reddeer.server.family.FamilyEAP;
import org.jboss.ide.eclipse.as.reddeer.server.family.FamilyWildFly;
import org.jboss.ide.eclipse.as.reddeer.server.requirement.ServerRequirement.JBossServer;
import org.jboss.ide.eclipse.as.reddeer.server.wizard.page.JBossRuntimeWizardPage;
import org.jboss.ide.eclipse.as.reddeer.server.wizard.page.NewServerAdapterPage;
import org.jboss.ide.eclipse.as.reddeer.server.wizard.page.NewServerAdapterPage.Profile;
import org.jboss.ide.eclipse.as.reddeer.server.wizard.page.NewServerRSIWizardPage;
import org.jboss.ide.eclipse.as.reddeer.server.wizard.page.NewServerWizardPageWithErrorCheck;
import org.jboss.reddeer.common.exception.RedDeerException;
import org.jboss.reddeer.common.logging.Logger;
import org.jboss.reddeer.eclipse.rse.ui.view.System;
import org.jboss.reddeer.eclipse.rse.ui.view.SystemViewPart;
import org.jboss.reddeer.eclipse.rse.ui.wizards.newconnection.RSEDefaultNewConnectionWizardMainPage;
import org.jboss.reddeer.eclipse.rse.ui.wizards.newconnection.RSEMainNewConnectionWizard;
import org.jboss.reddeer.eclipse.rse.ui.wizards.newconnection.RSENewConnectionWizardSelectionPage;
import org.jboss.reddeer.eclipse.rse.ui.wizards.newconnection.RSENewConnectionWizardSelectionPage.SystemType;
import org.jboss.reddeer.eclipse.wst.server.ui.wizard.NewServerWizard;
import org.jboss.reddeer.junit.requirement.CustomConfiguration;
import org.jboss.reddeer.junit.requirement.Requirement;
import org.jboss.reddeer.requirements.server.ConfiguredServerInfo;
import org.jboss.reddeer.requirements.server.ServerReqBase;
import org.jboss.reddeer.requirements.server.ServerReqState;

import static org.junit.Assert.assertTrue;


/**
 * 
 * @author psrna, Radoslav Rabara
 *
 */

public class ServerRequirement extends ServerReqBase implements Requirement<JBossServer>, CustomConfiguration<ServerRequirementConfig> {

	private static final Logger LOGGER = Logger.getLogger(ServerRequirement.class);
	
	private static ConfiguredServerInfo lastServerConfiguration;
	
	private ServerRequirementConfig config;
	private JBossServer server;
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface JBossServer {
		ServerReqState state() default ServerReqState.RUNNING;
		ServerReqType type() default ServerReqType.ANY;
		ServerReqVersion version() default ServerReqVersion.EQUAL;
		boolean cleanup() default true;
	}
	
	
	@Override
	public boolean canFulfill() {
		//requirement can be fulfilled only when required server's type and version matches to
		//configured server's type and version
		return ServerMatcher.matchServerFamily(server.type().getFamily(), config.getServerFamily()) &&
				ServerMatcher.matchServerVersion(server.type().getVersion(), server.version(),
				config.getServerFamily().getVersion());
	}

	@Override
	public void fulfill() {
		if(lastServerConfiguration != null) {
			boolean differentConfig = !config.equals(lastServerConfiguration.getConfig());
			if (differentConfig) {
				removeLastRequiredServerAndRuntime();
				lastServerConfiguration = null;
			}
		}
		if (lastServerConfiguration == null || !isLastConfiguredServerPresent()) {
			LOGGER.info("Setup server");
			if(config.getRemote() == null)
				setupLocalServerAdapter();
			else
				setupRemoteServerAdapter();
			lastServerConfiguration = new ConfiguredServerInfo(getServerNameLabelText(), config);
		}
		setupServerState(server.state());
	}
	
	@Override
	public void setDeclaration(JBossServer server) {
		this.server = server;
	}

	@Override
	public Class<ServerRequirementConfig> getConfigurationClass() {
		return ServerRequirementConfig.class;
	}

	@Override
	public void setConfiguration(ServerRequirementConfig config) {
		this.config = config;
	}
	
	@Override
	public void cleanUp() {
		if(server.cleanup() && config != null){
			removeLastRequiredServerAndRuntime();
			lastServerConfiguration = null;
		}
	}

	public ServerRequirementConfig getConfig() {
		return this.config;
	}
	
	@Override
	public String getServerNameLabelText() {
		if(this.config.getRemote() == null)
			return super.getServerNameLabelText();
		else
			return super.getServerTypeLabelText() + " Remote Server";
	}
	
	protected void setupLocalServerAdapter() {
		NewServerWizard serverW = new NewServerWizard();
		try {
			serverW.open();

			NewServerWizardPageWithErrorCheck sp = new NewServerWizardPageWithErrorCheck();
			
			String serverTypeLabelText = getServerTypeLabelText();
			
			//workaround for JBIDE-20548
			if(FamilyWildFly.class.equals(config.getServerFamily().getClass())){
				String label = config.getServerFamily().getLabel();
				String version = config.getServerFamily().getVersion();
				if(version.equals("8.x")){
					serverTypeLabelText = label+"  "+version;
				}
				if(version.equals("9.x")){
					serverTypeLabelText = label+"  "+version;
				}
				if(version.equals("10.x")){
					serverTypeLabelText = label+" "+ version;
				}
			}
			if(FamilyEAP.class.equals(config.getServerFamily().getClass())){
				String label = config.getServerFamily().getLabel();
				String version = config.getServerFamily().getVersion();
				if(version.equals("7.x")){
					serverTypeLabelText = label+" 7.x";
				}
			}
			sp.selectType(config.getServerFamily().getCategory(), serverTypeLabelText);
			sp.setName(getServerNameLabelText());

			sp.checkErrors();

			serverW.next();

			NewServerAdapterPage ap = new NewServerAdapterPage();
			ap.setRuntime(null);
			ap.checkErrors();

			serverW.next();

			setupRuntime();

			serverW.finish();
		} catch(RuntimeException e) {
			try{
				serverW.cancel();
			} catch (RedDeerException ex){
				throw e;
			}
			throw e;
		} catch(AssertionError e) {
			try{
				serverW.cancel();
			} catch (RedDeerException ex){
				throw e;
			}
			throw e;
		}
	}
	
	protected void setupRuntime(){
		
		JBossRuntimeWizardPage rp = new JBossRuntimeWizardPage();
		rp.setRuntimeName(getRuntimeNameLabelText());
		rp.setRuntimeDir(config.getRuntime());

		rp.checkErrors();
		
	}

	protected void setupRemoteSystem(){
		
		SystemViewPart sview = new SystemViewPart();
		RSEMainNewConnectionWizard connW = sview.newConnection();
		RSENewConnectionWizardSelectionPage sp = new RSENewConnectionWizardSelectionPage();
		sp.selectSystemType(SystemType.SSH_ONLY);
		connW.next();
		RSEDefaultNewConnectionWizardMainPage mp = new RSEDefaultNewConnectionWizardMainPage();
		mp.setHostName(config.getRemote().getHost());
		connW.finish();
		
		System system = sview.getSystem(config.getRemote().getHost());
		system.connect(config.getRemote().getUsername(), config.getRemote().getPassword());
				
		assertTrue(system.isConnected());
		
	}
	
	protected void setupRemoteServerAdapter() {
		NewServerWizard serverW = new NewServerWizard();
		try {
			//setup remote system first
			setupRemoteSystem();

			//-- Open 'New Server' wizard 
			serverW.open();
			//-- Select the server type and fill in server name, then continue on next page
			NewServerWizardPageWithErrorCheck sp = new NewServerWizardPageWithErrorCheck();
			sp.selectType(config.getServerFamily().getCategory(),getServerTypeLabelText());
			sp.setName(getServerNameLabelText());
			sp.checkErrors();
			serverW.next();
			
			//-- Select server profile (Remote)
			NewServerAdapterPage ap = new NewServerAdapterPage();
			ap.setProfile(Profile.REMOTE);
			//Remote server can be configured without local runtime if runtime is not specified
			if(config.getRuntime() == null)
				ap.setAssignRuntime(false);
			serverW.next();
			
			if(config.getRuntime() != null){
				//create new runtime
				setupRuntime();
				serverW.next();
			}
			
			NewServerRSIWizardPage rsp = new NewServerRSIWizardPage();
			rsp.setRemoteServerHome(config.getRemote().getRemoteServerHome());
			rsp.selectHost(config.getRemote().getHost()); //host was configured in setupRemoteSystem 
			serverW.finish();
			
		} catch(RuntimeException e) {
			serverW.cancel();
			throw e;
		} catch(AssertionError e) {
			serverW.cancel();
			throw e;
		}
	}

	@Override
	public ConfiguredServerInfo getConfiguredConfig() {
		return lastServerConfiguration;
	}

}
