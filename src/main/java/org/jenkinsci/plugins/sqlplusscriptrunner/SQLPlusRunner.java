package org.jenkinsci.plugins.sqlplusscriptrunner;

import hudson.model.BuildListener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SQLPlusRunner {

	private static final String MSG_TEMP_SCRIPT = "Temp script:";

	private static final String STAR = "*";

	private static final String ON = " on ";

	private static final String MSG_SCRIPT = "Running script ";

	private static final String MSG_DEFINED_SCRIPT = "Running defined script on ";

	private static final String AT = "@";

	private static final String SLASH = "/";

	private static final String MSG_ERROR = "Error:";

	private static final String MSG_GET_SQL_PLUS_VERSION = "Getting SQLPlus version";

	private static final String MSG_SQL_SCRIPT_MISSING = "Please set up the SQL script!";

	private static final String MSG_ORACLE_HOME_MISSING = "Please set up the ORACLE_HOME!";

	private static final String LOCAL_DATABASE_MSG = "local";

	private static final String SQL_TEMP_SCRIPT = "temp-script-";

	private static final String SQL_PREFIX = ".sql";

	public SQLPlusRunner(BuildListener listener) {
		this.listener = listener;
	}

	private final BuildListener listener;

	private static final String LINE = "--------------------------------------------------------------------------";

	public void runGetSQLPLusVersion(String sqlPath,String oracleHome) {

		if (oracleHome == null) { throw new RuntimeException(MSG_ORACLE_HOME_MISSING); }
		if (sqlPath == null) { throw new RuntimeException(MSG_SQL_SCRIPT_MISSING); }

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

		if (oracleHome == null) { throw new RuntimeException(MSG_ORACLE_HOME_MISSING); }
		if (script == null || script.length() < 1) { throw new RuntimeException(MSG_SQL_SCRIPT_MISSING); }

		String instanceStr = LOCAL_DATABASE_MSG;
		if (instance != null) {
			instanceStr = instance;
		}

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
