package io.github.elliotmok.mbg.springcloudconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;

import java.io.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 * 以code-based的方式操作spring-cloud-config
 *
 * @author molibin
 * @since 1.1.0
 * @date 2018-01-24
 */
public class SpringCloudConfigUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringCloudConfigUtil.class);

    private SpringCloudConfigUtil() {
    }

    /**
     * 按照激活的不同profile去读取spring-cloud-config的配置文件属性值
     * @param profile 激活的profile
     *                <p>如：如果profile值为"prd"，则会按照classpath路径下的bootstrap.properties和bootstrap-prd.properties（如果有同名属性，后者中的覆盖前者中的）属性值中指定的方式，
     *                去读取spring-cloud-config远程对应的配置信息。
     *                <p>profile值为"default"或者""时，则只加载bootstrap.properties
     * @return
     * @author molibin
     */
    public static PropertySource getPropertySource(String fileDir, String profile) throws IOException {
        if(profile == null || "default".equals(profile)){
            profile = "";
        }
        return getPropertySource(fileDir, "bootstrap", profile);
    }

    /**
     * @author molibin
     */
    private static PropertySource getPropertySource(String fileDir, String fileName, String suffix) throws IOException {
        //构成特定于spring-cloud-config的抽象文件配置对象“ConfigClientProperties”
        //1、构成"PropertySource"
        //1-1、加载properties文件
        Properties properties = loadPropertiesFileWithAndWithoutSuffix(fileDir, fileName, suffix);
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("propertySource", properties);
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(propertiesPropertySource);

        //2、构成“Environment”
        BootstrapEnvironment environment = new BootstrapEnvironment(propertySources);
        //3、构成最终的“ConfigClientProperties”
        ConfigClientProperties configClientProperties = new ConfigClientProperties(environment);
        configClientProperties.setUri(properties.getProperty("spring.cloud.config.uri"));

        //使用特定于spring-cloud-config的 property属性加载类“ConfigServicePropertySourceLocator”，来加载属性值
        ConfigServicePropertySourceLocator configServicePropertySourceLocator = new ConfigServicePropertySourceLocator(configClientProperties);
        return configServicePropertySourceLocator.locate(environment);
    }

    private static Properties loadPropertiesFileWithAndWithoutSuffix(String fileDir, String fileName, String suffix) throws IOException {
        Properties properties = new Properties();

        if(suffix == null){
            suffix = "";
        }
        Set<String> propertyFileSuffixes = new HashSet<>();
        propertyFileSuffixes.add("");
        propertyFileSuffixes.add(suffix);

        Iterator<String> it = propertyFileSuffixes.iterator();
        while(it.hasNext()){
            String nextObject = it.next();
            final String settingsFileName = fileDir + fileName + (nextObject != null && !"".equals(nextObject) ? "-" + suffix : "") + ".properties";
            LOGGER.info("spring active SETTINGS_FILE_NAME : {}", settingsFileName);

            try(InputStream is = new FileInputStream(new File(settingsFileName))) {
                properties.load(is);
            } catch (NullPointerException | IOException e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }

        }

        return properties;
    }


}