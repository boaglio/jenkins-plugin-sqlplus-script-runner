package org.jenkinsci.plugins.sqlplusscriptrunner;

import java.io.IOException;
import java.util.List;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings
public class SQLPlusRunnerBuilder extends Builder {

	private final String credentialsId;
	private final String user;
	private final String password;
	private final String instance;
	private final String scriptType;
	private final String script;
	private final String scriptContent;
	private final String customOracleHome;
	private final String customSQLPlusHome;
	private final String customTNSAdmin;

	/**
	 * @deprecated removed user and password in favour of credentials
	 */
	@Deprecated
	public SQLPlusRunnerBuilder(String user, String password, String instance, String scriptType, String script,
			String scriptContent, String customOracleHome,String customSQLPlusHome,String customTNSAdmin) {
		this.credentialsId = null;
		this.user = user;
		this.password = password;
		this.instance = instance;
		this.scriptType = scriptType;
		this.script = script;
		this.scriptContent = scriptContent;
		this.customOracleHome = customOracleHome;
		this.customSQLPlusHome = customSQLPlusHome;
		this.customTNSAdmin = customTNSAdmin;
	}

	@DataBoundConstructor
	public SQLPlusRunnerBuilder(String credentialsId, String instance, String scriptType, String script,
			String scriptContent, String customOracleHome,String customSQLPlusHome,String customTNSAdmin) {
		this.credentialsId = credentialsId;
		this.user = null;
		this.password = null;
		this.instance = instance;
		this.scriptType = scriptType;
		this.script = script;
		this.scriptContent = scriptContent;
		this.customOracleHome = customOracleHome;
		this.customSQLPlusHome = customSQLPlusHome;
		this.customTNSAdmin = customTNSAdmin;
	}

	public String getUser() {
		return user;
	}

	public String getPassword() {
		return password;
	}

	public String getInstance() {
		return instance;
	}

	public String getScriptType() {
		return scriptType;
	}

	public String getScript() {
		return script;
	}

	public String getScriptContent() {
		return scriptContent;
	}

	public String getCustomOracleHome() {
		return customOracleHome;
	}

	public String getCustomSQLPlusHome() {
		return customSQLPlusHome;
	}

	public String getCustomTNSAdmin() {
		return customTNSAdmin;
	}

	@Override
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {

		String sqlScript;
		if (ScriptType.userDefined.name().equals(scriptType)) {
			sqlScript = scriptContent;
		} else {
			sqlScript = script;
		}

		List<StandardUsernamePasswordCredentials> lookupCredentials = CredentialsProvider.lookupCredentials(
				StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, null, null);
		CredentialsMatcher credentialsMatcher = CredentialsMatchers.withId(credentialsId);
		StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(lookupCredentials,
				credentialsMatcher);
		if (credentials == null && (this.user == null)) {
			throw new AbortException("Invalid credentials " + credentialsId
					+ ". Failed to initialize credentials or load user and pass");
		}
		final Secret password = credentials.getPassword();

		final String usr = credentials == null ? this.user : credentials.getUsername();
		final String pwd = credentials == null ? this.password : password.getPlainText();

		EnvVars env = build.getEnvironment(listener);

		SQLPlusRunner sqlPlusRunner = new SQLPlusRunner(build,listener, launcher, getDescriptor().isHideSQLPlusVersion(), usr,
				pwd, env.expand(instance), env.expand(sqlScript), getDescriptor().oracleHome, scriptType,
				customOracleHome,customSQLPlusHome,customTNSAdmin, getDescriptor().tryToDetectOracleHome, getDescriptor().isDebug());

		try {

			sqlPlusRunner.run();

		} catch (Exception e) {

			e.printStackTrace(listener.getLogger());
			throw new AbortException(e.getMessage());
		}

		return true;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		private static final String DISPLAY_MESSAGE = "SQLPlus Script Runner";
		private static final String ORACLE_HOME = "oracleHome";
		private static final String HIDE_SQL_PLUS_VERSION = "hideSQLPlusVersion";
		private static final String TRY_TO_DETECT_ORACLE_HOME = "tryToDetectOracleHome";
		private static final String DEBUG = "debug";
		private boolean hideSQLPlusVersion;
		private boolean tryToDetectOracleHome;
		private boolean debug;
		private String oracleHome;

		public DescriptorImpl() {
			load();
		}

		@Override
		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return DISPLAY_MESSAGE;
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			hideSQLPlusVersion = formData.getBoolean(HIDE_SQL_PLUS_VERSION);
			oracleHome = formData.getString(ORACLE_HOME);
			tryToDetectOracleHome = formData.getBoolean(TRY_TO_DETECT_ORACLE_HOME);
			debug = formData.getBoolean(DEBUG);
			save();
			return super.configure(req, formData);
		}

		public boolean isHideSQLPlusVersion() {
			return hideSQLPlusVersion;
		}

		public void setHideSQLPlusVersion(boolean hideSQLPlusVersion) {
			this.hideSQLPlusVersion = hideSQLPlusVersion;
		}

		public boolean isTryToDetectOracleHome() {
			return tryToDetectOracleHome;
		}

		public void setTryToDetectOracleHome(boolean tryToDetectOracleHome) {
			this.tryToDetectOracleHome = tryToDetectOracleHome;
		}

		public boolean isDebug() {
			return debug;
		}

		public void setDebug(boolean debug) {
			this.debug = debug;
		}

		public String getOracleHome() {
			return oracleHome;
		}

		public void setOracleHome(String oracleHome) {
			this.oracleHome = oracleHome;
		}

		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup<?> context) {
			if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance())
					.hasPermission(Computer.CONFIGURE)) {
				return new ListBoxModel();
			}
			return new StandardUsernameListBoxModel().withMatching(new CredentialsMatcher() {
				private static final long serialVersionUID = 1L;

				@Override
				public boolean matches(Credentials item) {
					return item instanceof UsernamePasswordCredentialsImpl;
				}
			}, CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context, ACL.SYSTEM, null,
					null));
		}
	}
}
