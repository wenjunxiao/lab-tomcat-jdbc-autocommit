# lab-tomcat-jdbc-autocommit

## 问题

使用`tomcat-jdbc`时出现`Can't call commit when autocommit=true`，错误信息如下
```log
### Error committing transaction.  Cause: java.sql.SQLException: Can't call commit when autocommit=true
### Cause: java.sql.SQLException: Can't call commit when autocommit=true
; uncategorized SQLException for SQL []; SQL state [null]; error code [0]; Can't call commit when autocommit=true; nested exception is java.sql.SQLException: Can't call commit when autocommit=true] with root cause

java.sql.SQLException: Can't call commit when autocommit=true
```

## 分析

这个错误的意思是：自动提交的情况下不需要再执行`commit`操作。通常我们会把`autocommit`设置为`true`，这样非事务的操作都是自动提交的，
只有开启事务的时候才需要把`autocommit`设置为`false`，并在事务结束的时候执行`commit`/`rollback`操作，然后再把`autocommit`设置为`true`。
也就是说执行某个非事务操作完成之后执行了非预期的`commit`操作才会导致以上的异常，应该有两个检测`autocommit`的位置状态不一致导致的。

跑出`Can't call commit when autocommit=true`这个异常的代码在`mysql-connector-java-5.1.42.jar`的`com.mysql.jdbc.ConnectionImpl`中，
使用mybatis获取数据库连接的`org.mybatis.spring.transaction.SpringManagedTransaction.openConnection`的地方通过`this.connection.getAutoCommit()`
获取`autoCommit`的值，并在`commit()`通过`this.connection != null && !this.isConnectionTransactional && !this.autoCommit`判断是否需要在
数据库连接上执行最终的提交`this.connection.commit()`，也就是这个地方的状态判断和数据库连接上的状态不一致导致执行了非预期的`commit()`操作。

通过调试跟踪最终确定是因为`org.apache.tomcat.jdbc.pool.interceptor.ConnectionState`缓存了状态，并且在异常的情况下和连接上的状态不一致导致，
其大概的逻辑代码（非实际代码）如下
```java
class ConnectionState {
  boolean getAutoCommit() {
    if (autoCommit == null) {
      autoCommit = connection.getAutoCommit();
    }
    return autoCommit;
  }
  void setAutoCommit(boolean value) {
    connection.setAutoCommit(value);
    this.autoCommit = value;
  }
}
```
以上代码中只要`connection.setAutoCommit`出现异常就会导致状态不一致，缓存的逻辑需要调整成
```java
class ConnectionState {
  void setAutoCommit(boolean value) {
    try {
      connection.setAutoCommit(value);
      this.autoCommit = value;
    } catch (Exception e) {
      this.autoCommit = null; // 异常时清空，以便重新获取最新状态
    }
  }
}
```

## 复现

知道了问题的原因，如何复现这个问题呢？
我们只需要在`setAutoCommit(true)`的时候模拟异常即可，为了更贴近实际应用的情况，需要模拟服务端断开连接，因此需要准备两个账户，一个用于执行业务代码，
另一个用于Kill连接来模拟断开连接。
```sql 
CREATE TABLE `test`(
    id INT(11) AUTO_INCREMENT NOT NULL,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE USER 'test-tomcat-jdbc'@'localhost' IDENTIFIED BY 'test';
GRANT ALL ON test.* TO 'test-tomcat-jdbc'@'localhost';

CREATE USER 'root-tomcat-jdbc'@'localhost' IDENTIFIED BY 'test';
GRANT ALL ON *.* TO 'root-tomcat-jdbc'@'localhost';

FLUSH PRIVILEGES;
```
为了使用`ConnectionState`需要在业务的数据库连接池的配置`jdbc-interceptors`中包含`ConnectionState`，正常来说如果连接被断开之后，
连接会被剔除掉（`tomcat-jdbc`的连接池没有剔除导致一直不可用），因此还需要在数据库连接的`url`中增加`autoReconnect=true`确保断开后自动连接避免被剔除。
有了已经配置，运行应用之后请求新增接口之后就会触发Kill。
```sh
$ curl http://127.0.0.1:8080/test/add
```
此时会发现日志中有`Communications link failure`的错误信息，但是后续的检测数据库连接又是正常的，因为`autoReconnect=true`自动连接成功了。
但是再次请求则会报`Can't call commit when autocommit=true`的错误了
```sh
$ curl http://127.0.0.1:8080/test/get
{"timestamp":1690722907640,"status":500,"error":"Internal Server Error","exception":"org.springframework.jdbc.UncategorizedSQLException","message":"\n### Error committing transaction.  Cause: java.sql.SQLException: Can't call commit when autocommit=true\n### Cause: java.sql.SQLException: Can't call commit when autocommit=true\n; uncategorized SQLException for SQL []; SQL state [null]; error code [0]; Can't call commit when autocommit=true; nested exception is java.sql.SQLException: Can't call commit when autocommit=true","path":"/test/get"}
```

测试时只需要修改数据库的IP和端口即可，并通过配置以下不同的参数模拟断开连接的时机
```yaml
# 模拟连接到只读库
mock-readonly: false
# 开始事务后检测是否只读时断开连接
kill-condition: session.tx_read_only
# 开始事务并提交后重置自动提交时断开连接
#kill-condition: autocommit=1
#kill-condition: not-kill
```

## 结论

暂时不要使用`ConnectionState`或者使用其他数据库连接池。
