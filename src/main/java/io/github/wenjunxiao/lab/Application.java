package io.github.wenjunxiao.lab;

import io.github.wenjunxiao.lab.aspect.KillMySqlAdvice;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.ibatis.javassist.ClassPool;
import org.apache.ibatis.javassist.CtClass;
import org.apache.ibatis.javassist.CtConstructor;
import org.apache.ibatis.javassist.CtMethod;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.ByteArrayInputStream;
import java.lang.instrument.Instrumentation;

@SpringBootApplication(exclude = {MybatisAutoConfiguration.class})
public class Application {

  public static void main(String[] args) {
    Instrumentation instrumentation = ByteBuddyAgent.install();
    ClassPool classPool = ClassPool.getDefault();
    instrumentation.addTransformer((loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
      if (className.contains("MysqlIO")) {
        System.out.println("==================MysqlIO.wrapper===============" + className);
        try {
          classPool.importPackage(KillMySqlAdvice.class.getName());
          CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
          for (CtConstructor ctConstructor : ctClass.getDeclaredConstructors()) {
            ctConstructor.insertBeforeBody("KillMySqlAdvice.putMysqlIO(this, $1, $3);");
          }
          CtMethod ctMethod = ctClass.getDeclaredMethod("sqlQueryDirect");
          ctMethod.insertBefore("$2 = KillMySqlAdvice.checkKill(this, $1,$2);");
          return ctClass.toBytecode();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      return classfileBuffer;
    });
    SpringApplication.run(Application.class, args);
  }
}
