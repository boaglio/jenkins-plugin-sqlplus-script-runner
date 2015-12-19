package org.jenkinsci.plugins.sqlplusscriptrunner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

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

	/*
	 * serial UID.
	 */
	private static final long serialVersionUID = -8984348187608947947L;

	private static final String MSG_TEMP_SCRIPT = Messages.SQLPlusRunner_tempScript();

	private static final String ON = " on ";

	private static final String MSG_ORACLE_HOME = Messages.SQLPlusRunner_usingOracleHome();

	private static final String MSG_SCRIPT = Messages.SQLPlusRunner_runningScript();

	private static final String MSG_DEFINED_SCRIPT = Messages.SQLPlusRunner_runningDefinedScript();

	private static final String AT = "@";

	private static final String SLASH = "/";

	private static final String MSG_ERROR = Messages.SQLPlusRunner_error();

	private static final String MSG_GET_SQL_PLUS_VERSION = Messages.SQLPlusRunner_gettingSQLPlusVersion();

	private static final String MSG_ORACLE_HOME_MISSING = Messages.SQLPlusRunner_missingOracleHome();

	private static final String LOCAL_DATABASE_MSG = "local";

	private static final String SQL_TEMP_SCRIPT = "temp-script-";

	private static final String SQL_PREFIX = ".sql";

	private static final String HIDDEN_PASSWORD = "********";

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
	private static final String SQLPLUS_EXIT = EOL + "exit;" + EOL + "exit;";
	private static final String SQLPLUS_VERSION = "-v";

	public SQLPlusRunner(BuildListener listener,Map<String, String> vars,boolean isHideSQLPlusVersion,String user,String password,String instance,String script,String oracleHome,String scriptType) {
		this.listener = listener;
		this.vars = Collections.unmodifiableMap(vars);
		this.isHideSQLPlusVersion = isHideSQLPlusVersion;
		this.user = user;
		this.password = password;
		this.instance = instance;
		this.script = script;
		this.oracleHome = oracleHome;
		this.scriptType = scriptType;
	}

	private final BuildListener listener;

	private final boolean isHideSQLPlusVersion;

	private final String user;

	private final String password;

	private final String instance;

	private String script;

	private final String oracleHome;

	private final String scriptType;

	private final Map<String, String> vars;

	private static final String LINE = "--------------------------------------------------------------------------";

	@Override
	public Void invoke(File path, VirtualChannel channel) throws IOException, InterruptedException {
		// create environment vars
		final EnvVars envVars = new EnvVars();
		// adding vars from the build
		envVars.putAll(vars);

		if (!isHideSQLPlusVersion) {
			this.runGetSQLPLusVersion(path,envVars,oracleHome,listener);
		}

		if (oracleHome == null || oracleHome.length() < 1) { throw new RuntimeException(MSG_ORACLE_HOME_MISSING); }

		File directoryAccessTest = new File(oracleHome);
		if (!directoryAccessTest.exists()) { throw new RuntimeException(Messages.SQLPlusRunner_wrongOracleHome(oracleHome)); }

		if (script == null || script.length() < 1) { throw new RuntimeException(Messages.SQLPlusRunner_missingScript(path)); }

		String instanceStr = LOCAL_DATABASE_MSG;
		if (instance != null) {
			instanceStr = instance;
		}

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_ORACLE_HOME + oracleHome);
		listener.getLogger().println(LINE);

		if (ScriptType.userDefined.name().equals(scriptType)) {
			listener.getLogger().println(MSG_DEFINED_SCRIPT + user + SLASH + HIDDEN_PASSWORD + AT + instanceStr);
			script = createTempScript(script);
			listener.getLogger().println(MSG_TEMP_SCRIPT + script);
		} else {
			listener.getLogger().println(MSG_SCRIPT + script + ON + user + SLASH + HIDDEN_PASSWORD + AT + instanceStr);
		}

		listener.getLogger().println(LINE);

		try {
			// and the extra ones for the plugin
			envVars.clear();
			envVars.putAll(vars);
			envVars.put(ENV_ORACLE_HOME,oracleHome);
			envVars.put(ENV_LD_LIBRARY_PATH,oracleHome + File.separator + LIB_DIR);
			envVars.put(ENV_TNS_ADMIN,oracleHome + File.separator + NET_DIR);

			// create command arguments
			ArgumentListBuilder args = new ArgumentListBuilder();
			String arg1 = user + SLASH + password;
			if (instance != null) {
				arg1 = arg1 + AT + instance;
			}

			String arg2 = script;

			args.add(oracleHome + File.separator + BIN_DIR + File.separator + SQLPLUS);
			args.add(SQLPLUS_TRY_LOGIN_JUST_ONCE);
			args.add(arg1);
			args.add(AT + arg2);
			
			ProcessBuilder proc = new ProcessBuilder(args.toList());
			proc.environment().putAll(envVars);
			proc.redirectErrorStream(true);
			Process process = proc.start();
			int exitCode = process.waitFor();
			new StreamCopyThread("stdout from sqlplusrunner slave", process.getErrorStream(), listener.getLogger()).start();
			listener.getLogger().printf("Process exited with status %d%n", exitCode);
		} catch (Exception e) {
			listener.getLogger().println(MSG_ERROR + e.getMessage());
			throw new RuntimeException(e);
		}

		listener.getLogger().println(LINE);
		return null;
	}

	private void runGetSQLPLusVersion(File sqlPath,EnvVars envVars, String oracleHome, BuildListener listener) {

		if (oracleHome == null || oracleHome.length() < 1) { throw new RuntimeException(MSG_ORACLE_HOME_MISSING); }

		File directoryAccessTest = new File(oracleHome);
		if (!directoryAccessTest.exists()) { throw new RuntimeException(Messages.SQLPlusRunner_wrongOracleHome(oracleHome)); }

		// never supposed to happen, and would print null
		//if (sqlPath == null) { throw new RuntimeException(Messages.SQLPlusRunner_missingScript(sqlPath)); }

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_ORACLE_HOME + oracleHome);
		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_GET_SQL_PLUS_VERSION);
		try {
			envVars.put(ENV_ORACLE_HOME,oracleHome);
			envVars.put(ENV_LD_LIBRARY_PATH,oracleHome + File.separator + LIB_DIR);
			
			// create command arguments
			ArgumentListBuilder args = new ArgumentListBuilder();
			args.add(oracleHome + File.separator + BIN_DIR + File.separator + SQLPLUS);
			args.add(SQLPLUS_VERSION);
			
			ProcessBuilder proc = new ProcessBuilder(args.toList());
			proc.environment().putAll(envVars);
			proc.redirectErrorStream(true);
			Process process = proc.start();
			int exitCode = process.waitFor();
			new StreamCopyThread("stdout from sqlplusrunner slave for sqlplus version", process.getErrorStream(), listener.getLogger()).start();
			listener.getLogger().printf("Process exited with status %d%n", exitCode);
		} catch (Exception e) {
			listener.getLogger().println(MSG_ERROR + e.getMessage());
			throw new RuntimeException(e);
		}
		listener.getLogger().println(LINE);
	}

	private String createTempScript(String content) {

		String tempFile = "";
		BufferedWriter bw = null;
		try {

			File file = File.createTempFile(SQL_TEMP_SCRIPT + System.currentTimeMillis(),SQL_PREFIX);

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			bw = new BufferedWriter(fw);
			bw.write(content);
			bw.write(SQLPLUS_EXIT);

			tempFile = file.getPath();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				bw.close();
			} catch (IOException e) {}

		}
		return tempFile;

	}

}
