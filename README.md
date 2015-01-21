ignoratio-maven-plugin
=========

### Overview
This project is a Maven plugin which aims code generation based on RAML definitions. It covers lack between a Web RESTful application and a Java consumer application. With code generation for data/model classes and service classes all you have to do is start consuming resources from web RESTful application.

### Installation
Unfortunatelly this API is not available at Maven Central Repository yet. So at this point you have to install it manually.

Clone this repository with GIT `git clone https://github.com/aureliano/ignoratio-maven-plugin.git` or download source code from release `https://github.com/aureliano/ignoratio/releases/tag/x.x.x`, or even getting the edge source from `https://github.com/aureliano/ignoratio/archive/master.zip`. Extract files and go to project directory. Install locally with Maven by typing `mvn install`.

### Usage

After installation from source code all you have to do is add it as a plugin into your POM.
```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.github.aureliano</groupId>
      <artifactId>ignoratio-maven-plugin</artifactId>
      <version>x.x.x</version>
      <configuration>
        <sourceDirectory>${project.basedir}</sourceDirectory>
        <basePackageName>com.my.application.base.package.where.files.will.be.generated</basePackageName>
        <removeOldOutput>true</removeOldOutput>
      </configuration>
      <executions>
        <execution>
          <phase>generate-sources</phase>
          <goals>
            <goal>generate</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```
Some tags have to be detailed here.
* sourceDirectory - points to the directory where RAML files can be found.
* basePackageName - tells where classes have to be put.
* removeOldOutput - overrides generated classes.

Code generated by this Maven plugin depends on some dependencies. So you have to add them to your POM.
```xml
<dependencies>
  <dependency>
    <groupId>org.glassfish.jersey.core</groupId>
    <artifactId>jersey-client</artifactId>
    <version>2.14</version>
  </dependency>

  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.2.2</version>
  </dependency>
</dependencies>
```
Get ready by generating and compiling code with `mvn compile`.

### Sample
Now if we have a web service API with this [RAML definition](https://github.com/mulesoft/raml-jaxrs-codegen/blob/master/jersey-example/src/main/resources/raml/sales-enablement-api.raml), after code generation we can do like below.
```java
Products products = ApiMapService.instance().products().get();
System.out.println(products.size());
System.out.println(products.getProducts().get(0).getName());
```
In that sample we invoked a GET HTTP method to resource "products" which gave us back a Products object. Now lets take a specific product by id.
```
Product product = ApiMapService.instance().products().productId("25").get();
System.out.println(product.getName());
```
To create a new product all we to do is invoke POST method from products resource.
```java
Product product = ApiMapService.instance().products().post(
  new Product().withId("2015").withName("ball").withDescription("Soccer ball").withRegion("BR"));
System.out.println(product.getId());
```

=======
License - [MIT](https://github.com/aureliano/ignoratio/blob/master/LICENSE)
