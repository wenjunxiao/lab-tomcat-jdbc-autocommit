package io.github.wenjunxiao.lab.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@MapperScan(basePackages = "io.github.wenjunxiao.lab.mapper.root", sqlSessionFactoryRef = "rootSessionFactory")
@Configuration
public class RootDataSourceConfig {

  @ConfigurationProperties("spring.datasource.root")
  @Bean("rootDataSource")
  public DataSource rootDataSource() {
    return DataSourceBuilder.create().build();
  }

  @Bean
  public SqlSessionFactory rootSessionFactory(@Qualifier("rootDataSource") @Autowired DataSource dataSource) throws Exception {
    SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
    sqlSessionFactoryBean.setDataSource(dataSource);
    return sqlSessionFactoryBean.getObject();
  }
}
