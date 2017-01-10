package org.jenkinsci.plugins.sqlplusscriptrunner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import hudson.EnvVars;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamCopyThread;
import jenkins.MasterToSlaveFileCallable;

/**
 * Run SQLPlus commands on the slave, or master of Jenkins.
 */
public class SQLPlusRunner extends MasterToSlaveFileCallable<Void> {

	private static final String WINDOWS_OS = "win";

	private static final String OPERATION_SYSTEM = "os.name";

	/*
	 * serial UID.
	 */
	private static final long serialVersionUID = -8984348187608947947L;

	private static final String MSG_TEMP_SCRIPT = Messages.SQLPlusRunner_tempScript();

	private static final String ON = Messages.SQLPlusRunner_on();

	private static final String MSG_ORACLE_HOME = Messages.SQLPlusRunner_usingOracleHome();

	private static final String MSG_SCRIPT = Messages.SQLPlusRunner_runningScript();

	private static final String MSG_DEFINED_SCRIPT = Messages.SQLPlusRunner_runningDefinedScript();

	private static final String AT = "@";

	private static final String SLASH = "/";

	private static final String MSG_ERROR = Messages.SQLPlusRunner_error();

	private static final String MSG_GET_SQL_PLUS_VERSION = Messages.SQLPlusRunner_gettingSQLPlusVersion();

	private static final String MSG_ORACLE_HOME_MISSING = Messages.SQLPlusRunner_missingOracleHome();

	private static final String MSG_GET_ORACLE_HOME = Messages.SQLPlusRunner_gettingOracleHome();
	private static final String MSG_CUSTOM_ORACLE_HOME = Messages.SQLPlusRunner_usingCustomOracleHome();
	private static final String MSG_GLOBAL_ORACLE_HOME = Messages.SQLPlusRunner_usingGlobalOracleHome();
	private static final String MSG_DETECTED_ORACLE_HOME = Messages.SQLPlusRunner_usingDetectedOracleHome();
	private static final String LOCAL_DATABASE_MSG = "local";

	private static final String SQL_TEMP_SCRIPT = "temp-script-";

	private static final String SQL_PREFIX = ".sql";

	private static final String HIDDEN_PASSWORD = "********";

	private static final String LINE = Messages.SQLPlusRunner_line();

	// For executing commands
	private static final String EOL = "\n";
	private static final String LIB_DIR = "lib";
	private static final String BIN_DIR = "bin";
	private static final String NET_DIR = "network" + File.separator + "admin";

	private static final String ENV_LD_LIBRARY_PATH = "LD_LIBRARY_PATH";
	private static final String ENV_ORACLE_HOME = "ORACLE_HOME";
	private static final String ENV_TNS_ADMIN = "TNS_ADMIN";

	private static final String SQLPLUS_TRY_LOGIN_JUST_ONCE = "-L";
	private static final String SQLPLUS = "sqlplus";
	private static final String SQLPLUS_FOR_WINDOWS = "sqlplus.exe";
	private static final String SQLPLUS_EXIT = EOL + "exit;" + EOL + "exit;";
	private static final String SQLPLUS_VERSION = "-v";

	public SQLPlusRunner(BuildListener listener,boolean isHideSQLPlusVersion,String user,String password,String instance,String script,String oracleHome,String scriptType,String customOracleHome,boolean tryToDetectOracleHome) {
		this.listener = listener;
		this.isHideSQLPlusVersion = isHideSQLPlusVersion;
		this.user = user;
		this.password = password;
		this.instance = instance;
		this.script = script;
		this.oracleHome = oracleHome;
		this.scriptType = scriptType;
		this.customOracleHome = customOracleHome;
		this.tryToDetectOracleHome = tryToDetectOracleHome;
	}

	private final BuildListener listener;

	private final boolean isHideSQLPlusVersion;

	private final String user;

	private final String password;

	private final String instance;

	private String script;

	private final String oracleHome;

	private final String customOracleHome;

	private final String scriptType;

	private final boolean tryToDetectOracleHome;

	@Override
	public Void invoke(File path,VirtualChannel channel) throws IOException,InterruptedException {

		String selectedOracleHome = null;
		String detectedOracleHome = System.getenv(ENV_ORACLE_HOME);

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_GET_ORACLE_HOME);
		// custom ORACLE_HOME overrides everything
		if (customOracleHome != null && customOracleHome.length() > 0) {
			listener.getLogger().println(LINE);
			listener.getLogger().println(MSG_CUSTOM_ORACLE_HOME);
			selectedOracleHome = customOracleHome;
			// global ORACLE_HOME comes next
		} else if (oracleHome != null && oracleHome.length() > 0) {
			listener.getLogger().println(LINE);
			listener.getLogger().println(MSG_GLOBAL_ORACLE_HOME);
			selectedOracleHome = oracleHome;
			// now try to detect ORACLE_HOME
		} else if (tryToDetectOracleHome && detectedOracleHome != null) {
			listener.getLogger().println(LINE);
			listener.getLogger().println(MSG_DETECTED_ORACLE_HOME);
			selectedOracleHome = detectedOracleHome;
		} else {
			// nothing works, get global ORACLE_HOME
			selectedOracleHome = oracleHome;
		}

		if (!isHideSQLPlusVersion) {
			runGetSQLPLusVersion(selectedOracleHome,listener);
		}

		if (selectedOracleHome == null || selectedOracleHome.length() < 1) { throw new RuntimeException(MSG_ORACLE_HOME_MISSING); }

		File directoryAccessTest = new File(selectedOracleHome);
		if (!directoryAccessTest.exists()) { throw new RuntimeException(Messages.SQLPlusRunner_wrongOracleHome(selectedOracleHome)); }

		if (script == null || script.length() < 1) { throw new RuntimeException(Messages.SQLPlusRunner_missingScript(path)); }

		String instanceStr = LOCAL_DATABASE_MSG;
		if (instance != null) {
			instanceStr = instance;
		}

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_ORACLE_HOME + selectedOracleHome);
		listener.getLogger().println(LINE);

		if (ScriptType.userDefined.name().equals(scriptType)) {
			listener.getLogger().println(MSG_DEFINED_SCRIPT + " " + user + SLASH + HIDDEN_PASSWORD + AT + instanceStr);
			script = createTempScript(script);
			listener.getLogger().println(MSG_TEMP_SCRIPT + script);
		} else {
			listener.getLogger().println(MSG_SCRIPT + " " + path + File.separator + script + " " + ON + " " + user + SLASH + HIDDEN_PASSWORD + AT + instanceStr);
			File scriptFile = new File(path + File.separator + script);
			if (!scriptFile.exists()) { throw new RuntimeException(Messages.SQLPlusRunner_missingScript(path + File.separator + script)); }
			if (!FileUtil.hasExitCode(scriptFile))
			 addExit(null,scriptFile);
		}

		listener.getLogger().println(LINE);

		try {
			// and the extra ones for the plugin
			EnvVars envVars = new EnvVars();
			envVars.put(ENV_ORACLE_HOME,selectedOracleHome);
			envVars.put(ENV_LD_LIBRARY_PATH,selectedOracleHome + File.separator + LIB_DIR);
			envVars.put(ENV_TNS_ADMIN,selectedOracleHome + File.separator + NET_DIR);

			// create command arguments
			ArgumentListBuilder args = new ArgumentListBuilder();
			String arg1 = user + SLASH + password;
			if (instance != null) {
				arg1 = arg1 + AT + instance;
			}

			String arg2 = "";

			if (ScriptType.userDefined.name().equals(scriptType)) {
				arg2 = script;
			} else {
				arg2 = path + File.separator + script;
			}

			String sqlplus = SQLPLUS;
			if (isWindowsOS()) {
				sqlplus = SQLPLUS_FOR_WINDOWS;
			}

			args.add(selectedOracleHome + File.separator + BIN_DIR + File.separator + sqlplus);
			args.add(SQLPLUS_TRY_LOGIN_JUST_ONCE);
			args.add(arg1);
			args.add(AT + arg2);

			ProcessBuilder proc = new ProcessBuilder(args.toList());
			proc.environment().putAll(envVars);
			proc.redirectErrorStream(true);
			proc.directory(path);

			Process process = proc.start();
			int exitCode = process.waitFor();

			new StreamCopyThread(Messages.SQLPlusRunner_errorLogRunner(),process.getErrorStream(),listener.getLogger(),false).start();
			new StreamCopyThread(Messages.SQLPlusRunner_logRunner(),process.getInputStream(),listener.getLogger(),false).start();

			listener.getLogger().printf(Messages.SQLPlusRunner_processEnd() + " %d%n",exitCode);
		} catch (Exception e) {
			listener.getLogger().println(MSG_ERROR + e.getMessage());
			throw new RuntimeException(e);
		}

		listener.getLogger().println(LINE);
		return null;
	}

	public void runGetSQLPLusVersion(String oracleHome,BuildListener listener) {

		if (oracleHome == null || oracleHome.length() < 1) { throw new RuntimeException(MSG_ORACLE_HOME_MISSING); }

		File directoryAccessTest = new File(oracleHome);
		if (!directoryAccessTest.exists()) { throw new RuntimeException(Messages.SQLPlusRunner_wrongOracleHome(oracleHome)); }

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_ORACLE_HOME + oracleHome);
		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_GET_SQL_PLUS_VERSION);
		try {
			EnvVars envVars = new EnvVars();
			envVars.put(ENV_ORACLE_HOME,oracleHome);
			envVars.put(ENV_LD_LIBRARY_PATH,oracleHome + File.separator + LIB_DIR);

			// create command arguments
			ArgumentListBuilder args = new ArgumentListBuilder();

			String sqlplus = SQLPLUS;
			if (isWindowsOS()) {
				sqlplus = SQLPLUS_FOR_WINDOWS;
			}

			args.add(oracleHome + File.separator + BIN_DIR + File.separator + sqlplus);
			args.add(SQLPLUS_VERSION);

			ProcessBuilder proc = new ProcessBuilder(args.toList());
			proc.environment().putAll(envVars);
			proc.redirectErrorStream(true);
			Process process = proc.start();
			int exitCode = process.waitFor();

			new StreamCopyThread(Messages.SQLPlusRunner_errorLogVersion(),process.getErrorStream(),listener.getLogger(),false).start();
			new StreamCopyThread(Messages.SQLPlusRunner_logVersion(),process.getInputStream(),listener.getLogger(),false).start();

			listener.getLogger().printf(Messages.SQLPlusRunner_processEnd() + " %d%n",exitCode);

		} catch (Exception e) {
			listener.getLogger().println(MSG_ERROR + e.getMessage());
			throw new RuntimeException(e);
		}
		listener.getLogger().println(LINE);
	}

	private String createTempScript(String content) {

		String tempFile = "";
		try {

			File file = File.createTempFile(SQL_TEMP_SCRIPT + System.currentTimeMillis(),SQL_PREFIX);

			if (!FileUtil.hasExitCode(file))
			  addExit(content,file);

			tempFile = file.getPath();

		} catch (IOException e) {
			e.printStackTrace();
		}
		return tempFile;

	}

	private void addExit(String content,File file) throws IOException {

		BufferedWriter bw = null;
		try {

			FileWriter fw = new FileWriter(file.getAbsoluteFile(),true);
			bw = new BufferedWriter(fw);
			if (content != null) {
				bw.write(content);
			}
			bw.write(SQLPLUS_EXIT);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				bw.close();
			} catch (IOException e) {}

		}
	}

	private boolean isWindowsOS() {

		return System.getProperty(OPERATION_SYSTEM).toLowerCase().indexOf(WINDOWS_OS) >= 0;

	}

}
