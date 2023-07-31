package io.github.wenjunxiao.lab.aspect;

import org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;

import java.lang.reflect.Method;

public class ConnectionStateFixed extends ConnectionState {

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      return super.invoke(proxy, method, args);
    } catch (Throwable e) {
      this.autoCommit = null;
      this.readOnly = null;
      this.transactionIsolation = null;
      this.catalog = null;
      throw e;
    }
  }
}
