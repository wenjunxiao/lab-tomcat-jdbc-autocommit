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
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.lang.instrument.Instrumentation;

@SpringBootApplication(exclude = {MybatisAutoConfiguration.class})
public class Application {

  public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);
    wrap("true".equals(context.getEnvironment().getProperty("fix-connection-state")));
  }

  private static void wrap(boolean fixConnectionState) {
    Instrumentation instrumentation = ByteBuddyAgent.install();
    ClassPool classPool = ClassPool.getDefault();
    System.out.println("================wrap===========" + fixConnectionState);
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
          ctClass.debugWriteFile("target");
          return ctClass.toBytecode();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      } else if (fixConnectionState && className.contains("ConnectionState")) {
        System.out.println("==================ConnectionState.wrapper===============" + className);
        try {
          CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
          CtMethod ctMethod = ctClass.getDeclaredMethod("invoke");
          ctMethod.addCatch("{System.out.println(\"=============ConnectionState.catch=========\" + $e);" +
                  "        this.autoCommit = null;\n" +
                  "        this.transactionIsolation = null;\n" +
                  "        this.readOnly = null;\n" +
                  "        this.catalog = null; throw $e;}", classPool.get("java.lang.Exception"));
          ctClass.debugWriteFile("target");
          return ctClass.toBytecode();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      return classfileBuffer;
    });
  }

}
