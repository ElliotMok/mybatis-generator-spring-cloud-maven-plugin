package io.github.elliotmok.mbg.springcloudconfig.mavenplugin;

import io.github.elliotmok.mbg.springcloudconfig.SpringCloudConfigUtil;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.*;
import org.mybatis.generator.config.xml.ConfigurationParser;
import org.mybatis.generator.exception.InvalidConfigurationException;
import org.mybatis.generator.exception.XMLParserException;
import org.mybatis.generator.internal.DefaultShellCallback;
import org.springframework.core.env.PropertySource;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运行mybatis-generator
 * @author molibin
 */
@Mojo(name = "generate")
public class GenerateMojo extends AbstractMojo {
    @Parameter(name = "profile")
    private String profile;
    @Parameter(defaultValue = "spring.datasource.url")
    private String springDatasourceUrl;
    @Parameter(defaultValue = "spring.datasource.username")
    private String springDatasourceUsername;
    @Parameter(defaultValue = "spring.datasource.password")
    private String springDatasourcePassword;
    @Parameter(defaultValue = "spring.datasource.driver-class-name")
    private String springDatasourceDriver;
    @Parameter(defaultValue = "\\src\\main\\resources\\mybatis-generator-config.xml")
    private String mbgConfigurationFile;
    @Parameter(readonly = true, defaultValue = "${basedir}\\src\\main\\resources\\")
    private String hostProjectResourcesDir;
    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;


    @Override
    public void execute() throws MojoExecutionException {
        //获取数据库配置值
        PropertySource springCloudConfigPropertySource;
        try {
            springCloudConfigPropertySource = SpringCloudConfigUtil.getPropertySource(hostProjectResourcesDir, profile);
        } catch (NullPointerException e){
            getLog().error(e.getMessage(), e);
            throw new MojoExecutionException("找不到spring-cloud-config配置文件!");
        } catch (IOException e) {
            getLog().error(e.getMessage(), e);
            throw new MojoExecutionException(e.getMessage(), e);
        }
        String driverClass = (String)springCloudConfigPropertySource.getProperty(springDatasourceDriver);
        if(driverClass == null || "".equals(driverClass)){
            driverClass = "com.mysql.jdbc.Driver";//TODO @mok 改为当该属性为空时，去按照url默认解析。
                                                  // spring-boot已经能做到不用配置spring.datasource.driver-class-name,而根据URL自动解析("The builder can detect the one to use based on what’s available on the classpath. It also auto-detects the driver based on the JDBC URL")。
                                                  //详见：[79. Data Access - spring.io ](https://docs.spring.io/spring-boot/docs/current/reference/html/howto-data-access.html)
        }
        final String connectionUrl = (String)springCloudConfigPropertySource.getProperty(springDatasourceUrl);
        final String userId = (String)springCloudConfigPropertySource.getProperty(springDatasourceUsername);
        final String password = (String) springCloudConfigPropertySource.getProperty(springDatasourcePassword);
        //>>从CONNECTION_URL中解析出数据库名
        String schema = "";
        final String schemaRegex = "[\\s\\S]*:[0-9]*/([\\s\\S]*)\\?[\\s\\S]*";
        Pattern pattern = Pattern.compile(schemaRegex);
        Matcher matcher = pattern.matcher(connectionUrl);
        while (matcher.find()){
            schema = matcher.group(1);
        }


        List<String> warnings = new ArrayList<>();
        File configFile;
        try {
            configFile = modifyFile(new File(project.getBasedir() + File.separator + mbgConfigurationFile));
        } catch (ParserConfigurationException | IOException | SAXException | TransformerException e) {
            getLog().error(e.getMessage(), e);
            throw new MojoExecutionException(e.getMessage());
        }

        try {
            ConfigurationParser cp = new ConfigurationParser(warnings);
            Configuration config = cp.parseConfiguration(configFile);
            List<Context> contexts = config.getContexts();
            //覆盖数据源参数
            for(Context context : contexts){
                JDBCConnectionConfiguration jdbcConnectionConfiguration = context.getJdbcConnectionConfiguration();
                if(jdbcConnectionConfiguration != null){
                    jdbcConnectionConfiguration.setDriverClass(driverClass);
                    jdbcConnectionConfiguration.setConnectionURL(connectionUrl);
                    jdbcConnectionConfiguration.setUserId(userId);
                    jdbcConnectionConfiguration.setPassword(password);
                }
                ConnectionFactoryConfiguration connectionFactoryConfiguration = context.getConnectionFactoryConfiguration();
                if(connectionFactoryConfiguration != null){
                    connectionFactoryConfiguration.addProperty("driverClass", driverClass);
                    connectionFactoryConfiguration.addProperty("connectionURL", connectionUrl);
                    connectionFactoryConfiguration.addProperty("userId", userId);
                    connectionFactoryConfiguration.addProperty("password", password);
                }

                getLog().info("Datasource Configurations That Used by MBG In " + context.getId() + " Context Is: {DRIVER_CLASS= " + driverClass + "; CONNECTION_URL=" + connectionUrl + "; USER_ID=" + userId + "; SCHEME=" + schema);


            }




            DefaultShellCallback callback = new DefaultShellCallback(true);
            MyBatisGenerator myBatisGenerator = new MyBatisGenerator(config, callback, warnings);
            myBatisGenerator.generate(null);
        } catch (InvalidConfigurationException|SQLException|IOException|InterruptedException|XMLParserException e) {
            getLog().error(e.getMessage(), e);
            throw new MojoExecutionException(e.getMessage());
        } finally {
            configFile.delete();
        }
    }


    private static File modifyFile(File inputFile) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setIgnoringComments(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputFile);
        NodeList contextNodes = doc.getDocumentElement().getChildNodes();
        for(int i = 1; i < contextNodes.getLength(); i = i+2){
            NodeList contextChildNodes = contextNodes.item(i).getChildNodes();

            int j = 1;
            for(; j < contextChildNodes.getLength(); j = j + 2){
                String contextChildNodeName = contextChildNodes.item(j).getNodeName();
                //排除(connectionFactory|jdbcConnection)元素之前的property*,plugin*,commentGenerator?元素
                if ("property".equals(contextChildNodeName) || "plugin".equals(contextChildNodeName) || "commentGenerator".equals(contextChildNodeName)){
                    continue;
                }

                //如果不存在
                if(!"jdbcConnection".equals(contextChildNodeName) && !"connectionFactory".equals(contextChildNodeName)){
                    Node newEmptyJdbcConnectionNode = doc.createElement("jdbcConnection");
                    ((Element) newEmptyJdbcConnectionNode).setAttribute("driverClass", "");
                    ((Element) newEmptyJdbcConnectionNode).setAttribute("connectionURL", "");
                    ((Element) newEmptyJdbcConnectionNode).setAttribute("userId", "");
                    ((Element) newEmptyJdbcConnectionNode).setAttribute("password", "");
                    contextNodes.item(i).insertBefore(newEmptyJdbcConnectionNode, contextChildNodes.item(j));
                }
                break;

            }

        }

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DocumentType docType = doc.getDoctype();
        if(docType != null) {
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, docType.getPublicId());
            transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, docType.getSystemId());
        }

        DOMSource source = new DOMSource(doc);
        File outputFile = new File("tmp.xml");
        StreamResult result = new StreamResult(outputFile);
        transformer.transform(source, result);

        return outputFile;
    }
}