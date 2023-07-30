package io.github.wenjunxiao.lab.service;

import io.github.wenjunxiao.lab.domain.Test;
import io.github.wenjunxiao.lab.mapper.test.TestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestService {

  @Autowired
  private TestMapper testMapper;

  @Transactional
  public int add() {
    Test test = new Test();
    if (testMapper.add(test) > 0) {
      return test.getId();
    }
    return 0;
  }

  public Object findTopOne() {
    return testMapper.findTopOne();
  }
}
