package de.adorsys.ledgers.rest.client;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.netflix.feign.FeignFormatterRegistrar;
import org.springframework.cloud.netflix.feign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import feign.codec.Encoder;

/**
 * In this configuration, we clone the original object mapper and remove the
 * root value wrapping.
 * 
 * @author fpo
 *
 */
@Configuration
public class FeignConfig {
	
	@Autowired
	private ObjectMapper originalObjectMapper;
	
	@Bean
	public Encoder feignEncoder() {
		ObjectMapper objectMapper = originalObjectMapper.copy();
        objectMapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
		@SuppressWarnings("rawtypes")
		HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
		ObjectFactory<HttpMessageConverters> objectFactory = () -> new HttpMessageConverters(jacksonConverter);
		return new SpringEncoder(objectFactory);
	}
	
	@Bean
	public FeignFormatterRegistrar localDateFeignFormatterRegistrar() {
	    return new FeignFormatterRegistrar() {
	        @Override
	        public void registerFormatters(FormatterRegistry formatterRegistry) {
	            DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
	            registrar.setUseIsoFormat(true);
	            registrar.registerFormatters(formatterRegistry);
	        }
	    };
	}	
	
}
