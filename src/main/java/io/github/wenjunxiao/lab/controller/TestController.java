package io.github.wenjunxiao.lab.controller;

import io.github.wenjunxiao.lab.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("test")
public class TestController {

  @Autowired
  private TestService testService;

  @GetMapping("get")
  public Object get() {
    return testService.findTopOne();
  }

  @GetMapping("add")
  public int add() {
    return testService.add();
  }
}
