package ca.uhn.fhir.jpa.demo.elasticsearch;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Driver;
import java.util.Properties;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import ca.uhn.fhir.jpa.config.BaseJavaConfigDstu3;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.search.ElasticsearchMappingProvider;
import ca.uhn.fhir.jpa.util.SubscriptionsRequireManualActivationInterceptorDstu3;
import ca.uhn.fhir.rest.server.interceptor.IServerInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import org.apache.commons.dbcp2.BasicDataSource;
import org.hibernate.search.elasticsearch.cfg.ElasticsearchEnvironment;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * This is the configuration file for the example server integrating the ElasticSearch engine.
 */
@Configuration
@EnableTransactionManagement()
@PropertySource("classpath:db.properties")
public class FhirServerConfig extends BaseJavaConfigDstu3 {
	@Value("${database.hibernate.dialect}")
	private String db_dialect ;//= "ca.uhn.fhir.jpa.util.DerbyTenSevenHapiFhirDialect";
	@Value("${database.driverClassName}")
	private String db_className; // = "org.apache.derby.jdbc.EmbeddedDriver";
	@Value("${database.url}")
	private String db_URL ; //= "jdbc:derby:directory:target/jpaserver_derby_files;create=true";
	@Value("${database.username}")
	private String db_username ;//= "";
	@Value("${database.password}")
	private String db_password ; //= "";
	/**
	 * Configure FHIR properties around the the JPA server via this bean
	 */
	@Bean()
	public DaoConfig daoConfig() {
		DaoConfig retVal = new DaoConfig();
		retVal.setAllowMultipleDelete(true);
		return retVal;
	}

	/**
	 * The following bean configures the database connection. The 'url' property value of "jdbc:derby:directory:jpaserver_derby_files;create=true" indicates that the server should save resources in a
	 * directory called "jpaserver_derby_files".
	 *
	 * A URL to a remote database could also be placed here, along with login credentials and other properties supported by BasicDataSource.
	 */
	@Bean(destroyMethod = "close")
	public DataSource dataSource() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
		BasicDataSource retVal = new BasicDataSource();
		Class<?> clazz = Class.forName(db_className);
		Constructor<?> ctor = clazz.getConstructor();
		Object object = ctor.newInstance();

		retVal.setDriver((Driver) object);
		retVal.setUrl(db_URL);
		retVal.setUsername(db_username);
		retVal.setPassword(db_password);
		return retVal;
	}

	@Override
	@Bean()
	public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
		LocalContainerEntityManagerFactoryBean retVal = super.entityManagerFactory();
		retVal.setPersistenceUnitName("HAPI_PU");
		try {
			retVal.setDataSource(dataSource());
			retVal.setJpaProperties(jpaProperties());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException("Unable to load Properties: " + e.getMessage());
		} catch (NoSuchMethodException |InstantiationException | IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
			throw new RuntimeException("Unable to load Driver: " + e.getMessage());
		}
		return retVal;
	}

	private Properties jpaProperties() throws ClassNotFoundException {
		Properties extraProperties = new Properties();
		Class<?> act = Class.forName(db_dialect);
		extraProperties.put("hibernate.dialect", act.getName());
		extraProperties.put("hibernate.format_sql", "true");
		extraProperties.put("hibernate.show_sql", "false");
		extraProperties.put("hibernate.hbm2ddl.auto", "update");
		extraProperties.put("hibernate.jdbc.batch_size", "20");
		extraProperties.put("hibernate.cache.use_query_cache", "false");
		extraProperties.put("hibernate.cache.use_second_level_cache", "false");
		extraProperties.put("hibernate.cache.use_structured_entries", "false");
		extraProperties.put("hibernate.cache.use_minimal_puts", "false");

		// the belowing properties are used for ElasticSearch integration
		extraProperties.put(ElasticsearchEnvironment.ANALYSIS_DEFINITION_PROVIDER, ElasticsearchMappingProvider.class.getName());
		extraProperties.put("hibernate.search.default.indexmanager", "elasticsearch");
		extraProperties.put("hibernate.search.default.elasticsearch.host", "http://127.0.0.1:9200");
		extraProperties.put("hibernate.search.default.elasticsearch.index_schema_management_strategy", "CREATE");
		extraProperties.put("hibernate.search.default.elasticsearch.index_management_wait_timeout", "10000");
		extraProperties.put("hibernate.search.default.elasticsearch.required_index_status", "yellow");
		return extraProperties;
	}

	/**
	 * This interceptor adds some pretty syntax highlighting in responses when a browser is detected
	 */
	@Bean(autowire = Autowire.BY_TYPE)
	public IServerInterceptor responseHighlighterInterceptor() {
		ResponseHighlighterInterceptor retVal = new ResponseHighlighterInterceptor();
		return retVal;
	}

	@Bean(autowire = Autowire.BY_TYPE)
	public IServerInterceptor subscriptionSecurityInterceptor() {
		SubscriptionsRequireManualActivationInterceptorDstu3 retVal = new SubscriptionsRequireManualActivationInterceptorDstu3();
		return retVal;
	}

	@Bean()
	public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		JpaTransactionManager retVal = new JpaTransactionManager();
		retVal.setEntityManagerFactory(entityManagerFactory);
		return retVal;
	}

}
