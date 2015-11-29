package org.jenkinsci.plugins.sqlplusscriptrunner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import hudson.model.BuildListener;

public class SQLPlusRunner {

	private static final String MSG_TEMP_SCRIPT = Messages.SQLPlusRunner_tempScript();

	private static final String STAR = "*";

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

	public SQLPlusRunner(BuildListener listener) {
		this.listener = listener;
	}

	private final BuildListener listener;

	private static final String LINE = "--------------------------------------------------------------------------";

	public void runGetSQLPLusVersion(String sqlPath,String oracleHome) {

		if (oracleHome == null || oracleHome.length() < 1) { throw new RuntimeException(MSG_ORACLE_HOME_MISSING); }

		File directoryAccessTest = new File(oracleHome);
		if (!directoryAccessTest.exists()) { throw new RuntimeException(Messages.SQLPlusRunner_wrongOracleHome(oracleHome)); }

		if (sqlPath == null) { throw new RuntimeException(Messages.SQLPlusRunner_missingScript(sqlPath)); }

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_ORACLE_HOME + oracleHome);
		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_GET_SQL_PLUS_VERSION);
		try {
			listener.getLogger().println(ExternalProgramUtil.getVersion(sqlPath,oracleHome));
		} catch (Exception e) {
			listener.getLogger().println(MSG_ERROR + e.getMessage());
			throw new RuntimeException(e);
		}
		listener.getLogger().println(LINE);
	}

	public void runScript(String user,String password,String instance,String script,String sqlPath,String oracleHome,String scriptType) throws Exception {

		if (oracleHome == null || oracleHome.length() < 1) { throw new RuntimeException(MSG_ORACLE_HOME_MISSING); }

		File directoryAccessTest = new File(oracleHome);
		if (!directoryAccessTest.exists()) { throw new RuntimeException(Messages.SQLPlusRunner_wrongOracleHome(oracleHome)); }

		if (script == null || script.length() < 1) { throw new RuntimeException(Messages.SQLPlusRunner_missingScript(sqlPath)); }

		String instanceStr = LOCAL_DATABASE_MSG;
		if (instance != null) {
			instanceStr = instance;
		}

		listener.getLogger().println(LINE);
		listener.getLogger().println(MSG_ORACLE_HOME + oracleHome);
		listener.getLogger().println(LINE);

		if (ScriptType.userDefined.name().equals(scriptType)) {
			listener.getLogger().println(MSG_DEFINED_SCRIPT + user + SLASH + showHiddenPassword(password) + AT + instanceStr);
			script = createTempScript(script);
			listener.getLogger().println(MSG_TEMP_SCRIPT + script);
		} else {
			listener.getLogger().println(MSG_SCRIPT + script + ON + user + SLASH + showHiddenPassword(password) + AT + instanceStr);
		}

		listener.getLogger().println(LINE);

		try {
			listener.getLogger().println(ExternalProgramUtil.run(user,password,instanceStr,script,sqlPath,oracleHome,scriptType));
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

	private String showHiddenPassword(String password) {
		if (password == null) { return ""; }
		int size = password.length();
		StringBuilder output = new StringBuilder();
		for (int i = 0 ; i < size ; i++) {
			output.append(STAR);
		}
		return output.toString();
	}
}
