package io.github.wenjunxiao.lab.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@MapperScan(basePackages = "io.github.wenjunxiao.lab.mapper.test", sqlSessionFactoryRef = "testSessionFactory")
@Configuration
public class TestDataSourceConfig {

  @Primary
  @ConfigurationProperties("spring.datasource.test")
  @Bean("testDataSource")
  public DataSource testDataSource() {
    return DataSourceBuilder.create().build();
  }

  @Bean
  public DataSourceTransactionManager testTransactionManager(@Qualifier("testDataSource") DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  @Bean
  public SqlSessionFactory testSessionFactory(@Qualifier("testDataSource") DataSource testDataSource) throws Exception {
    SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
    sqlSessionFactoryBean.setDataSource(testDataSource);
    return sqlSessionFactoryBean.getObject();
  }
}
