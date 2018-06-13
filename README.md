[TOC]

### 为什么设计该插件？
Mybatis已经有开发了一个“mybatis-generator-maven-plugin”插件供大众使用了，但该插件仅能通过配置固定的常量参数来使用数据源信息，无法满足从spring-cloud-config配置文件中读取数据源配置信息来生成代码。而对于“针对项目进行代码生成”等操作更适合设计成“工具”来使用，所以相比起零散的代码类，独立的jar包等方式，还不如能集成进项目又能独立使用的maven插件来得合适。故本插件内部封装了“mybatis-generator-maven-plugin”插件，设计为读取spring-cloud-config的配置文件（即默认bootstrap-XXX.properties）的数据源来生成Mybatis代码。


### Quick Start
☞src/main/resources/bootstrap-XXX.properties

☞src/main/resources/mybatis-generator-config.xml

☞pom.xml

```xml
<plugin>
    <groupId>io.github.elliotmok</groupId>
    <artifactId>mybatis-generator-spring-cloud-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>exe</id>
            <phase>validate</phase>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
    <!-- 配置参数 -->
    <configuration>
        <profile>prd</profile>
        <mbgConfigurationFile>src/main/resources/mybatis-generator-config.xml</mbgConfigurationFile>
    </configuration>
    <!--所需依赖 -->
    <dependencies>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>6.0.6</version>
        </dependency>
    </dependencies>
</plugin>
```

#### goal
该插件仅有一个goal： generate。

该插件可以单独使用，也可以绑定于项目的生命周期阶段（pharse）中。由于生成的Mybatis代码通常需要参与项目的编译，故建议通常应绑定到项目的compile之前的阶段，比如validate阶段等。

#### 配置参数
+ profile  spring-cloud-config配置文件的profile，对应“bootstrap-{profile}.properties”。
+ mbgConfigurationFile  Mybatis Generator的配置文件位置。

#### 配置文件
该插件没有暴露独立设计的配置文件，而是直接暴露了底层用到的Mybatis Generator（MBG）的配置文件给用户配置。

配置文件的默认路径为：${project_directory}/src/main/resources/mybatis-generator-config.xml。各配置元素的含义请参考:[MyBatis Generator Core – MyBatis Generator XML Configuration File Reference](http://www.mybatis.org/generator/configreference/xmlconfig.html)

☞注意：由于本插件生成Mybatis代码的数据源本身就被设计为来自spring-cloud-config中的配置信息所指向的数据源，插件底层会始终使用用户指定的spring-cloud-config中的数据源配置信息。故原本在MBG配置文件中用于配置数据源配置信息的```<jdbcConnection>```元素或者```<connectionFactory>```元素在该插件中的“mybatis-generator-config.xml”文件中并不起任何作用，你可以选择忽略配置这两个元素。
