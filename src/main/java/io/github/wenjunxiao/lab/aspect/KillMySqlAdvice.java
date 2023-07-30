package io.github.wenjunxiao.lab.aspect;

import com.mysql.jdbc.MysqlIO;
import com.mysql.jdbc.SQLError;
import com.mysql.jdbc.StatementImpl;
import io.github.wenjunxiao.lab.service.RootService;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class KillMySqlAdvice {

  /**
   * 0-无需处理
   * 1-待执行
   * 2-已开启事务
   * 3-已断开
   */
  private static final Map<MysqlIO, Integer> mysqlIOMap = new HashMap<>();

  public static void putMysqlIO(MysqlIO io, String host, Properties props) {
    String user = props.getProperty("user");
    boolean isTestUser = RootService.isKillTestUser(user);
    System.out.println("====KillMySqlAdvice.putMysqlIO====> host=" + host + ", user=" + user + ", isTestUser=" + isTestUser);
    if (RootService.isKillTestUser(user)) {
      mysqlIOMap.put(io, 1);
    } else {
      mysqlIOMap.put(io, 0);
    }
  }

  private static String replaceQuery(String query, int killStatus) {
    if (query == null) return null;
    return query.replaceAll("@@query_cache_size", "0")
            .replaceAll("@@query_cache_type", "'OFF'")
            .replaceAll("@@tx_isolation", "'REPEATABLE-READ'")
            .replaceAll("@@session.tx_read_only", killStatus > 0 && RootService.isReadOnly() ? "1" : "0");
  }

  public static String checkKill(MysqlIO io, StatementImpl callingStatement, String query) throws SQLException {
    int killStatus = mysqlIOMap.getOrDefault(io, 0);
    if (killStatus == 0) {
      return replaceQuery(query, killStatus);
    }
    if (query == null) {
      String statement = callingStatement.toString();
      System.out.println("====KillMySqlAdvice.checkInsert====> " + statement);
      if (statement.contains("INSERT INTO test")) {
        mysqlIOMap.put(io, 2);
        if (RootService.isReadOnly()) {
          throw new SQLException("mock server running with the --read-only", SQLError.SQL_STATE_CLI_SPECIFIC_CONDITION, 1290);
        }
      }
      return null;
    }
    if (killStatus == 1 && query.contains("autocommit=0")) {
      mysqlIOMap.put(io, 2); // 已经开启事务
    } else if (killStatus == 2) {
      boolean canKill = RootService.canKill(query);
      System.out.println("====KillMySqlAdvice.checkKill====> " + query + " " + canKill);
      if (canKill) {
        mysqlIOMap.put(io, 3);
        RootService.killTest();
      }
    }
    return replaceQuery(query, killStatus);
  }
}
