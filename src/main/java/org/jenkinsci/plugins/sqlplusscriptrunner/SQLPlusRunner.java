package org.jenkinsci.plugins.sqlplusscriptrunner;

import java.io.File;
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


	private static final String HIDDEN_PASSWORD = "********";

	private static final String LINE = Messages.SQLPlusRunner_line();

	// For executing commands
	private static final String LIB_DIR = "lib";
	private static final String BIN_DIR = "bin";
	private static final String NET_DIR = "network" + File.separator + "admin";

	private static final String ENV_LD_LIBRARY_PATH = "LD_LIBRARY_PATH";
	private static final String ENV_ORACLE_HOME = "ORACLE_HOME";
	private static final String ENV_TNS_ADMIN = "TNS_ADMIN";

	private static final String SQLPLUS_TRY_LOGIN_JUST_ONCE = "-L";
	private static final String SQLPLUS_VERSION = "-v";
	private static final String SQLPLUS = "sqlplus";
	private static final String SQLPLUS_FOR_WINDOWS = "sqlplus.exe";

	private static final String TNSNAMES_ORA = "tnsnames.ora";

	private static final int PROCESS_EXIT_CODE_SUCCESSFUL = 0;

	public SQLPlusRunner(BuildListener listener, boolean isHideSQLPlusVersion, String user, String password,
			String instance, String script, String oracleHome, String scriptType, String customOracleHome,
			boolean tryToDetectOracleHome,boolean debug) {
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
		this.debug = debug;
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

	private final boolean debug;

	@Override
	public Void invoke(File path, VirtualChannel channel) throws IOException, InterruptedException {

		String selectedOracleHome = null;
		String detectedOracleHome = System.getenv(ENV_ORACLE_HOME);

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_GET_ORACLE_HOME);
		// custom ORACLE_HOME overrides everything
		if (customOracleHome != null && customOracleHome.length() > 0) {
			if (debug) listener.getLogger().println("custom ORACLE_HOME selected");
			listener.getLogger().println(LINE);
			listener.getLogger().println(MSG_CUSTOM_ORACLE_HOME);
			selectedOracleHome = customOracleHome;
			// global ORACLE_HOME comes next
		} else if (oracleHome != null && oracleHome.length() > 0) {
			if (debug) listener.getLogger().println("global ORACLE_HOME selected");
			listener.getLogger().println(LINE);
			listener.getLogger().println(MSG_GLOBAL_ORACLE_HOME);
			selectedOracleHome = oracleHome;
			// now try to detect ORACLE_HOME
		} else if (tryToDetectOracleHome && detectedOracleHome != null) {
			if (debug) listener.getLogger().println("try to detect ORACLE_HOME selected");
			listener.getLogger().println(LINE);
			listener.getLogger().println(MSG_DETECTED_ORACLE_HOME);
			selectedOracleHome = detectedOracleHome;
		} else {
			// nothing works, get global ORACLE_HOME
			if (debug) listener.getLogger().println("global ORACLE_HOME selected anyway");
			selectedOracleHome = oracleHome;
		}

		if (!isHideSQLPlusVersion) {
			runGetSQLPLusVersion(selectedOracleHome, listener);
		}

		if (selectedOracleHome == null || selectedOracleHome.length() < 1) {
			throw new RuntimeException(MSG_ORACLE_HOME_MISSING);
		}

		File directoryAccessTest = new File(selectedOracleHome);
		if (debug) listener.getLogger().println("testing directory "+directoryAccessTest.getAbsolutePath());
		if (!directoryAccessTest.exists()) {
			throw new RuntimeException(Messages.SQLPlusRunner_wrongOracleHome(selectedOracleHome));
		}

		if (script == null || script.length() < 1) {
			throw new RuntimeException(Messages.SQLPlusRunner_missingScript(path));
		}

		String instanceStr = LOCAL_DATABASE_MSG;
		if (instance != null) {
			instanceStr = instance;
		}

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_ORACLE_HOME + selectedOracleHome);
		listener.getLogger().println(LINE);

		String tempScript = null;
		if (ScriptType.userDefined.name().equals(scriptType)) {
			listener.getLogger().println(MSG_DEFINED_SCRIPT + " " + user + SLASH + HIDDEN_PASSWORD + AT + instanceStr);
			script = FileUtil.createTempScript(script);
			tempScript = script;
			listener.getLogger().println(MSG_TEMP_SCRIPT + " "+ script);
		} else {
			listener.getLogger().println(MSG_SCRIPT + " " + path + File.separator + script + " " + ON + " " + user
					+ SLASH + HIDDEN_PASSWORD + AT + instanceStr);
			File scriptFile = new File(path + File.separator + script);
			if (debug) listener.getLogger().println("testing script "+scriptFile.getAbsolutePath());
			if (!scriptFile.exists()) {
				throw new RuntimeException(Messages.SQLPlusRunner_missingScript(path + File.separator + script));
			}
			if (!FileUtil.hasExitCode(scriptFile))
				FileUtil.addExit(null, scriptFile);
		}

		listener.getLogger().println(LINE);

		int exitCode = 0;
		try {
			// and the extra ones for the plugin
			EnvVars envVars = new EnvVars();
			File workDirectory = path;
			envVars.put(ENV_ORACLE_HOME, selectedOracleHome);
			if (debug) listener.getLogger().println("ORACLE_HOME = "+selectedOracleHome);
			envVars.put(ENV_LD_LIBRARY_PATH, selectedOracleHome + File.separator + LIB_DIR+File.pathSeparator+selectedOracleHome);
			if (debug) listener.getLogger().println("LD_LIBRARY_PATH = "+ selectedOracleHome + File.separator + LIB_DIR+File.pathSeparator+selectedOracleHome);

			boolean findTNSNAMESOracleHome = FileUtil.findFile( TNSNAMES_ORA ,new File(selectedOracleHome));
			boolean findTNSNAMESOracleHomeNetworkAdmin = FileUtil.findFile( TNSNAMES_ORA ,new File(selectedOracleHome + File.separator + NET_DIR));
			if (findTNSNAMESOracleHomeNetworkAdmin) {
			 envVars.put(ENV_TNS_ADMIN, selectedOracleHome + File.separator + NET_DIR);
			 if (debug) listener.getLogger().println("found TNSNAMES.ORA on "+new File(selectedOracleHome + File.separator + NET_DIR).getAbsolutePath());
			 if (debug) listener.getLogger().println("TNS_ADMIN = "+selectedOracleHome + File.separator + NET_DIR );
			} else if (findTNSNAMESOracleHome) {
			 envVars.put(ENV_TNS_ADMIN, selectedOracleHome);
			 if (debug) listener.getLogger().println("found TNSNAMES.ORA on "+new File(selectedOracleHome).getAbsolutePath());
			 if (debug) listener.getLogger().println("TNS_ADMIN = "+selectedOracleHome);
			 workDirectory = new File(selectedOracleHome);
			} else {
				throw new RuntimeException(Messages.SQLPlusRunner_missingTNSNAMES());
			}

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
			listener.getLogger().println("Work Directory = "+ workDirectory);
			listener.getLogger().println("SQL*Plus exec file = "+sqlplus);

			boolean findSQLPlusOnOracleHomeBin = FileUtil.findFile( sqlplus ,new File(selectedOracleHome + File.separator + BIN_DIR));

			boolean findSQLPlusOnOracleHome = FileUtil.findFile( sqlplus ,new File(selectedOracleHome));

			if (findSQLPlusOnOracleHomeBin) {
				if (debug) listener.getLogger().println("found SQL*Plus on "+new File(selectedOracleHome + File.separator + BIN_DIR).getAbsolutePath());
				args.add(selectedOracleHome + File.separator + BIN_DIR + File.separator + sqlplus);
			} else if (findSQLPlusOnOracleHome) {
				if (debug) listener.getLogger().println("found SQL*Plus on "+new File(selectedOracleHome).getAbsolutePath());
				args.add(selectedOracleHome + File.separator + File.separator + sqlplus);
			} else {
				throw new RuntimeException(Messages.SQLPlusRunner_missingSQLPlus());
			}

			args.add(SQLPLUS_TRY_LOGIN_JUST_ONCE);
			args.add(arg1);
			args.add(AT + arg2);

			if (debug) {
			 listener.getLogger().println(" Statement: ");
			 listener.getLogger().println(LINE);
			 for (String a: args.toList()) {
				listener.getLogger().print(a+" ");
			 }
			 listener.getLogger().println(LINE);
			 listener.getLogger().println(" ");
			}

			ProcessBuilder proc = new ProcessBuilder(args.toList());
			proc.environment().putAll(envVars);
			proc.redirectErrorStream(true);
			proc.directory(workDirectory);

			Process process = proc.start();
			exitCode = process.waitFor();

			new StreamCopyThread(Messages.SQLPlusRunner_errorLogRunner(), process.getErrorStream(),
					listener.getLogger(), false).start();
			new StreamCopyThread(Messages.SQLPlusRunner_logRunner(), process.getInputStream(), listener.getLogger(),
					false).start();

			listener.getLogger().printf(Messages.SQLPlusRunner_processEnd() + " %d%n", exitCode);

		} catch (RuntimeException e) {
		    throw e;
		} catch (Exception e) {
			listener.getLogger().println(MSG_ERROR + e.getMessage());
			throw new RuntimeException(e);
		} finally {
			if (tempScript != null) {
				try {
					File f = new File(tempScript);
					boolean removed = f.delete();
					if (!removed)
						listener.getLogger().printf(Messages.SQLPlusRunner_tempFileNotRemoved());
				} catch (Exception e) {
				}
			}
		}

		if (exitCode != PROCESS_EXIT_CODE_SUCCESSFUL) {
			listener.getLogger().println(LINE);
			listener.getLogger().println("Exit code: "+exitCode);
			listener.getLogger().println(LINE);
			throw new RuntimeException(Messages.SQLPlusRunner_processErrorEnd());
		}

		listener.getLogger().println(LINE);
		return null;
	}

	public void runGetSQLPLusVersion(String oracleHome, BuildListener listener) {

		if (oracleHome == null || oracleHome.length() < 1) {
			throw new RuntimeException(MSG_ORACLE_HOME_MISSING);
		}

		File directoryAccessTest = new File(oracleHome);
		if (!directoryAccessTest.exists()) {
			throw new RuntimeException(Messages.SQLPlusRunner_wrongOracleHome(oracleHome));
		}

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_ORACLE_HOME + oracleHome);
		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_GET_SQL_PLUS_VERSION);
		try {
			EnvVars envVars = new EnvVars();
			envVars.put(ENV_ORACLE_HOME, oracleHome);
			if (debug) listener.getLogger().println("ORACLE_HOME = "+oracleHome);
			envVars.put(ENV_LD_LIBRARY_PATH, oracleHome + File.separator + LIB_DIR);
			if (debug) listener.getLogger().println("LD_LIBRARY_PATH = "+oracleHome + File.separator + LIB_DIR);

			// create command arguments
			ArgumentListBuilder args = new ArgumentListBuilder();

			String sqlplus = SQLPLUS;
			if (isWindowsOS()) {
				sqlplus = SQLPLUS_FOR_WINDOWS;
			}

			listener.getLogger().println("SQL*Plus exec file = "+sqlplus);

			boolean findSQLPlusOnOracleHomeBin = FileUtil.findFile( sqlplus ,new File(oracleHome + File.separator + BIN_DIR));

			boolean findSQLPlusOnOracleHome = FileUtil.findFile( sqlplus ,new File(oracleHome));

			if (findSQLPlusOnOracleHomeBin) {
				listener.getLogger().println("found SQL*Plus on "+new File(oracleHome + File.separator + BIN_DIR).getAbsolutePath());
				args.add(oracleHome + File.separator + BIN_DIR + File.separator + sqlplus);
			} else if (findSQLPlusOnOracleHome) {
				listener.getLogger().println("found SQL*Plus on "+new File(oracleHome).getAbsolutePath());
				args.add(oracleHome + File.separator + File.separator + sqlplus);
			} else {
				throw new RuntimeException(Messages.SQLPlusRunner_missingSQLPlus());
			}

			args.add(SQLPLUS_VERSION);

			if (debug) {
 			 listener.getLogger().println(LINE);
			 listener.getLogger().println("Statement:");
			 for (String a: args.toList()) {
				listener.getLogger().print(a+" ");
			 }
			 listener.getLogger().println(" ");
			 listener.getLogger().println(LINE);
			}

			ProcessBuilder proc = new ProcessBuilder(args.toList());
			proc.environment().putAll(envVars);
			proc.redirectErrorStream(true);
			Process process = proc.start();
			int exitCode = process.waitFor();

			new StreamCopyThread(Messages.SQLPlusRunner_errorLogVersion(), process.getErrorStream(),
					listener.getLogger(), false).start();
			new StreamCopyThread(Messages.SQLPlusRunner_logVersion(), process.getInputStream(), listener.getLogger(),
					false).start();

			listener.getLogger().printf(Messages.SQLPlusRunner_processEnd() + " %d%n", exitCode);

		} catch (Exception e) {
			listener.getLogger().println(MSG_ERROR + e.getMessage());
			throw new RuntimeException(e);
		}
		listener.getLogger().println(LINE);
	}


	private boolean isWindowsOS() {

		return System.getProperty(OPERATION_SYSTEM).toLowerCase().indexOf(WINDOWS_OS) >= 0;

	}

}
