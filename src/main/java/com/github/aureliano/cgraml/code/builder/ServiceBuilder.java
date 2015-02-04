package com.github.aureliano.cgraml.code.builder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.github.aureliano.cgraml.code.gen.ServiceFetchInterfaceGenerator;
import com.github.aureliano.cgraml.code.gen.ServiceParametersInterfaceGenerator;
import com.github.aureliano.cgraml.code.meta.ActionMeta;
import com.github.aureliano.cgraml.code.meta.ClassMeta;
import com.github.aureliano.cgraml.code.meta.FieldMeta;
import com.github.aureliano.cgraml.code.meta.MethodMeta;
import com.github.aureliano.cgraml.code.meta.ServiceMeta;
import com.github.aureliano.cgraml.code.meta.Visibility;
import com.github.aureliano.cgraml.helper.CodeBuilderHelper;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;

public class ServiceBuilder implements IBuilder {

	private ClassMeta clazz;
	private ServiceMeta serviceMeta;
	private static final Set<String> GENERATED_CLASSES = new HashSet<String>();
	
	public ServiceBuilder() {
		super();
	}

	@SuppressWarnings("unchecked")
	@Override
	public ServiceBuilder parse(String pkg, String entity, Object resource) {
		this.serviceMeta = (ServiceMeta) resource;
		
		this.clazz = new ClassMeta()
			.withPackageName(pkg + ".service")
			.withJavaDoc("Generated by cgraml-maven-plugin.")
			.withClassName(CodeBuilderHelper.sanitizedTypeName(this.serviceMeta.getUri()) + "Service");
		
		if (GENERATED_CLASSES.contains(this.clazz.getCanonicalClassName())) {
			throw new IllegalArgumentException("Class " + this.clazz.getCanonicalClassName() + " was already generated before. Skipping!");
		}
		
		this.addUrlAttributeToClass();
		this.addAttributeToClassIfParameterizedResource(this.serviceMeta);
		this.addServiceParametersToClass(this.serviceMeta);		
		
		this.addServiceMethodsToClass(this.serviceMeta);
		this.addHttpAccessMethodsToClass(pkg + ".model", this.serviceMeta);
		
		if (this.containsHttpGetCollectionData()) {
			this.clazz.addInterface(this.clazz.getPackageName() + "." + ServiceFetchInterfaceGenerator.CLASS_NAME);
			this.addInheritedMethodsImplementation();
		}
		
		GENERATED_CLASSES.add(this.clazz.getCanonicalClassName());
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ServiceBuilder build() {
		this.buildJavaClass();
		return this;
	}
	
	private void buildJavaClass() {
		try {
			JCodeModel codeModel = new JCodeModel();
			JDefinedClass definedClass = codeModel._class(this.clazz.getCanonicalClassName());
			definedClass.javadoc().append(this.clazz.getJavaDoc());
			
			String narrowClass = this.getModelFetchType();
			for (String interfaceName : this.clazz.getInterfaces()) {
				definedClass._implements(codeModel.ref(interfaceName).narrow(codeModel.ref(narrowClass)));
			}
			
			JMethod constructor = definedClass.constructor(Visibility.PUBLIC.getMod());
			constructor.param(String.class, "url");
			String uri = this.serviceMeta.getUri().substring(this.serviceMeta.getUri().lastIndexOf("/"));
			constructor.body().directStatement("this.url = ((url == null || url == \"\") ? \"\" : url) +  \"" + uri + "\";");
			
			this.appendClassAttributes(codeModel, definedClass);
			this.appendClassMethods(codeModel, definedClass);
			
			codeModel.build(new File("src/main/java"));
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private void appendClassAttributes(JCodeModel codeModel, JDefinedClass definedClass) {
		for (FieldMeta field : this.clazz.getFields()) {
			CodeBuilderHelper.addAttributeToClass(codeModel, definedClass, field);
		}
	}

	private void appendClassMethods(JCodeModel codeModel, JDefinedClass definedClass) {
		for (MethodMeta method : this.clazz.getMethods()) {
			CodeBuilderHelper.addMethodToClass(codeModel, definedClass, method);
		}
	}
	
	private String getModelFetchType() {
		return this.clazz.getCanonicalClassName().replaceAll("Service$", "").replace(".service.", ".model.");
	}

	private void addInheritedMethodsImplementation() {
		String pkg = this.clazz.getPackageName().replaceAll("\\.model$", ".parameters");
		String modelFetchType = this.getModelFetchType();
		String parameterInterface = this.clazz.getPackageName()
				.replaceAll("\\.service$", ".parameters") + "." + ServiceParametersInterfaceGenerator.CLASS_NAME;
		
		for (MethodMeta m : ServiceFetchInterfaceBuilder.getAbstractMethods()) {
			MethodMeta method = m.clone();
			if (method.getReturnType().equals("T")) {
				method.setReturnType(modelFetchType);
			} else if (method.getReturnType().equals(ServiceParametersInterfaceGenerator.CLASS_NAME)) {
				method.setReturnType(parameterInterface);
			}
			
			int index = this.clazz.getMethods().indexOf(method);
			if (index >= 0) {
				String body = this.clazz.getMethods().get(index).getBody();
				method.setBody(body);
				this.clazz.getMethods().remove(index);
			}
			
			if ("withParameters".equals(method.getName())) {
				method.setReturnType(String.format("%s.%s", pkg, ServiceFetchInterfaceGenerator.CLASS_NAME));
				method.setGenericReturnType(modelFetchType);
				
				FieldMeta field = method.getParameters().get(0);
				String parameterType = this.clazz.findField(field).getType();
				method.setBody(method.getBody().replace("this.parameters = parameters", "this.parameters = (" + parameterType + ") parameters"));
				field.setType(this.clazz.getPackageName()
						.replaceAll(".service$", ".parameters." + ServiceParametersInterfaceGenerator.CLASS_NAME));
			}
			
			this.clazz.addMethod(method);
		}
	}

	private void addServiceParametersToClass(ServiceMeta service) {
		if (CodeBuilderHelper.getGetAction(service).getParameters().isEmpty()) {
			return;
		}
		
		String serviceType = CodeBuilderHelper.sanitizedTypeName(service.getUri());
		
		FieldMeta field = new FieldMeta();
		field.setName("parameters");
		field.setType(this.clazz.getPackageName().replace(".service", ".parameters.") + serviceType + "Parameters");
		field.setVisibility(Visibility.PRIVATE);
		
		this.clazz.addField(field);
		this.clazz.addMethod(CodeBuilderHelper.createGetterMethod(field));
		this.clazz.addMethod(CodeBuilderHelper.createBuilderMethod(this.clazz.getClassName(), field));
	}

	private void addUrlAttributeToClass() {
		FieldMeta field = new FieldMeta();
		field.setName("url");
		field.setType(String.class.getName());
		field.setVisibility(Visibility.PRIVATE);
		
		this.clazz.addField(field);
		this.clazz.addMethod(CodeBuilderHelper.createGetterMethod(field));
	}
	
	private void addAttributeToClassIfParameterizedResource(ServiceMeta service) {
		Matcher matcher = Pattern.compile("/?\\{[\\w\\d]+\\}$").matcher(service.getUri());
		if (!matcher.find()) {
			return;
		}
		
		FieldMeta field = new FieldMeta();
		String name = CodeBuilderHelper.sanitizedTypeName(service.getUri());
		name = name.substring(0, 1).toLowerCase() + name.substring(1);
		
		field.setName(name);
		field.setType(String.class.getName());
		field.setVisibility(Visibility.PRIVATE);
		
		this.clazz.addField(field);
		
		MethodMeta setter = CodeBuilderHelper.createSetterMethod(field);
		String body = new StringBuilder()
			.append(String.format("if (%s != null && %s != \"\") {", field.getName(), field.getName()))
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("this.url = this.url.substring(0, this.url.lastIndexOf(\"/\")) + \"/\" + " + field.getName() + ";")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append(String.format("this.%s = %s;", field.getName(), field.getName()))
			.append("\n" + CodeBuilderHelper.tabulation(2)).append("}")
			.toString();
		setter.setBody(body);
		
		this.clazz.addMethod(setter);
		this.clazz.addMethod(CodeBuilderHelper.createGetterMethod(field));
	}

	private void addServiceMethodsToClass(ServiceMeta service) {
		if (service.getNextServices().isEmpty()) {
			return;
		}
		
		for (ServiceMeta s : service.getNextServices()) {
			MethodMeta method = new MethodMeta();
			
			String name = CodeBuilderHelper.sanitizedTypeName(s.getUri());
			method.setReturnType(name + "Service");
			
			name = name.substring(0, 1).toLowerCase() + name.substring(1);
			method.setName("_" + name);
			method.setVisibility(Visibility.PUBLIC);
			
			Matcher matcher = Pattern.compile("/?\\{[\\w\\d]+\\}$").matcher(s.getUri());
			if (matcher.find()) {
				FieldMeta field = new FieldMeta();
				field.setName(method.getName().replaceFirst("_", ""));
				field.setType(String.class.getName());
				
				method.addParameter(field);
			}				
			
			method.setBody(this.buildServiceMethodBody(method));
			this.clazz.addMethod(method);
		}
	}
	
	private String buildServiceMethodBody(MethodMeta method) {
		StringBuilder b = new StringBuilder();
		b.append(String.format("%s service = new %s(this.url);", method.getReturnType(), method.getReturnType()));
		b.append("\n" + CodeBuilderHelper.tabulation(2));
		
		if (!method.getParameters().isEmpty()) {
			for (FieldMeta param : method.getParameters()) {
				b.append(String.format("service.set%s(%s);", StringUtils.capitalize(param.getName()), param.getName()));
				b.append("\n" + CodeBuilderHelper.tabulation(2));
			}
		}
		
		b.append("\n" + CodeBuilderHelper.tabulation(2));
		return b.append("return service;").toString();
	}

	private void addHttpAccessMethodsToClass(String pkgModel, ServiceMeta service) {
		for (ActionMeta action : service.getActions()) {
			MethodMeta method = new MethodMeta();
			method.setName("http" + StringUtils.capitalize(action.getMethod().name().toLowerCase()));
			boolean javaType = CodeBuilderHelper.stringToClass(service.getType()) != null;
			
			if (javaType) {
				method.setReturnType(service.getType());
			} else {
				method.setReturnType(pkgModel + "." + service.getType());
			}
				
			if (method.getName().equals("httpPost")) {
				method.setReturnType((javaType) ? service.getGenericType() : (pkgModel + "." + service.getGenericType()));
			} else if (method.getName().equals("httpDelete")) {
				method.setReturnType(null);
			}
			
			if (method.getName().equals("httpPost") || method.getName().equals("httpPut") || method.getName().equals("httpPatch")) {
				FieldMeta param = new FieldMeta();
				param.setName("entityResource");
				param.setType(method.getReturnType());
				
				method.addParameter(param);
			}
			
			method.setVisibility(Visibility.PUBLIC);
			method.setBody(this.constructBody(method));
			
			this.clazz.addMethod(method);
		}
	}
	
	private String constructBody(MethodMeta method) {
		if (method.getName().equals("httpGet")) {
			return this.methodGetBody(method);
		} else if (method.getName().equals("httpPost")) {
			return this.methodPostBody(method);
		} else if (method.getName().equals("httpPut")) {
			return this.methodPutBody(method);
		} else if (method.getName().equals("httpDelete")) {
			return this.methodDeleteBody(method);
		}
		
		return "throw new UnsupportedOperationException(\"" + method.getName().toUpperCase() + " is a non-standard HTTP method and it is not supported by this generator.\");";
	}
	
	private String methodGetBody(MethodMeta method) {
		StringBuilder builder = new StringBuilder();
		
		if (this.serviceHasParameters()) {
			String pkg = this.clazz.getPackageName().replace(".service", ".parameters.");
			String serviceName = pkg + CodeBuilderHelper.sanitizedTypeName(this.serviceMeta.getUri()) + "Parameters";
			
			builder
				.append("if (this.parameters == null) {")
				.append("\n" + CodeBuilderHelper.tabulation(3))
				.append("this.parameters = new " + serviceName + "();")
				.append("\n" + CodeBuilderHelper.tabulation(2) + "}")
				.append("\n\n" + CodeBuilderHelper.tabulation(2));
		}
		
		builder
			.append("javax.ws.rs.client.Client client = javax.ws.rs.client.ClientBuilder.newClient();")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("javax.ws.rs.client.WebTarget target = client.target(ApiMapService.instance().getBaseUri())")
			.append(".path(this.url)");
		
		List<FieldMeta> parameters = this.classAttributes();
		if (!parameters.isEmpty()) {
			for (FieldMeta param : parameters) {
				String getter = String.format("this.parameters.get%s()", StringUtils.capitalize(param.getName()));
				builder.append(String.format(".queryParam(\"%s\", %s)", param.getName(), getter));
			}
		}
		
		builder
			.append(";")		
			.append("\n\n" + CodeBuilderHelper.tabulation(2))
			.append("String json = target.request(javax.ws.rs.core.MediaType.APPLICATION_JSON).get(String.class);")
			.append("\n\n" + CodeBuilderHelper.tabulation(2))
			.append("com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("try {")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("return mapper.readValue(json, " + method.getReturnType() + ".class);")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("} catch (Exception ex) {")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("throw new RuntimeException(ex);")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("}");
		
		return builder.toString();
	}
	
	private String methodPostBody(MethodMeta method) {
		return new StringBuilder()
			.append("javax.ws.rs.client.Client client = javax.ws.rs.client.ClientBuilder.newClient();")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("javax.ws.rs.client.WebTarget target = client.target(ApiMapService.instance().getBaseUri())")
			.append(".path(this.url);")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("try {")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("String json = mapper.writeValueAsString(" + method.getParameters().get(0).getName() + ");")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("json = target.request(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).post(javax.ws.rs.client.Entity.entity(json, javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE), String.class);")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("return mapper.readValue(json, " + method.getReturnType() + ".class);")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("} catch (Exception ex) {")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("throw new RuntimeException(ex);")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("}")
			.toString();
	}
	
	private String methodPutBody(MethodMeta method) {
		return new StringBuilder()
			.append("javax.ws.rs.client.Client client = javax.ws.rs.client.ClientBuilder.newClient();")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("javax.ws.rs.client.WebTarget target = client.target(ApiMapService.instance().getBaseUri())")
			.append(".path(this.url);")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("try {")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("String json = mapper.writeValueAsString(" + method.getParameters().get(0).getName() + ");")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("json = target.request(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).put(javax.ws.rs.client.Entity.entity(json, javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE), String.class);")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("return mapper.readValue(json, " + method.getReturnType() + ".class);")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("} catch (Exception ex) {")
			.append("\n" + CodeBuilderHelper.tabulation(3))
			.append("throw new RuntimeException(ex);")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("}")
			.toString();
	}
	
	private String methodDeleteBody(MethodMeta method) {
		return new StringBuilder()
			.append("javax.ws.rs.client.Client client = javax.ws.rs.client.ClientBuilder.newClient();")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("javax.ws.rs.client.WebTarget target = client.target(ApiMapService.instance().getBaseUri())")
			.append(".path(this.url);")
			.append("\n" + CodeBuilderHelper.tabulation(2))
			.append("target.request().delete();")
			.toString();
	}
	
	private List<FieldMeta> classAttributes() {
		List<FieldMeta> fields = new ArrayList<FieldMeta>();
		
		ActionMeta action = CodeBuilderHelper.getGetAction(this.serviceMeta);
		if ((action == null) || (action.getParameters().isEmpty())) {
			return fields;
		}
		
		for (FieldMeta f : action.getParameters()) {
			if (f.getName().equals("url")) {
				continue;
			}
			fields.add(f);
		}
		
		return fields;
	}
	
	private boolean serviceHasParameters() {
		ActionMeta action = CodeBuilderHelper.getGetAction(this.serviceMeta);
		return ((action != null) && (!action.getParameters().isEmpty()));
	}

	private boolean containsHttpGetCollectionData() {
		MethodMeta method = this.getHttpGetCollectionDataMethod();
		if (method == null) {
			return false;
		}
		
		String returnType = method.getReturnType().substring(method.getReturnType().lastIndexOf('.') + 1);
		return this.clazz.getClassName().replaceAll("Service$", "").equals(returnType);
	}
	
	private MethodMeta getHttpGetCollectionDataMethod() {
		for (MethodMeta method : this.clazz.getMethods()) {
			if ("httpGet".equals(method.getName())) {
				return method;
			}
		}
		
		return null;
	}
	
	public ClassMeta getClazz() {
		return clazz;
	}
	
	public ServiceBuilder withClazz(ClassMeta clazz) {
		this.clazz = clazz;
		return this;
	}
}