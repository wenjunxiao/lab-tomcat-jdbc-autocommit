# lab-tomcat-jdbc-autocommit

## Question

When using `tomcat-jdbc`, `Can't call commit when autocommit=true` appears, and the error message is as follows
```log
### Error committing transaction.  Cause: java.sql.SQLException: Can't call commit when autocommit=true
### Cause: java.sql.SQLException: Can't call commit when autocommit=true
; uncategorized SQLException for SQL []; SQL state [null]; error code [0]; Can't call commit when autocommit=true; nested exception is java.sql.SQLException: Can't call commit when autocommit=true] with root cause

java.sql.SQLException: Can't call commit when autocommit=true
```

## Analysis

The meaning of this error is that there is no need to perform the 'commit' operation in the case of automatic submission. 
Usually, we set `autocommit` to `false`, so that non-transactional operations are automatically committed,
It is only necessary to set `autocommit` to `false` when starting a transaction, and execute the `commit`/`rollback`
operation at the end of the transaction, and then set `autocommit` to `true`.
That is to say, the unexpected `commit` operation performed after a non-transactional is completed will cause the above exception, 
which should be caused by the inconsistent status of the two `autocommit` cache.

The code that throws the exception `Can't call commit when autocommit=true` is in the class `com.mysql.jdbc.ConnectionImpl` 
of `mysql-connector-java-5.1.42.jar`. In `org.mybatis.spring.transaction.SpringManagedTransaction.openConnection`, 
mybatis use `this.connection.getAutoCommit()` to obtain the database connection's `autoCommit` status.
And then use the `autoCommit` status to determine if it is necessary to call `this.connection.commit()`.
This is where inconsistent states lead to unexpected execution `commit()`.

Through debugging and tracking, it was ultimately determined that `org.apache.tomcat.jdbc.pool.interceptor.ConnectionState`
cached the state and caused inconsistency with the state on the connection in abnormal situations.
The logical code (non-actual code) is as follows
```java
class ConnectionState {
  boolean getAutoCommit() {
    if (autoCommit == null) { // cache if null
      autoCommit = connection.getAutoCommit();
    }
    return autoCommit;
  }
  void setAutoCommit(boolean value) {
    connection.setAutoCommit(value);
    this.autoCommit = value; // update cache value
  }
}
```
Any exceptions in the above code `connection.setAutoCommit` will result in inconsistent states, and the cache logic needs to be adjusted to
```java
class ConnectionState {
  void setAutoCommit(boolean value) {
    try {
      connection.setAutoCommit(value);
      this.autoCommit = value;
    } catch (Throwable e) {
      this.autoCommit = null; // reset if any exception
    }
  }
}
```

## Recurrent

Knowing the cause of the problem, how to reproduce it?
We only need to simulate exceptions during `setAutoCommit(true)`. In order to be more practical, we need to simulate server disconnection.
Therefore, we need to prepare two accounts, one for executing business code, another is used to kill connections to simulate disconnection.
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

In order to use `ConnectionState`, you need to include `ConnectionState` in the configuration `jdbc-interceptors` of the database connection pool.
Normally, if the connection is disconnected, the connection will be removed (but `tomcat-jdbc` not, which will cause always unavailable).
Therefore, `autoReconnect=true` needs to be added to the `url` of the database connection to ensure that the automatic connection is not removed after disconnection.
With the configuration already in place, after running the application and requesting the addition of an interface, Kill will be triggered.
```sh
$ curl http://127.0.0.1:8080/test/add
```

At this point, it will be found that there is an error message of `Communications link failure` in the log,
but the subsequent detection of database connection is normal because `autoReconnect=true` automatically connected successfully.
But requesting again will result in an error of `Can't call commit when autocommit=true`
```sh
$ curl http://127.0.0.1:8080/test/get
{"timestamp":1690722907640,"status":500,"error":"Internal Server Error","exception":"org.springframework.jdbc.UncategorizedSQLException","message":"\n### Error committing transaction.  Cause: java.sql.SQLException: Can't call commit when autocommit=true\n### Cause: java.sql.SQLException: Can't call commit when autocommit=true\n; uncategorized SQLException for SQL []; SQL state [null]; error code [0]; Can't call commit when autocommit=true; nested exception is java.sql.SQLException: Can't call commit when autocommit=true","path":"/test/get"}
```

During testing, only the IP and port of the database need to be modified, and the timing of disconnection can be simulated by configuring the following different parameters
```yaml
# simulate connecting to a read-only server
mock-readonly: false
# disconnect when detecting read-only status after starting a transaction
kill-condition: session.tx_read_only
# disconnect when starting a transaction and resetting automatic commit after commit
#kill-condition: autocommit=1
# not kill
#kill-condition: not-kill
# fix the issue with `ConnectionState`
fix-connection-state: false
```

## Conclusion

Do not use `ConnectionState` until repair, or use other database Connection pool. 
Alternatively, fix the problem by rewriting it (such as `io.github.wenjunxiao.lab.aspect.ConnectionStateFixed`).
