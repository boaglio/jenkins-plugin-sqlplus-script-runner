package org.jenkinsci.plugins.sqlplusscriptrunner;

import java.io.IOException;
import java.util.Objects;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;

public class SQLPlusRunnerBuilder extends Builder {

	private final String user;
	private final String password;
	private final String instance;
	private final String scriptType;
	private final String script;
	private final String scriptContent;
	private final String customOracleHome;

	@DataBoundConstructor
	public SQLPlusRunnerBuilder(String user, String password, String instance, String scriptType, String script,
			String scriptContent, String customOracleHome) {
		this.user = user;
		this.password = password;
		this.instance = instance;
		this.scriptType = scriptType;
		this.script = script;
		this.scriptContent = scriptContent;
		this.customOracleHome = customOracleHome;
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

	@SuppressWarnings("rawtypes")
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {

		String sqlScript;
		if (ScriptType.userDefined.name().equals(scriptType)) {
			sqlScript = scriptContent;
		} else {
			sqlScript = script;
		}

		EnvVars env = build.getEnvironment(listener);
		sqlScript = env.expand(sqlScript);

		SQLPlusRunner sqlPlusRunner = new SQLPlusRunner(listener, getDescriptor().isHideSQLPlusVersion(), env.expand(user),
				password, instance, sqlScript, getDescriptor().oracleHome, scriptType, customOracleHome,
				getDescriptor().tryToDetectOracleHome,getDescriptor().isDebug());

		try {
			// The FilePath executing this callable can be used in the #invoke
			// method to get access to
			// the virtual file. Operations will happen either on the slave or
			// on the master node, and results
			// will be serialized back to the master.
			FilePath fp = build.getWorkspace();
			if (fp != null)
				fp.act(Objects.requireNonNull(sqlPlusRunner));
			else
				throw new AbortException("Filepath null!");
		} catch (Exception e) {
			// Either throw an abort exception, or just log the error, set build
			// result to failure or unstable
			// and then proceed with other build steps.
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

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
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

	}
}
