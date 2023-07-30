package io.github.wenjunxiao.lab.mapper.test;

import io.github.wenjunxiao.lab.domain.Test;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

public interface TestMapper {

  @Insert("INSERT INTO test(create_time, update_time) VALUES (NOW(), NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int add(Test test);

  @Select("SELECT id, create_time as createTime, update_time as updateTime FROM test ORDER BY create_time DESC LIMIT 1")
  Test findTopOne();

}
