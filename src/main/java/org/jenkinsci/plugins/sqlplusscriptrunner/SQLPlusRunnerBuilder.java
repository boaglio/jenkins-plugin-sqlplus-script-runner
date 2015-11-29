package org.jenkinsci.plugins.sqlplusscriptrunner;

import java.io.File;
import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;

public class SQLPlusRunnerBuilder extends Builder {

	private static final String ENV_ORACLE_HOME = "ORACLE_HOME";
	private static final String WORKSPACE_DIR = "workspace";
	private static final String BUILDS_DIR = "builds";
	private static final String LINE = Messages.SQLPlusRunnerBuilder_line();
	private static final String MSG_GET_ORACLE_HOME = Messages.SQLPlusRunnerBuilder_gettingOracleHome();
	private static final String MSG_CUSTOM_ORACLE_HOME = Messages.SQLPlusRunnerBuilder_usingCustomOracleHome();
	private static final String MSG_GLOBAL_ORACLE_HOME = Messages.SQLPlusRunnerBuilder_usingGlobalOracleHome();
	private static final String MSG_DETECTED_ORACLE_HOME = Messages.SQLPlusRunnerBuilder_usingDetectedOracleHome();

	private final String user;
	private final String password;
	private final String instance;
	private final String scriptType;
	private final String script;
	private final String scriptContent;
	private final String customOracleHome;

	@DataBoundConstructor
	public SQLPlusRunnerBuilder(String user,String password,String instance,String scriptType,String script,String scriptContent,String customOracleHome) {
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
	public boolean perform(AbstractBuild build,Launcher launcher,BuildListener listener) throws InterruptedException,IOException {

		String buildPath = build.getRootDir().getCanonicalPath();
		String selectedOracleHome = null;
		String detectedOracleHome = System.getenv(ENV_ORACLE_HOME);

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_GET_ORACLE_HOME);
		if (customOracleHome != null && customOracleHome.length() > 0) {
			listener.getLogger().println(LINE);
			listener.getLogger().println(MSG_CUSTOM_ORACLE_HOME);
			selectedOracleHome = customOracleHome;
		} else if (getDescriptor().oracleHome != null && getDescriptor().oracleHome.length() > 0) {
			listener.getLogger().println(LINE);
			listener.getLogger().println(MSG_GLOBAL_ORACLE_HOME);
			selectedOracleHome = getDescriptor().getOracleHome();
		} else if (getDescriptor().tryToDetectOracleHome && detectedOracleHome != null) {
			listener.getLogger().println(LINE);
			listener.getLogger().println(MSG_DETECTED_ORACLE_HOME);
			selectedOracleHome = detectedOracleHome;
		} else {
			selectedOracleHome = getDescriptor().getOracleHome();
		}

		String sqlPath = buildPath.substring(0,buildPath.indexOf(BUILDS_DIR)) + File.separator + WORKSPACE_DIR;

		File dirSQLPath = new File(sqlPath);
		if (!dirSQLPath.exists()) {
			dirSQLPath.mkdirs();
		}

		try {
			SQLPlusRunner sqlPlusRunner = new SQLPlusRunner(listener);
			if (!getDescriptor().isHideSQLPlusVersion()) {
				sqlPlusRunner.runGetSQLPLusVersion(sqlPath,selectedOracleHome);
			}
			if (ScriptType.userDefined.name().equals(scriptType)) {
				sqlPlusRunner.runScript(user,password,instance,scriptContent,sqlPath,selectedOracleHome,scriptType);
			} else {
				sqlPlusRunner.runScript(user,password,instance,script,sqlPath,selectedOracleHome,scriptType);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
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
		private boolean hideSQLPlusVersion;
		private boolean tryToDetectOracleHome;
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
		public boolean configure(StaplerRequest req,JSONObject formData) throws FormException {
			hideSQLPlusVersion = formData.getBoolean(HIDE_SQL_PLUS_VERSION);
			oracleHome = formData.getString(ORACLE_HOME);
			tryToDetectOracleHome = formData.getBoolean(TRY_TO_DETECT_ORACLE_HOME);
			save();
			return super.configure(req,formData);
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

		public String getOracleHome() {
			return oracleHome;
		}

		public void setOracleHome(String oracleHome) {
			this.oracleHome = oracleHome;
		}

	}
}
