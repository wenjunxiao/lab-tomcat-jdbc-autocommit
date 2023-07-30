package io.github.wenjunxiao.lab.mapper.root;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ProcesslistMapper {

  @Select("SELECT id FROM information_schema.PROCESSLIST WHERE `user` = #{user}")
  List<Integer> findProcessByUser(String user);

  @Update("KILL #{id}")
  int killTest(int id);

}
