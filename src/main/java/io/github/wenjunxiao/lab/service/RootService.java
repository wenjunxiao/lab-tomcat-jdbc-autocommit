package io.github.wenjunxiao.lab.service;

import io.github.wenjunxiao.lab.mapper.root.ProcesslistMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RootService {
  private static RootService instance;

  @Autowired
  private ProcesslistMapper processlistMapper;

  @Value("${spring.datasource.test.username}")
  private String testUser;
  @Value("${kill-condition}")
  private String killCondition;
  @Value("${mock-readonly:false}")
  private boolean mockReadOnly;

  public RootService() {
    instance = this;
  }

  public void kill() {
    List<Integer> idList = processlistMapper.findProcessByUser(testUser);
    System.out.println("processlist => " + idList);
    for (Integer id : idList) {
      processlistMapper.killTest(id);
    }
  }

  public static void killTest() {
    instance.kill();
  }

  public static boolean isKillTestUser(String user) {
    return instance.testUser.equals(user);
  }

  public static boolean canKill(String query) {
    return query.contains(instance.killCondition);
  }

  public static boolean isReadOnly() {
    return instance.mockReadOnly;
  }
}
