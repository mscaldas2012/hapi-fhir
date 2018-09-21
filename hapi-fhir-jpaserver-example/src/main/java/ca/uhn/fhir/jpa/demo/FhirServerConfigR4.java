package ca.uhn.fhir.jpa.demo;


import ca.uhn.fhir.jpa.config.BaseJavaConfigR4;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.util.SubscriptionsRequireManualActivationInterceptorR4;
import ca.uhn.fhir.rest.server.interceptor.IServerInterceptor;
import ca.uhn.fhir.rest.server.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Driver;
import java.util.Properties;

/**
 * This is the primary configuration file for the example server
 */
@Configuration
@EnableTransactionManagement()
@PropertySource("classpath:db.properties")
public class FhirServerConfigR4 extends BaseJavaConfigR4 {
    @Value("${database.hibernate.dialect}")
    private String db_dialect ;
    @Value("${database.driverClassName}")
    private String db_className;
    @Value("${database.url}")
    private String db_URL ;
    @Value("${database.username}")
    private String db_username ;
    @Value("${database.password}")
    private String db_password ;

    @Value("${hibernate.format_sql}")
    private String hibernate_format_sql;
    @Value("${hibernate.show_sql}")
    private String hibernate_show_sql;
    @Value("${hibernate.hbm2ddl.auto}")
    private String hibernate_hbm2ddl_auto;
    @Value("${hibernate.jdbc.batch_size}")
    private String hibernate_jdbc_batch_size;
    @Value("${hibernate.cache.use_query_cache}")
    private String hibernate_cache_use_query_cache;
    @Value("${hibernate.cache.use_second_level_cache}")
    private String hibernate_cache_use_second_level_cache;
    @Value("${hibernate.cache.use_structured_entries}")
    private String hibernate_cache_use_structured_entries;
    @Value("${hibernate.cache.use_minimal_puts}")
    private String hibernate_cache_use_minimal_puts;
    @Value("${hibernate.search.model_mapping}")
    private String hibernate_search_model_mapping;
    @Value("${hibernate.search.default.directory_provider}")
    private String hibernate_search_default_directory_provider;
    @Value("${hibernate.search.default.indexBase}")
    private String hibernate_search_default_indexBase;
    @Value("${hibernate.search.lucene_version}")
    private String hibernate_search_lucene_version;

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

        Class<?> dialect = Class.forName(db_dialect);
        extraProperties.put("hibernate.dialect", dialect.getName());
        extraProperties.put("hibernate.format_sql", hibernate_format_sql);
		extraProperties.put("hibernate.show_sql", hibernate_show_sql);
		extraProperties.put("hibernate.hbm2ddl.auto", hibernate_hbm2ddl_auto);
		extraProperties.put("hibernate.jdbc.batch_size", hibernate_jdbc_batch_size);
		extraProperties.put("hibernate.cache.use_query_cache", hibernate_cache_use_query_cache);
		extraProperties.put("hibernate.cache.use_second_level_cache", hibernate_cache_use_second_level_cache);
		extraProperties.put("hibernate.cache.use_structured_entries", hibernate_cache_use_structured_entries);
		extraProperties.put("hibernate.cache.use_minimal_puts", hibernate_cache_use_minimal_puts);

        Class<?> modelMapping = Class.forName(hibernate_search_model_mapping);
		extraProperties.put("hibernate.search.model_mapping", modelMapping.getName());

		extraProperties.put("hibernate.search.default.directory_provider", hibernate_search_default_directory_provider);
		extraProperties.put("hibernate.search.default.indexBase", hibernate_search_default_indexBase);
		extraProperties.put("hibernate.search.lucene_version", hibernate_search_lucene_version);
		extraProperties.put("hibernate.search.default.worker.execution", "async");
		return extraProperties;
	}

	/**
	 * Do some fancy logging to create a nice access log that has details about each incoming request.
	 */
	public IServerInterceptor loggingInterceptor() {
		LoggingInterceptor retVal = new LoggingInterceptor();
		retVal.setLoggerName("fhirtest.access");
		retVal.setMessageFormat(
				"Path[${servletPath}] Source[${requestHeader.x-forwarded-for}] Operation[${operationType} ${operationName} ${idOrResourceName}] UA[${requestHeader.user-agent}] Params[${requestParameters}] ResponseEncoding[${responseEncodingNoDefault}]");
		retVal.setLogExceptions(true);
		retVal.setErrorMessageFormat("ERROR - ${requestVerb} ${requestUrl}");
		return retVal;
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
		SubscriptionsRequireManualActivationInterceptorR4 retVal = new SubscriptionsRequireManualActivationInterceptorR4();
		return retVal;
	}

	@Bean()
	public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		JpaTransactionManager retVal = new JpaTransactionManager();
		retVal.setEntityManagerFactory(entityManagerFactory);
		return retVal;
	}

}
